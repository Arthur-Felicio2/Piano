package com.example.pianoapp

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    // --- Mapa para gerenciar os "tracks" de áudio e coroutines ativas ---
    private val activeTracks = mutableMapOf<Int, Pair<AudioTrack, Job>>()
    private val audioScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // --- Constantes de Áudio para Síntese ---
    private val SAMPLE_RATE = 44100
    private val BUFFER_SIZE = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

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

    // --- FREQUÊNCIAS DAS TECLAS BRANCAS (Em Hertz) ---
    private val whiteKeyFrequencies = doubleArrayOf(
        32.70, 36.71, 41.20, 43.65, 49.00, 55.00, 61.74, // Oitava 1 (C1 a B1)
        65.41, 73.42, 82.41, 87.31, 98.00, 110.00, 123.47, // Oitava 2 (C2 a B2)
        130.81, 146.83, 164.81, 174.61, 196.00, 220.00, 246.94, // Oitava 3 (C3 a B3)
        261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, // Oitava 4 (C4 a B4)
        523.25, 587.33, 659.26, 698.46, 783.99, 880.00, 987.77, // Oitava 5 (C5 a B5)
        1046.50, 1174.66, 1318.51, 1396.91, 1567.98, 1760.00, 1975.53, // Oitava 6 (C6 a B6)
        2093.00, 2349.32, 2637.02, 2793.83, 3135.96, 3520.00, 3951.07  // Oitava 7 (C7 a B7)
    )

    // --- [NOVO] ARRAYS DE TECLAS PRETAS (C#, D#, F#, G#, A#) ---
    // (Verifique se esses R.id's batem com seu layout XML)
    private val blackKeyIds = arrayOf(
        R.id.keyCs1, R.id.keyDs1, R.id.keyFs1, R.id.keyGs1, R.id.keyAs1,
        R.id.keyCs2, R.id.keyDs2, R.id.keyFs2, R.id.keyGs2, R.id.keyAs2,
        R.id.keyCs3, R.id.keyDs3, R.id.keyFs3, R.id.keyGs3, R.id.keyAs3,
        R.id.keyCs4, R.id.keyDs4, R.id.keyFs4, R.id.keyGs4, R.id.keyAs4,
        R.id.keyCs5, R.id.keyDs5, R.id.keyFs5, R.id.keyGs5, R.id.keyAs5,
        R.id.keyCs6, R.id.keyDs6, R.id.keyFs6, R.id.keyGs6, R.id.keyAs6,
        R.id.keyCs7, R.id.keyDs7, R.id.keyFs7, R.id.keyGs7, R.id.keyAs7
    )

    // --- [NOVO] FREQUÊNCIAS DAS TECLAS PRETAS (Em Hertz) ---
    private val blackKeyFrequencies = doubleArrayOf(
        34.65, 38.89, 46.25, 51.91, 58.27, // Oitava 1 (C#1 a A#1)
        69.30, 77.78, 92.50, 103.83, 116.54, // Oitava 2
        138.59, 155.56, 185.00, 207.65, 233.08, // Oitava 3
        277.18, 311.13, 369.99, 415.30, 466.16, // Oitava 4
        554.37, 622.25, 739.99, 830.61, 932.33, // Oitava 5
        1108.73, 1244.51, 1479.98, 1661.22, 1864.66, // Oitava 6
        2217.46, 2489.02, 2959.96, 3322.44, 3729.31  // Oitava 7
    )


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

        // --- Configuração do Scroll e SeekBar (Original) ---
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

        // --- Configuração das Teclas Brancas (com AudioTrack) ---
        val whiteKeys = whiteKeyIds.mapIndexed { index, keyId ->
            findViewById<Button>(keyId).apply {
                setOnTouchListener { view, event ->
                    val frequency = whiteKeyFrequencies[index]

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (activeTracks[view.id] == null) {
                                val audioTrack = createAudioTrack()
                                val job = audioScope.launch {
                                    playNote(audioTrack, frequency)
                                }
                                activeTracks[view.id] = Pair(audioTrack, job)
                                setBackgroundColor("#80ffe5".toColorInt())
                            }
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val activeTrack = activeTracks[view.id]
                            if (activeTrack != null) {
                                activeTrack.second.cancel()
                                val track = activeTrack.first
                                track.setVolume(0.0f)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                        track.stop()
                                    }
                                    track.release()
                                }, 50)
                                activeTracks.remove(view.id)
                            }
                            setBackgroundColor("#FFFFFF".toColorInt())
                            true
                        }
                        else -> false
                    }
                }
            }
        }

        // --- [NOVO] Configuração das Teclas Pretas (com AudioTrack) ---
        val blackKeys = blackKeyIds.mapIndexed { index, keyId ->
            findViewById<Button>(keyId).apply {
                setOnTouchListener { view, event ->
                    val frequency = blackKeyFrequencies[index] // Usa as frequências pretas

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (activeTracks[view.id] == null) {
                                val audioTrack = createAudioTrack()
                                val job = audioScope.launch {
                                    playNote(audioTrack, frequency)
                                }
                                activeTracks[view.id] = Pair(audioTrack, job)

                                // Cor de "pressionado" (pode ser a mesma ou outra)
                                setBackgroundColor("#80ffe5".toColorInt())
                            }
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val activeTrack = activeTracks[view.id]
                            if (activeTrack != null) {
                                activeTrack.second.cancel()
                                val track = activeTrack.first
                                track.setVolume(0.0f)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                        track.stop()
                                    }
                                    track.release()
                                }, 50)
                                activeTracks.remove(view.id)
                            }

                            // Retorna para a cor preta
                            setBackgroundColor("#000000".toColorInt())
                            true
                        }
                        else -> false
                    }
                }
            }
        }

        // O array de teclas agora contém TODAS as teclas (brancas e pretas)
        keys = (whiteKeys + blackKeys).toTypedArray()
    }

    // --- Funções de Síntese (AudioTrack) ---

    /**
     * Cria um AudioTrack configurado para streaming de áudio.
     */
    private fun createAudioTrack(): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(BUFFER_SIZE)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /**
     * Coroutine que gera uma onda senoidal e a escreve no AudioTrack.
     * Continua em loop (isActive) até que o Job seja cancelado.
     */
    private fun CoroutineScope.playNote(track: AudioTrack, frequency: Double) {
        val buffer = ShortArray(BUFFER_SIZE / 2) // /2 pois é Short (16-bit)
        var phase = 0.0

        track.play()

        while (isActive) {
            // Gera a onda senoidal
            for (i in buffer.indices) {
                // sin(2 * PI * phase * freq / sampleRate)
                val sinValue = sin(2.0 * PI * phase * frequency / SAMPLE_RATE)
                buffer[i] = (sinValue * Short.MAX_VALUE).toInt().toShort()
                phase++

                // Reseta a fase para evitar overflow e manter a precisão
                if (phase * frequency / SAMPLE_RATE > 1.0) {
                    phase = 0.0
                }
            }

            // Escreve os dados no buffer do AudioTrack
            track.write(buffer, 0, buffer.size)
        }

        // Quando o loop termina (job cancelado), paramos o track
        // (O stop/release principal é feito no postDelayed)
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause() // Pausa suave antes do stop/release no Handler
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // Cancela todas as coroutines de áudio
        audioScope.cancel()

        // Libera todos os AudioTracks que possam estar ativos
        activeTracks.values.forEach { (track, job) ->
            job.cancel() // Garante que a coroutine parou
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.release()
        }
        activeTracks.clear()
    }
}