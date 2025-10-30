package com.example.pianoapp

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent // <-- IMPORTANTE: Para o toque
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    // Usando SoundPool para som limpo e rápido
    private lateinit var soundPool: SoundPool
    private var soundMap = HashMap<Int, Int>()

    // Handlers para o "release" suave da nota (evita o corte seco e o "repique")
    private val releaseHandler = Handler(Looper.getMainLooper())
    private val NOTE_RELEASE_MS = 150L
    private val pendingStopRunnables = mutableMapOf<Int, Runnable>()


    // --- ARRAYS DE TECLAS BRANCAS NA ORDEM CORRETA (Dó, Ré, Mi...) ---
    private val whiteKeyIds = arrayOf(
        R.id.keyC1, R.id.keyD1, R.id.keyE1, R.id.keyF1, R.id.keyG1, R.id.keyA1, R.id.keyB1,
        R.id.keyC2, R.id.keyD2, R.id.keyE2, R.id.keyF2, R.id.keyG2, R.id.keyA2, R.id.keyB2,
        R.id.keyC3, R.id.keyD3, R.id.keyE3, R.id.keyF3, R.id.keyG3, R.id.keyA3, R.id.keyB3,
        R.id.keyC4, R.id.keyD4, R.id.keyE4, R.id.keyF4, R.id.keyG4, R.id.keyA4, R.id.keyB4,
        R.id.keyC5, R.id.keyD5, R.id.keyE5, R.id.keyF5, R.id.keyG5, R.id.keyA5, R.id.keyB5,
        R.id.keyC6, R.id.keyD6, R.id.keyE6, R.id.keyF6, R.id.keyG6, R.id.keyA6, R.id.keyB6,
        R.id.keyC7, R.id.keyD7, R.id.keyE7, R.id.keyF7, R.id.keyG7, R.id.keyA7, R.id.keyB7
    )

    // --- ARRAYS DE SONS DAS TECLAS BRANCAS NA ORDEM CORRETA (Dó, Ré, Mi...) ---
    private val whiteKeySounds = arrayOf(
        R.raw.c1, R.raw.d1, R.raw.e1, R.raw.f1, R.raw.g1, R.raw.a1, R.raw.b1,
        R.raw.c2, R.raw.d2, R.raw.e2, R.raw.f2, R.raw.g2, R.raw.a2, R.raw.b2,
        R.raw.c3, R.raw.d3, R.raw.e3, R.raw.f3, R.raw.g3, R.raw.a3, R.raw.b3,
        R.raw.c4, R.raw.d4, R.raw.e4, R.raw.f4, R.raw.g4, R.raw.a4, R.raw.b4,
        R.raw.c5, R.raw.d5, R.raw.e5, R.raw.f5, R.raw.g5, R.raw.a5, R.raw.b5,
        R.raw.c6, R.raw.d6, R.raw.e6, R.raw.f6, R.raw.g6, R.raw.a6, R.raw.b6,
        R.raw.c7, R.raw.d7, R.raw.e7, R.raw.f7, R.raw.g7, R.raw.a7, R.raw.b7
    )

    // --- ARRAYS DAS TECLAS PRETAS REMOVIDOS (conforme solicitado) ---
    // private val blackKeyIds = ...
    // private val blackKeySounds = ...

    private lateinit var keys: Array<Button>

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContentView(R.layout.activity_main)

        // --- Configuração do SoundPool ---
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(16)
            .setAudioAttributes(audioAttributes)
            .build()

        loadSounds() // Carrega apenas os sons das teclas brancas

        // --- Configuração do Scroll e SeekBar ---
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        val scrollView = findViewById<HorizontalScrollView>(R.id.scrollView)
        scrollView.post { scrollView.scrollTo((scrollView.getChildAt(0).width * 0.55).toInt(), 0) }

        scrollView.setOnTouchListener { _, _ -> true }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scrollX = (scrollView.getChildAt(0).width - scrollView.width) * progress / 100
                scrollView.scrollTo(scrollX, 0)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // --- Configuração das Teclas Brancas (com setOnTouchListener) ---
        val whiteKeys = whiteKeyIds.mapIndexed { index, keyId ->
            findViewById<Button>(keyId).apply {
                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Cancela qualquer "stop" pendente para esta tecla (evita "repique")
                            pendingStopRunnables[view.id]?.let {
                                releaseHandler.removeCallbacks(it)
                            }
                            pendingStopRunnables.remove(view.id)

                            // Toca o som imediatamente e guarda o ID do stream
                            val streamId = playSound(whiteKeySounds[index])
                            view.tag = streamId
                            setBackgroundColor("#80ffe5".toColorInt())
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // Pega o ID do stream
                            val streamId = (view.tag as? Int) ?: return@setOnTouchListener false

                            // Cria o comando "stop"
                            val stopRunnable = Runnable {
                                soundPool.stop(streamId)
                                pendingStopRunnables.remove(view.id) // Limpa o mapa
                            }

                            // Armazena e agenda o comando de "stop" atrasado (simula "release")
                            pendingStopRunnables[view.id] = stopRunnable
                            releaseHandler.postDelayed(stopRunnable, NOTE_RELEASE_MS)

                            view.tag = null
                            setBackgroundColor("#FFFFFF".toColorInt())
                            true
                        }
                        else -> false
                    }
                }
            }
        }

        // --- Configuração das Teclas Pretas REMOVIDA ---
        // val blackKeys = ...

        // O array de teclas agora contém apenas as brancas
        keys = whiteKeys.toTypedArray()
    }

    // Carrega apenas os sons das teclas brancas
    private fun loadSounds() {
        whiteKeySounds.forEach { soundResource ->
            val soundId = soundPool.load(this, soundResource, 1)
            soundMap[soundResource] = soundId
        }
        // Loop de blackKeySounds removido
    }

    // Função playSound do SoundPool (retorna o streamID)
    private fun playSound(soundResource: Int): Int {
        val soundId = soundMap[soundResource] ?: 0
        return if (soundId != 0) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpa o SoundPool e o Handler
        releaseHandler.removeCallbacksAndMessages(null)
        soundPool.release()
    }
}