package com.example.pianoapp

import android.annotation.SuppressLint
import android.content.DialogInterface
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
import androidx.appcompat.app.AlertDialog
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
    private var musicPlaybackJob: Job? = null // Job para a música automática

    // --- Constantes de Áudio para Síntese ---
    private val SAMPLE_RATE = 44100
    private val BUFFER_SIZE = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    // --- Mapeamento de Frequências (COMPLETO) ---
    private val noteFrequencies = mapOf(
        // Oitava 1
        "C1" to 32.70, "C#1" to 34.65, "D1" to 36.71, "D#1" to 38.89, "E1" to 41.20,
        "F1" to 43.65, "F#1" to 46.25, "G1" to 49.00, "G#1" to 51.91, "A1" to 55.00,
        "A#1" to 58.27, "B1" to 61.74,
        // Oitava 2
        "C2" to 65.41, "C#2" to 69.30, "D2" to 73.42, "D#2" to 77.78, "E2" to 82.41,
        "F2" to 87.31, "F#2" to 92.50, "G2" to 98.00, "G#2" to 103.83, "A2" to 110.00,
        "A#2" to 116.54, "B2" to 123.47,
        // Oitava 3
        "C3" to 130.81, "C#3" to 138.59, "D3" to 146.83, "D#3" to 155.56, "E3" to 164.81,
        "F3" to 174.61, "F#3" to 185.00, "G3" to 196.00, "G#3" to 207.65, "A3" to 220.00,
        "A#3" to 233.08, "B3" to 246.94,
        // Oitava 4 (Oitava Central)
        "C4" to 261.63, "C#4" to 277.18, "D4" to 293.66, "D#4" to 311.13, "E4" to 329.63,
        "F4" to 349.23, "F#4" to 369.99, "G4" to 392.00, "G#4" to 415.30, "A4" to 440.00,
        "A#4" to 466.16, "B4" to 493.88,
        // Oitava 5
        "C5" to 523.25, "C#5" to 554.37, "D5" to 587.33, "D#5" to 622.25, "E5" to 659.26,
        "F5" to 698.46, "F#5" to 739.99, "G5" to 783.99, "G#5" to 830.61, "A5" to 880.00,
        "A#5" to 932.33, "B5" to 987.77,
        // Oitava 6
        "C6" to 1046.50, "C#6" to 1108.73, "D6" to 1174.66, "D#6" to 1244.51, "E6" to 1318.51,
        "F6" to 1396.91, "F#6" to 1479.98, "G6" to 1567.98, "G#6" to 1661.22, "A6" to 1760.00,
        "A#6" to 1864.66, "B6" to 1975.53,
        // Oitava 7
        "C7" to 2093.00, "C#7" to 2217.46, "D7" to 2349.32, "D#7" to 2489.02, "E7" to 2637.02,
        "F7" to 2793.83, "F#7" to 2959.96, "G7" to 3135.96, "G#7" to 3322.44, "A7" to 3520.00,
        "A#7" to 3729.31, "B7" to 3951.07
    )

    // --- ARRAYS DE IDS DAS TECLAS ---
    private val allKeyIds = arrayOf(
        R.id.keyC1, R.id.keyCs1, R.id.keyD1, R.id.keyDs1, R.id.keyE1, R.id.keyF1, R.id.keyFs1, R.id.keyG1, R.id.keyGs1, R.id.keyA1, R.id.keyAs1, R.id.keyB1,
        R.id.keyC2, R.id.keyCs2, R.id.keyD2, R.id.keyDs2, R.id.keyE2, R.id.keyF2, R.id.keyFs2, R.id.keyG2, R.id.keyGs2, R.id.keyA2, R.id.keyAs2, R.id.keyB2,
        R.id.keyC3, R.id.keyCs3, R.id.keyD3, R.id.keyDs3, R.id.keyE3, R.id.keyF3, R.id.keyFs3, R.id.keyG3, R.id.keyGs3, R.id.keyA3, R.id.keyAs3, R.id.keyB3,
        R.id.keyC4, R.id.keyCs4, R.id.keyD4, R.id.keyDs4, R.id.keyE4, R.id.keyF4, R.id.keyFs4, R.id.keyG4, R.id.keyGs4, R.id.keyA4, R.id.keyAs4, R.id.keyB4,
        R.id.keyC5, R.id.keyCs5, R.id.keyD5, R.id.keyDs5, R.id.keyE5, R.id.keyF5, R.id.keyFs5, R.id.keyG5, R.id.keyGs5, R.id.keyA5, R.id.keyAs5, R.id.keyB5,
        R.id.keyC6, R.id.keyCs6, R.id.keyD6, R.id.keyDs6, R.id.keyE6, R.id.keyF6, R.id.keyFs6, R.id.keyG6, R.id.keyGs6, R.id.keyA6, R.id.keyAs6, R.id.keyB6,
        R.id.keyC7, R.id.keyCs7, R.id.keyD7, R.id.keyDs7, R.id.keyE7, R.id.keyF7, R.id.keyFs7, R.id.keyG7, R.id.keyGs7, R.id.keyA7, R.id.keyAs7, R.id.keyB7
    )

    // --- Mapeamento de IDs para Nomes de Notas ---
    private val keyIdToNoteName = mapOf(
        R.id.keyC1 to "C1", R.id.keyCs1 to "C#1", R.id.keyD1 to "D1", R.id.keyDs1 to "D#1", R.id.keyE1 to "E1", R.id.keyF1 to "F1", R.id.keyFs1 to "F#1", R.id.keyG1 to "G1", R.id.keyGs1 to "G#1", R.id.keyA1 to "A1", R.id.keyAs1 to "A#1", R.id.keyB1 to "B1",
        R.id.keyC2 to "C2", R.id.keyCs2 to "C#2", R.id.keyD2 to "D2", R.id.keyDs2 to "D#2", R.id.keyE2 to "E2", R.id.keyF2 to "F2", R.id.keyFs2 to "F#2", R.id.keyG2 to "G2", R.id.keyGs2 to "G#2", R.id.keyA2 to "A2", R.id.keyAs2 to "A#2", R.id.keyB2 to "B2",
        R.id.keyC3 to "C3", R.id.keyCs3 to "C#3", R.id.keyD3 to "D3", R.id.keyDs3 to "D#3", R.id.keyE3 to "E3", R.id.keyF3 to "F3", R.id.keyFs3 to "F#3", R.id.keyG3 to "G3", R.id.keyGs3 to "G#3", R.id.keyA3 to "A3", R.id.keyAs3 to "A#3", R.id.keyB3 to "B3",
        R.id.keyC4 to "C4", R.id.keyCs4 to "C#4", R.id.keyD4 to "D4", R.id.keyDs4 to "D#4", R.id.keyE4 to "E4", R.id.keyF4 to "F4", R.id.keyFs4 to "F#4", R.id.keyG4 to "G4", R.id.keyGs4 to "G#4", R.id.keyA4 to "A4", R.id.keyAs4 to "A#4", R.id.keyB4 to "B4",
        R.id.keyC5 to "C5", R.id.keyCs5 to "C#5", R.id.keyD5 to "D5", R.id.keyDs5 to "D#5", R.id.keyE5 to "E5", R.id.keyF5 to "F5", R.id.keyFs5 to "F#5", R.id.keyG5 to "G5", R.id.keyGs5 to "G#5", R.id.keyA5 to "A5", R.id.keyAs5 to "A#5", R.id.keyB5 to "B5",
        R.id.keyC6 to "C6", R.id.keyCs6 to "C#6", R.id.keyD6 to "D6", R.id.keyDs6 to "D#6", R.id.keyE6 to "E6", R.id.keyF6 to "F6", R.id.keyFs6 to "F#6", R.id.keyG6 to "G6", R.id.keyGs6 to "G#6", R.id.keyA6 to "A6", R.id.keyAs6 to "A#6", R.id.keyB6 to "B6",
        R.id.keyC7 to "C7", R.id.keyCs7 to "C#7", R.id.keyD7 to "D7", R.id.keyDs7 to "D#7", R.id.keyE7 to "E7", R.id.keyF7 to "F7", R.id.keyFs7 to "F#7", R.id.keyG7 to "G7", R.id.keyGs7 to "G#7", R.id.keyA7 to "A7", R.id.keyAs7 to "A#7", R.id.keyB7 to "B7"
    )

    private lateinit var keys: Array<Button>
    private lateinit var playMusicButton: Button

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Configuração para Fullscreen (Imersivo)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContentView(R.layout.activity_main)

        // Botão do Menu de Música
        playMusicButton = findViewById(R.id.playMusicButton)
        playMusicButton.setOnClickListener {
            showMusicSelectionDialog()
        }

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

        // --- Configuração de TODAS as Teclas ---
        keys = allKeyIds.map { findViewById<Button>(it) }.toTypedArray()

        keys.forEach { keyButton ->
            val noteName = keyIdToNoteName[keyButton.id]
            val frequency = noteFrequencies[noteName]

            if (frequency != null) {
                keyButton.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (activeTracks[view.id] == null) {
                                val audioTrack = createAudioTrack()
                                val job = audioScope.launch {
                                    playNote(audioTrack, frequency)
                                }
                                activeTracks[view.id] = Pair(audioTrack, job)

                                val pressedColor = if (noteName!!.contains("#")) "#80ffe5".toColorInt() else "#FF80FF".toColorInt()
                                view.setBackgroundColor(pressedColor)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            stopKey(view.id)
                            val defaultColor = if (noteName!!.contains("#")) "#000000".toColorInt() else "#FFB300".toColorInt()
                            view.setBackgroundColor(defaultColor)
                            true
                        }
                        else -> false
                    }
                }
            }
        }
    }

    // --- FUNÇÕES DE ÁUDIO ---

    /**
     * Para a reprodução de uma única tecla.
     */
    private fun stopKey(keyId: Int) {
        val activeTrack = activeTracks[keyId]
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
            activeTracks.remove(keyId)
        }
    }

    /**
     * Cria um AudioTrack configurado para streaming de áudio.
     */
    private fun createAudioTrack(): AudioTrack {
        val track = AudioTrack.Builder()
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

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            throw IllegalStateException("Falha ao inicializar AudioTrack. Estado inválido: ${track.state}")
        }

        return track
    }

    /**
     * Coroutine que gera uma onda senoidal e a escreve no AudioTrack.
     */
    private fun CoroutineScope.playNote(track: AudioTrack, frequency: Double) {

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            return
        }

        val buffer = ShortArray(BUFFER_SIZE / 2)
        var phase = 0.0

        try {
            track.play()
        } catch (e: IllegalStateException) {
            return
        }

        while (isActive && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            for (i in buffer.indices) {
                val sinValue = sin(2.0 * PI * phase * frequency / SAMPLE_RATE)
                buffer[i] = (sinValue * Short.MAX_VALUE).toInt().toShort()
                phase++

                if (phase * frequency / SAMPLE_RATE > 1.0) {
                    phase = 0.0
                }
            }

            val written = track.write(buffer, 0, buffer.size)
            if (written < 0) {
                break
            }
        }

        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            try {
                track.pause()
            } catch (e: IllegalStateException) {
                // Ignora
            }
        }
    }

    // --- FUNÇÕES DE MÚSICA AUTOMÁTICA ---

    private fun showMusicSelectionDialog() {
        val musicOptions = arrayOf("Piratas do Caribe (He's a Pirate)")

        AlertDialog.Builder(this, R.style.ArcadeDialogTheme)
            .setTitle("🤖 Selecione uma Música")
            .setItems(musicOptions) { dialog: DialogInterface, which: Int ->
                when (which) {
                    0 -> playPiratesOfTheCaribbean()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    /**
     * Estrutura da música: Transcrição corrigida para maior fidelidade.
     */
    private val piratesTheme: List<Pair<String, Long>> = listOf(
        // Primeira Parte (D's rápidos e melodia)
        "D4" to 150, "D4" to 150, "D4" to 300,
        "D4" to 150, "D4" to 150, "D4" to 300,
        "D4" to 300, "WAIT" to 50, "E4" to 300, "WAIT" to 50, "F4" to 300, "WAIT" to 50, "G4" to 300,
        "A4" to 300, "WAIT" to 50, "G4" to 300, "WAIT" to 50, "F4" to 300, "WAIT" to 50, "E4" to 300,
        "D4" to 600, "WAIT" to 300,

        // Repetição (Oitava Acima)
        "D4" to 300, "WAIT" to 50, "E4" to 300, "WAIT" to 50, "F4" to 300, "WAIT" to 50, "G4" to 300,
        "A4" to 300, "WAIT" to 50, "G4" to 300, "WAIT" to 50, "F4" to 300, "WAIT" to 50, "E4" to 300,

        // Terceira Parte (Subida Dramática)
        "D4" to 300, "WAIT" to 50, "G4" to 300, // D G
        "A4" to 300, "WAIT" to 50, "B4" to 300, // A B
        "C5" to 300, "WAIT" to 50, "B4" to 300, // C B
        "A4" to 300, "WAIT" to 50, "G4" to 300, // A G

        // Quarta Parte (Final)
        "F4" to 300, "WAIT" to 50, "E4" to 300, // F E
        "D4" to 300, "WAIT" to 50, "C4" to 600, // D C
        "WAIT" to 300 // Pausa final
    )


    /**
     * Toca a música "Piratas do Caribe" usando a Coroutine e garante a
     * liberação correta dos recursos de áudio.
     */
    private fun playPiratesOfTheCaribbean() {
        musicPlaybackJob?.cancel()

        musicPlaybackJob = audioScope.launch {

            // Função helper para tocar e esperar
            suspend fun playAndDelay(noteName: String, duration: Long) {
                val frequency = noteFrequencies[noteName] ?: return

                // Tenta criar um Track DEDICADO.
                val noteTrack = try {
                    createAudioTrack()
                } catch (e: Exception) {
                    return
                }

                val buttonId = keyIdToNoteName.filterValues { it == noteName }.keys.firstOrNull()

                try {
                    // 1. Início do Flash de Cor (UI Thread)
                    buttonId?.let { id ->
                        val button = findViewById<Button>(id)
                        button?.let { btn ->
                            Handler(Looper.getMainLooper()).post {
                                val pressedColor = if (noteName.contains("#")) "#80ffe5".toColorInt() else "#FF80FF".toColorInt()
                                btn.setBackgroundColor(pressedColor)
                            }
                        }
                    }

                    // 2. Toca a nota pelo tempo da duração.
                    withTimeoutOrNull(duration) {
                        playNote(noteTrack, frequency)
                    }

                } finally {
                    // 3. Limpeza Garantida do AudioTrack (CRÍTICO)
                    if (noteTrack.playState == AudioTrack.PLAYSTATE_PLAYING || noteTrack.playState == AudioTrack.PLAYSTATE_PAUSED) {
                        try { noteTrack.stop() } catch (e: Exception) {}
                    }
                    noteTrack.release() // Libera o recurso de hardware

                    // 4. Retorna a cor original (UI Thread)
                    buttonId?.let { id ->
                        val button = findViewById<Button>(id)
                        button?.let { btn ->
                            Handler(Looper.getMainLooper()).post {
                                val defaultColor = if (noteName.contains("#")) "#000000".toColorInt() else "#FFB300".toColorInt()
                                btn.setBackgroundColor(defaultColor)
                            }
                        }
                    }
                }
            }

            for (note in piratesTheme) {
                if (!isActive) break

                val (noteName, duration) = note

                if (noteName == "WAIT") {
                    delay(duration)
                } else {
                    playAndDelay(noteName, duration)
                }
            }

            musicPlaybackJob = null
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        audioScope.cancel()

        activeTracks.values.forEach { (track, job) ->
            job.cancel()
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.release()
        }
        activeTracks.clear()

        musicPlaybackJob?.cancel()
        musicPlaybackJob = null
    }
}