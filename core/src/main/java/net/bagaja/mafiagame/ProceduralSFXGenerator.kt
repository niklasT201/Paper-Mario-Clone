package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Disposable
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates raw audio data for sound effects and converts them into LibGDX Sound objects.
 */
object ProceduralSFXGenerator : Disposable {

    private const val SAMPLE_RATE = 44100
    private val tempDirs = mutableListOf<FileHandle>()

    /**
     * Generates a sound effect based on the specified type and returns a LibGDX Sound object.
     */
    fun generate(effect: SoundManager.Effect): Sound {
        val pcmData = when (effect) {
            SoundManager.Effect.GUNSHOT_REVOLVER -> generateGunshot()
            SoundManager.Effect.FIRE_LOOP -> generateFireLoop()
            SoundManager.Effect.FIRE_SIZZLE -> generateFireSizzle()
            SoundManager.Effect.PUNCH_HIT -> generatePunchHit()
            SoundManager.Effect.ITEM_PICKUP -> generateItemPickup()
            SoundManager.Effect.RELOAD_CLICK -> generateReloadClick()
            SoundManager.Effect.GLASS_BREAK -> generateGlassBreak()
            SoundManager.Effect.CAR_CRASH_HEAVY -> generateCarCrash()
            SoundManager.Effect.DOOR_LOCKED -> generateDoorLocked()
            SoundManager.Effect.CAR_DOOR_OPEN -> generateCarDoor(open = true)
            SoundManager.Effect.CAR_DOOR_CLOSE -> generateCarDoor(open = false)
            SoundManager.Effect.PLAYER_HURT -> generatePlayerHurt()
            SoundManager.Effect.MELEE_SWOOSH -> generateMeleeSwoosh()
            SoundManager.Effect.FOOTSTEP -> generateFootstep()
            SoundManager.Effect.TELEPORT -> generateTeleport()
            SoundManager.Effect.MISSION_START -> generateArpeggio(floatArrayOf(261.63f, 329.63f, 392.00f), 0.12f) // C-E-G chord
            SoundManager.Effect.OBJECTIVE_COMPLETE -> generateArpeggio(floatArrayOf(392.00f, 523.25f, 659.25f), 0.1f) // G-C-E chord (higher)
            SoundManager.Effect.WATER_SPLASH -> generateWaterSplash()
            SoundManager.Effect.BLOOD_SQUISH -> generateBloodSquish()
            SoundManager.Effect.WOOD_SPLINTER -> generateWoodSplinter()
            SoundManager.Effect.LOOT_DROP -> generateLootDrop()
        }
        val wavData = convertToWav(pcmData)

        val tempDir = FileHandle.tempDirectory("mafiagame-sfx-${System.nanoTime()}")
        tempDirs.add(tempDir)
        val tempHandle = tempDir.child("sfx.wav")

        tempHandle.writeBytes(wavData, false)
        return Gdx.audio.newSound(tempHandle)
    }

    override fun dispose() {
        println("Cleaning up ${tempDirs.size} temporary SFX directories...")
        tempDirs.forEach { dir ->
            try {
                if (dir.exists()) dir.deleteDirectory()
            } catch (e: Exception) {
                println("Error cleaning up temp directory ${dir.path()}: ${e.message}")
            }
        }
        tempDirs.clear()
    }

    // --- Private Generation and Conversion Functions (Unchanged) ---
    private fun generateGunshot(): ShortArray {
        val durationSeconds = 0.3f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val noiseEnvelope = (1.0f - progress).coerceAtLeast(0f).pow(10)
            val noise = (Random.nextFloat() * 2f - 1f) * noiseEnvelope * 0.7f
            val thumpFrequency = 80f * (1.0f - progress).pow(2)
            val thumpEnvelope = (1.0f - progress).coerceAtLeast(0f).pow(3)
            val thump = sin(2f * PI * i * thumpFrequency / SAMPLE_RATE).toFloat() * thumpEnvelope * 1.0f
            val mixedSample = ((noise + thump) * 0.6f).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateFireLoop(): ShortArray {
        val durationSeconds = 2.0f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)
        var lowPass = 0f
        for (i in 0 until numSamples) {
            val noise = Random.nextFloat() * 2f - 1f
            lowPass += (noise - lowPass) * 0.1f
            val crackle = if (Random.nextFloat() < 0.05f) Random.nextFloat() * 1.5f else 1.0f
            val amplitudeEnvelope = (0.5f + sin(i * 0.1f) * 0.1f) * crackle
            val mixedSample = (lowPass * amplitudeEnvelope * 0.5f).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateFireSizzle(): ShortArray {
        val durationSeconds = 0.4f // Shorter duration
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples

            // A short burst of higher-frequency noise for the "sizzle"
            val noiseEnvelope = (1.0f - progress).pow(10) // Very fast decay
            val noise = (Random.nextFloat() * 2f - 1f) * noiseEnvelope

            // A subtle, higher-pitched thump for the "puff" of steam
            val thumpFrequency = 250f * (1.0f - progress).pow(2) // Higher starting pitch
            val thumpEnvelope = (1.0f - progress).pow(4)
            val thump = sin(2f * PI * i * thumpFrequency / SAMPLE_RATE).toFloat() * thumpEnvelope

            val mixedSample = ((noise * 0.7f) + (thump * 0.3f)).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * 0.6f * Short.MAX_VALUE).toInt().toShort() // Slightly quieter overall
        }
        return samples
    }

    /**
     * Generates a punch/melee impact sound.
     */
    private fun generatePunchHit(): ShortArray {
        val durationSeconds = 0.15f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples

            // A very fast decaying burst of noise for the "smack"
            val noiseEnvelope = (1.0f - progress).pow(8)
            val noise = (Random.nextFloat() * 2f - 1f) * noiseEnvelope * 0.6f

            // A quick, low-frequency thump for the "oomph"
            val thumpFrequency = 100f
            val thumpEnvelope = (1.0f - progress).pow(4)
            val thump = sin(2f * PI * i * thumpFrequency / SAMPLE_RATE).toFloat() * thumpEnvelope * 0.4f

            val mixedSample = (noise + thump).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /**
     * Generates a classic, rising-pitch "pickup" sound.
     */
    private fun generateItemPickup(): ShortArray {
        val durationSeconds = 0.12f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val frequency = 880f + (progress * 880f)
            val envelope = (1.0f - progress).pow(2)
            val sample = sin(2f * PI * i * frequency / SAMPLE_RATE).toFloat() * envelope

            samples[i] = (sample * 0.4f * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /**
     * Generates a sharp, metallic "click" sound.
     */
    private fun generateReloadClick(): ShortArray {
        val durationSeconds = 0.05f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            // A very short, high-frequency burst of noise
            val noiseEnvelope = (1.0f - progress).pow(10)
            val noise = (Random.nextFloat() * 2f - 1f) * noiseEnvelope
            samples[i] = (noise * 0.5f * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /**
     * Generates a glass-shattering sound.
     */
    private fun generateGlassBreak(): ShortArray {
        val durationSeconds = 0.8f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            var sample = 0f

            // Mix multiple high-frequency sine waves with different decays for the "shatter"
            val freq1 = 4000f + sin(progress * 10f) * 200f
            val env1 = (1.0f - progress).pow(8)
            sample += sin(2f * PI * i * freq1 / SAMPLE_RATE).toFloat() * env1 * 0.3f

            val freq2 = 6000f + sin(progress * 15f) * 300f
            val env2 = (1.0f - progress).pow(12)
            sample += sin(2f * PI * i * freq2 / SAMPLE_RATE).toFloat() * env2 * 0.3f

            // Add high-frequency noise for the "crash"
            val noiseEnv = (1.0f - progress).pow(10)
            sample += (Random.nextFloat() * 2f - 1f) * noiseEnv * 0.4f

            samples[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /**
     * Generates a heavy car crash sound with a thud and scrape.
     */
    private fun generateCarCrash(): ShortArray {
        val durationSeconds = 1.0f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples

            // Low frequency thud for the initial impact
            val thumpFrequency = 60f
            val thumpEnvelope = (1.0f - progress).pow(6)
            val thump = sin(2f * PI * i * thumpFrequency / SAMPLE_RATE).toFloat() * thumpEnvelope * 0.7f

            // Decaying noise for the crunching/scraping metal
            val noiseEnvelope = (1.0f - progress).pow(8)
            val noise = (Random.nextFloat() * 2f - 1f) * noiseEnvelope * 0.3f

            val mixedSample = (thump + noise).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateDoorLocked(): ShortArray {
        val durationSeconds = 0.15f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            // A quick, deep thump for the door impact
            val thumpFreq = 100f * (1.0f - progress).pow(4)
            val thumpEnv = (1.0f - progress).pow(6)
            val thump = sin(2f * PI * i * thumpFreq / SAMPLE_RATE).toFloat() * thumpEnv * 0.7f

            // A short metallic click for the handle rattle
            val clickEnv = (1.0f - progress).pow(25)
            val click = (Random.nextFloat() * 2f - 1f) * clickEnv * 0.3f

            val mixedSample = (thump + click).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateCarDoor(open: Boolean): ShortArray {
        val durationSeconds = if (open) 0.2f else 0.25f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            // Low-frequency thump
            val thumpFreq = 80f
            val thump = sin(2f * PI * i * thumpFreq / SAMPLE_RATE).toFloat()
            // High-frequency metallic click/latch sound
            val click = sin(2f * PI * i * 2500f / SAMPLE_RATE).toFloat()

            val envelope = (1.0f - progress).pow(if (open) 8 else 5)

            val mixedSample = ((thump * 0.4f) + (click * 0.6f)) * envelope
            samples[i] = (mixedSample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generatePlayerHurt(): ShortArray {
        val durationSeconds = 0.12f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            // A low sawtooth wave with a rapid pitch drop to simulate an "oof"
            val freq = 150f * (1.0f - progress).pow(2)
            val env = (1.0f - progress).pow(3)
            val saw = (((i.toFloat() / SAMPLE_RATE * freq * 2.0f) % 2.0f) - 1.0f) * env

            val mixedSample = (saw * 0.5f).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateMeleeSwoosh(): ShortArray {
        val durationSeconds = 0.18f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)
        var lowPass = 0f

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val noise = Random.nextFloat() * 2f - 1f
            // Apply a simple low-pass filter to soften it
            lowPass += (noise - lowPass) * 0.4f
            // Create an envelope that fades in and out to create the "swoosh" shape
            val envelope = sin(progress * PI.toFloat())

            val mixedSample = (lowPass * envelope * 0.4f).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateFootstep(): ShortArray {
        val durationSeconds = 0.1f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)
        var lowPass = 0f

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val noise = Random.nextFloat() * 2f - 1f
            // Heavy low-pass filter to make it sound like a soft "thump"
            lowPass += (noise - lowPass) * 0.7f
            val envelope = (1.0f - progress).pow(6)

            val mixedSample = (lowPass * envelope * 0.6f).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateTeleport(): ShortArray {
        val durationSeconds = 0.4f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            // A sawtooth wave that rapidly sweeps upwards in frequency
            val freq = 440f + (progress * progress * 1500f)
            val envelope = (1.0f - progress).pow(2) // Fade out
            val saw = (((i.toFloat() / SAMPLE_RATE * freq * 2.0f) % 2.0f) - 1.0f) * envelope

            val mixedSample = (saw * 0.4f).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateArpeggio(notes: FloatArray, noteDuration: Float): ShortArray {
        val totalDuration = notes.size * noteDuration
        val numSamples = (SAMPLE_RATE * totalDuration).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val noteIndex = (progress * notes.size).toInt().coerceIn(0, notes.size - 1)
            val freq = notes[noteIndex]

            // Short envelope for each note
            val progressInNote = (i % (SAMPLE_RATE * noteDuration)) / (SAMPLE_RATE * noteDuration)
            val noteEnvelope = (1.0f - progressInNote).pow(4)

            val sine = sin(2f * PI * i * freq / SAMPLE_RATE).toFloat()
            val mixedSample = (sine * noteEnvelope * 0.4f).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateBloodSquish(): ShortArray {
        val durationSeconds = 0.25f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)
        var lowPass = 0f

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val noise = Random.nextFloat() * 2f - 1f
            // A heavier low-pass filter to make it sound "thicker"
            lowPass += (noise - lowPass) * 0.6f

            // A low-frequency thump that drops in pitch
            val thumpFrequency = 120f * (1.0f - progress).pow(3)
            val thump = sin(2f * PI * i * thumpFrequency / SAMPLE_RATE).toFloat()

            // A fast decaying envelope
            val envelope = (1.0f - progress).pow(7)

            val mixedSample = ((lowPass * 0.6f) + (thump * 0.4f)) * envelope
            samples[i] = (mixedSample.coerceIn(-1f, 1f) * 0.4f * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /**
     * Generates a short, sharp splash sound for stepping in water.
     */
    private fun generateWaterSplash(): ShortArray {
        val durationSeconds = 0.2f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)
        var lowPass = 0f

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            // Start with white noise for the "splash"
            val noise = Random.nextFloat() * 2f - 1f
            // Apply a simple low-pass filter to make it sound less harsh
            lowPass += (noise - lowPass) * 0.4f

            // A quick, sharp decay
            val envelope = (1.0f - progress).pow(6)

            val mixedSample = lowPass * envelope
            samples[i] = (mixedSample.coerceIn(-1f, 1f) * 0.5f * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateWoodSplinter(): ShortArray {
        val durationSeconds = 0.1f // Very short and sharp
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples

            // High-frequency noise for the "crack"
            val noiseEnvelope = (1.0f - progress).pow(12) // Very rapid decay
            val noise = (Random.nextFloat() * 2f - 1f) * noiseEnvelope

            // A quick, downward-pitching tone for the "splinter" sound
            val toneFrequency = 2000f * (1.0f - progress).pow(4) // Starts high, drops fast
            val toneEnvelope = (1.0f - progress).pow(8)
            val tone = sin(2f * PI * i * toneFrequency / SAMPLE_RATE).toFloat() * toneEnvelope

            // Mix them, giving more weight to the noisy crackle
            val mixedSample = ((noise * 0.7f) + (tone * 0.3f)).coerceIn(-1f, 1f)

            // Make it relatively quiet
            samples[i] = (mixedSample * 0.4f * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun generateLootDrop(): ShortArray {
        val durationSeconds = 0.3f // Slightly longer than a pickup sound
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples

            // --- Main Tone (Falling Pitch) ---
            val startFreq = 700f
            val endFreq = 350f
            // A sine wave that sweeps from a high pitch to a lower one
            val frequency = startFreq - (progress * (startFreq - endFreq))
            val envelope = (1.0f - progress).pow(3) // Let it ring out a little
            val mainTone = sin(2f * PI * i * frequency / SAMPLE_RATE).toFloat() * envelope

            // --- Sparkle Effect (Quick burst of high-frequency noise) ---
            val sparkleEnvelope = (1.0f - progress).pow(10) // Decays very quickly
            val sparkleNoise = (Random.nextFloat() * 2f - 1f) * sparkleEnvelope * 0.2f // Keep it subtle

            // Mix the two sounds together
            val mixedSample = (mainTone * 0.8f + sparkleNoise).coerceIn(-1f, 1f)

            // Set final volume
            samples[i] = (mixedSample * 0.5f * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun DataOutputStream.writeLittleEndianInt(value: Int) {
        writeByte(value and 0xFF); writeByte((value ushr 8) and 0xFF); writeByte((value ushr 16) and 0xFF); writeByte((value ushr 24) and 0xFF)
    }

    private fun DataOutputStream.writeLittleEndianShort(value: Short) {
        writeByte(value.toInt() and 0xFF); writeByte((value.toInt() ushr 8) and 0xFF)
    }

    private fun convertToWav(pcmData: ShortArray): ByteArray {
        val byteStream = ByteArrayOutputStream()
        val dataStream = DataOutputStream(byteStream)
        val byteData = ByteArray(pcmData.size * 2)
        pcmData.forEachIndexed { i, sh ->
            byteData[i * 2] = (sh.toInt() and 0xff).toByte()
            byteData[i * 2 + 1] = (sh.toInt() shr 8 and 0xff).toByte()
        }
        dataStream.writeBytes("RIFF"); dataStream.writeLittleEndianInt(36 + byteData.size); dataStream.writeBytes("WAVE"); dataStream.writeBytes("fmt "); dataStream.writeLittleEndianInt(16); dataStream.writeLittleEndianShort(1.toShort()); dataStream.writeLittleEndianShort(1.toShort()); dataStream.writeLittleEndianInt(SAMPLE_RATE); dataStream.writeLittleEndianInt(SAMPLE_RATE * 2); dataStream.writeLittleEndianShort(2.toShort()); dataStream.writeLittleEndianShort(16.toShort()); dataStream.writeBytes("data"); dataStream.writeLittleEndianInt(byteData.size); dataStream.write(byteData); dataStream.flush()
        return byteStream.toByteArray()
    }

    private fun Float.pow(n: Int): Float {
        var result = 1.0f; repeat(n) { result *= this }; return result
    }
}
