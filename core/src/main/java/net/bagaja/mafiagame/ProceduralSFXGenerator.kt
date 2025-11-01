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
