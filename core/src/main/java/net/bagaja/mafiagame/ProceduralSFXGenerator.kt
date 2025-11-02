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
            SoundManager.Effect.EXPLOSION -> generateExplosion()
            SoundManager.Effect.PUNCH_HIT -> generatePunchHit()
            SoundManager.Effect.ITEM_PICKUP -> generateItemPickup()
            SoundManager.Effect.RELOAD_CLICK -> generateReloadClick()
            SoundManager.Effect.GLASS_BREAK -> generateGlassBreak()
            SoundManager.Effect.CAR_CRASH_HEAVY -> generateCarCrash()
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

    private fun generateExplosion(): ShortArray {
        val durationSeconds = 1.2f
        val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples

            // 1. A deep, powerful thump that quickly fades
            val thumpFrequency = 40f * (1.0f - progress).pow(4) // Rapidly falling pitch
            val thumpEnvelope = (1.0f - progress).pow(2)
            val thump = sin(2f * PI * i * thumpFrequency / SAMPLE_RATE).toFloat() * thumpEnvelope

            // 2. A long tail of crackling noise
            val noiseEnvelope = (1.0f - progress).pow(6)
            val noise = (Random.nextFloat() * 2f - 1f) * noiseEnvelope

            val mixedSample = ((thump * 0.8f) + (noise * 0.2f)).coerceIn(-1f, 1f)
            samples[i] = (mixedSample * Short.MAX_VALUE).toInt().toShort()
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
