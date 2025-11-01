package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * A self-contained, threaded procedural music generator.
 * This class is a refactored version of your original MusicGenerator.
 */
class ProceduralMusicGenerator(
    private val bpm: Double,
    private val kickPattern: String,
    private val snarePattern: String,
    private val hihatPattern: String,
    private val crashPattern: String,
    private val bassPattern: Array<Note?>,
    private val chordStabPattern: Array<Note?>,
    private val leadRiffPattern: Array<Note?>,
    private val tensionPulsePattern: Array<Note?>
) : Disposable {

    private val sampleRate = 44100
    private val audioDevice: AudioDevice = Gdx.audio.newAudioDevice(sampleRate, true)
    private var isThreadRunning = false
    private var musicThread: Thread? = null
    private var volume = 1.0f

    private val activeVoices = mutableListOf<Oscillator>()

    fun start() {
        if (isThreadRunning) return
        isThreadRunning = true
        musicThread = thread(start = true, isDaemon = true, name = "ProceduralMusicThread") {
            runLoop()
        }
    }

    fun stop() {
        isThreadRunning = false
        musicThread?.join(500) // Wait up to 500ms for the thread to finish
        musicThread = null
    }

    fun isPlaying(): Boolean {
        // The song is "playing" as long as its dedicated thread is alive.
        return musicThread?.isAlive ?: false
    }

    fun setVolume(newVolume: Float) {
        this.volume = newVolume.coerceIn(0f, 1f)
    }

    override fun dispose() {
        stop()
        audioDevice.dispose()
    }

    private fun runLoop() {
        var currentStep = 0
        val stepsPerBar = 16
        val totalSteps = bassPattern.size * (16 / 8) // Calculate total steps from bass pattern

        val beatsPerSecond = bpm / 60.0
        val stepsPerSecond = beatsPerSecond * 4.0 // 16th notes
        val samplesPerStep = (sampleRate / stepsPerSecond).toInt()

        var samplesUntilNextStep = samplesPerStep
        val buffer = ShortArray(512)

        while (isThreadRunning) {
            for (i in buffer.indices) {
                if (samplesUntilNextStep <= 0) {
                    samplesUntilNextStep += samplesPerStep
                    currentStep = (currentStep + 1) % totalSteps

                    // Drums
                    if (kickPattern[currentStep % kickPattern.length] == 'x') activeVoices.add(Oscillator(Waveform.KICK, Note.E1.freq, 1.2f, 0.25f))
                    if (snarePattern[currentStep % snarePattern.length] == 'x') activeVoices.add(Oscillator(Waveform.NOISE, Note.A5.freq, 0.4f, 0.08f))
                    if (hihatPattern[currentStep % hihatPattern.length] == 'x') activeVoices.add(Oscillator(Waveform.NOISE, Note.C7.freq, 0.12f, 0.05f))
                    if (crashPattern[currentStep % crashPattern.length] == 'x') activeVoices.add(Oscillator(Waveform.NOISE, Note.E4.freq, 0.3f, 0.4f))

                    // Instruments
                    bassPattern.getOrNull(currentStep)?.let { activeVoices.add(Oscillator(Waveform.SQUARE, it.freq, 0.6f, 0.12f)) }
                    chordStabPattern.getOrNull(currentStep)?.let { activeVoices.add(Oscillator(Waveform.TRIANGLE, it.freq, 0.35f, 0.4f)) }
                    leadRiffPattern.getOrNull(currentStep)?.let { activeVoices.add(Oscillator(Waveform.PULSE_HARD, it.freq, 0.35f, 0.2f)) }
                    tensionPulsePattern.getOrNull(currentStep)?.let { activeVoices.add(Oscillator(Waveform.SINE, it.freq, 0.08f, 0.15f)) }
                }

                var sample = 0.0f
                activeVoices.forEach { voice -> sample += voice.getSample(1.0 / sampleRate) }
                activeVoices.removeAll { it.isFinished }

                sample = (sample * this.volume * 0.16f).coerceIn(-1.0f, 1.0f) // Apply master and local volume
                buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                samplesUntilNextStep--
            }
            audioDevice.writeSamples(buffer, 0, buffer.size)
        }
    }

    // --- Private Inner Classes (Copied from your original file) ---
    private enum class Waveform { SINE, SQUARE, SAWTOOTH, TRIANGLE, NOISE, KICK, PULSE_HARD, VIOLIN_SAW }

    private class Oscillator(
        val waveform: Waveform,
        var frequency: Float,
        var amplitude: Float,
        val duration: Float
    ) {
        private var time = 0.0
        var isFinished = false
            private set
        private val attackTime = 0.005
        private val decayTime = (duration - attackTime).coerceAtLeast(0.005)

        fun getSample(deltaTime: Double): Float {
            if (isFinished) return 0.0f
            time += deltaTime
            if (time >= duration) {
                isFinished = true
                return 0.0f
            }

            // --- MODIFIED ENVELOPE ---
            val currentAttackTime = if (waveform == Waveform.VIOLIN_SAW) 0.05f else 0.005f // Slower attack for violin
            val currentDecayTime = (duration - currentAttackTime).coerceAtLeast(0.005F)
            val envelope = if (time < currentAttackTime) {
                (time / currentAttackTime).toFloat()
            } else {
                val t = ((time - currentAttackTime) / currentDecayTime).toFloat()
                1.0f - (t * t)
            }

            val value = when (waveform) {
                Waveform.SINE -> sin(2.0 * PI * frequency * time).toFloat()
                Waveform.SQUARE -> if (sin(2.0 * PI * frequency * time) > 0) 1.0f else -1.0f
                Waveform.SAWTOOTH -> (((time * frequency * 2.0) % 2.0) - 1.0).toFloat()
                Waveform.TRIANGLE -> ((if ((time * frequency) % 1.0 < 0.5) (time * frequency) % 1.0 * 4.0 - 1.0 else 3.0 - (time * frequency) % 1.0 * 4.0).toFloat())
                Waveform.NOISE -> MathUtils.random(-1.0f, 1.0f)
                Waveform.KICK -> sin(2.0 * PI * (frequency * (1.0f - (time / duration).toFloat()).coerceAtLeast(0.01f) * 15f) * time).toFloat()
                Waveform.PULSE_HARD -> if (sin(2.0 * PI * frequency * time) > 0.6) 1.0f else -1.0f

                // --- NEW VIOLIN WAVEFORM LOGIC ---
                Waveform.VIOLIN_SAW -> {
                    // Add vibrato: a gentle, periodic change in pitch
                    val vibratoRate = 5.0 // How fast the pitch wavers
                    val vibratoDepth = 0.005 // How much the pitch changes
                    val vibrato = sin(2.0 * PI * vibratoRate * time).toFloat() * vibratoDepth
                    val effectiveFrequency = frequency * (1f + vibrato)
                    // Generate the base sawtooth wave with the modulated frequency
                    (((time * effectiveFrequency * 2.0) % 2.0) - 1.0).toFloat()
                }
            }
            return value * amplitude * envelope
        }
    }

    // Note enum is required for the patterns
    @Suppress("unused")
    enum class Note(val freq: Float) {
        C1(32.70f), D1(36.71f), E1(41.20f), F1(43.65f), G1(49.00f), GS1(51.91f), A1(55.00f), B1(61.74f),
        C2(65.41f), CS2(69.30f), D2(73.42f), E2(82.41f), F2(87.31f), FS2(92.50f), G2(98.00f), GS2(103.83f), A2(110.00f), B2(123.47f),
        C3(130.81f), CS3(138.59f), D3(146.83f), E3(164.81f), F3(174.61f), FS3(185.00f), G3(196.00f), GS3(207.65f), A3(220.00f), B3(246.94f),
        C4(261.63f), CS4(277.18f), D4(293.66f), E4(329.63f), F4(349.23f), FS4(369.99f), G4(392.00f), A4(440.00f), B4(493.88f),
        C5(523.25f), D5(587.33f), E5(659.25f), A5(880.00f), C7(2093.00f)
    }
}
