package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates procedural chiptune-style music from code, inspired by retro consoles.
 * Action version - intense shootout music with driving rhythm and tension.
 */
class MusicGenerator : Disposable {

    // --- Core Audio Components ---
    private val sampleRate = 44100
    private val audioDevice: AudioDevice = Gdx.audio.newAudioDevice(sampleRate, true)
    private var isPlaying = false
    private lateinit var musicThread: Thread

    // --- Musical Properties ---
    private var bpm = 145.0 // Fast, intense tempo for action
    private var volume = 0.16f

    // --- Sequencer Patterns (4/4 time - 16 steps per bar for driving rhythm) ---
    // Aggressive, constant kick drum - like a machine gun heartbeat
    private val kickPattern  = "x---x---x---x---"
    // Tight snare hits on backbeat
    private val snarePattern = "----x-------x---"
    // Rapid hi-hats for urgency
    private val hihatPattern = "x-x-x-x-x-x-x-x-"
    // Occasional crash for emphasis (must match 64 steps)
    private val crashPattern = "x---------------x---------------x---------------x---------------"

    // Aggressive progression: E5 - A5 - D5 - A5 (power chord style, intense and driving)
    // Using 5th chords (no 3rd) for that raw, aggressive sound
    private val bassPattern = arrayOf(
        // E5 - aggressive entry
        Note.E2, Note.E2, null, Note.E3, null, Note.E2, null, null,
        Note.E2, Note.E2, null, Note.E3, null, Note.E2, null, null,
        // A5 - building tension
        Note.A1, Note.A1, null, Note.A2, null, Note.A1, null, null,
        Note.A1, Note.A1, null, Note.A2, null, Note.A1, null, null,
        // D5 - peak intensity
        Note.D2, Note.D2, null, Note.D3, null, Note.D2, null, null,
        Note.D2, Note.D2, null, Note.D3, null, Note.D2, null, null,
        // A5 - driving forward
        Note.A1, Note.A1, null, Note.A2, null, Note.A1, Note.A2, null,
        Note.A1, Note.A1, null, Note.A2, null, Note.A1, Note.A2, Note.A2
    )

    // Fast power chord stabs
    private val chordStabPattern = arrayOf(
        // E5 power chord
        Note.E3, null, null, Note.B3, null, null, null, null,
        Note.E3, null, null, Note.B3, null, null, null, null,
        // A5 power chord
        Note.A3, null, null, Note.E4, null, null, null, null,
        Note.A3, null, null, Note.E4, null, null, null, null,
        // D5 power chord
        Note.D3, null, null, Note.A3, null, null, null, null,
        Note.D3, null, null, Note.A3, null, null, null, null,
        // A5 power chord with more hits
        Note.A3, null, null, Note.E4, null, null, Note.A3, null,
        Note.A3, null, Note.E4, null, Note.A3, null, Note.E4, null
    )

    // Aggressive lead riff - like an action movie chase scene
    private val leadRiffPattern = arrayOf(
        Note.E4, null, Note.FS4, Note.G4, null, Note.FS4, Note.E4, null,
        Note.D4, null, Note.E4, null, Note.FS4, null, null, null,
        Note.A4, null, Note.G4, Note.FS4, null, Note.E4, Note.D4, null,
        Note.E4, null, Note.FS4, null, Note.A4, null, null, null,
        Note.D4, null, Note.E4, Note.FS4, null, Note.G4, Note.A4, null,
        Note.B4, null, Note.A4, null, Note.G4, null, null, null,
        Note.A4, null, Note.G4, null, Note.FS4, null, Note.E4, null,
        Note.D4, null, Note.E4, null, Note.A3, Note.B3, Note.CS4, Note.D4
    )

    // Pulsing synth for tension
    private val tensionPulsePattern = arrayOf(
        Note.E5, null, null, null, Note.E5, null, null, null,
        Note.E5, null, null, null, Note.E5, null, null, null,
        Note.A4, null, null, null, Note.A4, null, null, null,
        Note.A4, null, null, null, Note.A4, null, null, null,
        Note.D5, null, null, null, Note.D5, null, null, null,
        Note.D5, null, null, null, Note.D5, null, null, null,
        Note.A4, null, null, null, Note.A4, null, null, null,
        Note.A4, null, null, Note.A4, null, Note.A4, null, null
    )

    // --- Synthesis Components ---
    private val activeVoices = mutableListOf<Oscillator>()

    fun start() {
        if (isPlaying) return
        isPlaying = true
        musicThread = thread(start = true, isDaemon = true, name = "MusicThread") {
            run()
        }
    }

    fun stop() {
        isPlaying = false
        musicThread.join(1000)
    }

    private fun run() {
        var currentStep = 0
        val stepsPerBar = 16
        val totalSteps = 128 // Match our actual pattern length (8 bars × 16 steps)

        val beatsPerSecond = bpm / 60.0
        val stepsPerSecond = beatsPerSecond * 4.0 // 16th notes
        val samplesPerStep = (sampleRate / stepsPerSecond).toInt()

        var samplesUntilNextStep = samplesPerStep
        val buffer = ShortArray(512)

        while (isPlaying) {
            for (i in buffer.indices) {
                if (samplesUntilNextStep <= 0) {
                    samplesUntilNextStep += samplesPerStep
                    currentStep = (currentStep + 1) % totalSteps

                    // --- Aggressive drums ---
                    if (kickPattern[currentStep % kickPattern.length] == 'x') {
                        activeVoices.add(Oscillator(Waveform.KICK, Note.E1.freq, 1.2f, 0.25f))
                    }
                    if (snarePattern[currentStep % snarePattern.length] == 'x') {
                        activeVoices.add(Oscillator(Waveform.NOISE, Note.A5.freq, 0.4f, 0.08f))
                    }
                    if (hihatPattern[currentStep % hihatPattern.length] == 'x') {
                        activeVoices.add(Oscillator(Waveform.NOISE, Note.C7.freq, 0.12f, 0.05f))
                    }
                    if (crashPattern[currentStep % crashPattern.length] == 'x') {
                        activeVoices.add(Oscillator(Waveform.NOISE, Note.E4.freq, 0.3f, 0.4f))
                    }

                    // --- Driving bass ---
                    bassPattern.getOrNull(currentStep)?.let { note ->
                        activeVoices.add(Oscillator(Waveform.SQUARE, note.freq, 0.6f, 0.12f))
                    }

                    // --- Power chord stabs ---
                    chordStabPattern.getOrNull(currentStep)?.let { note ->
                        activeVoices.add(Oscillator(Waveform.SAWTOOTH, note.freq, 0.25f, 0.1f))
                    }

                    // --- Aggressive lead riff ---
                    leadRiffPattern.getOrNull(currentStep)?.let { note ->
                        activeVoices.add(Oscillator(Waveform.PULSE_HARD, note.freq, 0.35f, 0.2f))
                    }

                    // --- Tension pulse (high pitched urgency) ---
                    tensionPulsePattern.getOrNull(currentStep)?.let { note ->
                        activeVoices.add(Oscillator(Waveform.SINE, note.freq, 0.08f, 0.15f))
                    }
                }

                var sample = 0.0f
                activeVoices.forEach { voice ->
                    sample += voice.getSample(1.0 / sampleRate)
                }
                activeVoices.removeAll { it.isFinished }

                // Hard limiting for that aggressive, compressed sound
                sample = (sample * volume).coerceIn(-1.0f, 1.0f)
                buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                samplesUntilNextStep--
            }
            audioDevice.writeSamples(buffer, 0, buffer.size)
        }
    }

    override fun dispose() {
        stop()
        audioDevice.dispose()
    }

    private enum class Waveform { SINE, SQUARE, SAWTOOTH, TRIANGLE, NOISE, KICK, PULSE_HARD }

    private class Oscillator(
        val waveform: Waveform,
        var frequency: Float,
        var amplitude: Float,
        val duration: Float
    ) {
        private var time = 0.0
        var isFinished = false
            private set

        private val attackTime = 0.005 // Very fast attack for punch
        private val decayTime = (duration - attackTime).coerceAtLeast(0.005)

        fun getSample(deltaTime: Double): Float {
            if (isFinished) return 0.0f
            time += deltaTime
            if (time >= duration) {
                isFinished = true
                return 0.0f
            }

            // Sharp, aggressive envelope
            val envelope = if (time < attackTime) {
                (time / attackTime).toFloat()
            } else {
                val t = ((time - attackTime) / decayTime).toFloat()
                1.0f - (t * t) // Quick decay
            }

            val value = when (waveform) {
                Waveform.SINE -> sin(2.0 * PI * frequency * time).toFloat()
                Waveform.SQUARE -> if (sin(2.0 * PI * frequency * time) > 0) 1.0f else -1.0f
                Waveform.SAWTOOTH -> (((time * frequency * 2.0) % 2.0) - 1.0).toFloat()
                Waveform.TRIANGLE -> {
                    val phase = (time * frequency) % 1.0
                    (if (phase < 0.5) phase * 4.0 - 1.0 else 3.0 - phase * 4.0).toFloat()
                }
                Waveform.NOISE -> MathUtils.random(-1.0f, 1.0f)
                Waveform.KICK -> {
                    val kickFreq = frequency * (1.0f - (time / duration).toFloat()).coerceAtLeast(0.01f) * 15f
                    sin(2.0 * PI * kickFreq * time).toFloat()
                }
                Waveform.PULSE_HARD -> {
                    // Aggressive pulse wave with narrow pulse width
                    val pulse = sin(2.0 * PI * frequency * time)
                    if (pulse > 0.6) 1.0f else -1.0f
                }
            }
            return value * amplitude * envelope
        }
    }

    private enum class Note(val freq: Float) {
        D1(36.71f), E1(41.20f), F1(43.65f), G1(49.00f), GS1(51.91f), A1(55.00f), B1(61.74f),
        C2(65.41f), CS2(69.30f), D2(73.42f), E2(82.41f), F2(87.31f), FS2(92.50f), G2(98.00f), GS2(103.83f), A2(110.00f), B2(123.47f),
        C3(130.81f), CS3(138.59f), D3(146.83f), E3(164.81f), F3(174.61f), FS3(185.00f), G3(196.00f), GS3(207.65f), A3(220.00f), B3(246.94f),
        C4(261.63f), CS4(277.18f), D4(293.66f), E4(329.63f), F4(349.23f), FS4(369.99f), G4(392.00f), A4(440.00f), B4(493.88f),
        D5(587.33f), E5(659.25f),
        // Notes for drums/effects
        A5(880.00f), C7(2093.00f)
    }
}
