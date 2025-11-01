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
 * Generates raw audio data for sound effects from code and converts them into
 * LibGDX Sound objects. This object is responsible for cleaning up the temporary
 * files it creates upon disposal.
 */
// MODIFIED: Implemented Disposable for cleanup
object ProceduralSFXGenerator : Disposable {

    private const val SAMPLE_RATE = 44100
    // NEW: A list to keep track of the temporary directories we create.
    private val tempDirs = mutableListOf<FileHandle>()

    /**
     * Generates a sound effect based on the specified type and returns a LibGDX Sound object.
     */
    fun generate(effect: SoundManager.SoundEffect): Sound {
        val pcmData = when (effect) {
            SoundManager.SoundEffect.GUNSHOT_REVOLVER -> generateGunshot()
            SoundManager.SoundEffect.FIRE_LOOP -> generateFireLoop()
        }
        val wavData = convertToWav(pcmData)

        // --- MODIFIED BLOCK ---
        // 1. Create a temporary directory.
        val tempDir = FileHandle.tempDirectory("mafiagame-sfx")
        // 2. Add this directory to our list so we can delete it later.
        tempDirs.add(tempDir)
        // 3. Create our own file handle with the correct .wav extension inside that directory.
        val tempHandle = tempDir.child("sfx_${System.nanoTime()}.wav")
        // 4. REMOVED the problematic deleteOnExit() call.
        // --- END OF MODIFIED BLOCK ---

        tempHandle.writeBytes(wavData, false)
        return Gdx.audio.newSound(tempHandle)
    }

    // NEW: This method will be called when the game shuts down to clean up our temp files.
    override fun dispose() {
        println("Cleaning up ${tempDirs.size} temporary SFX directories...")
        tempDirs.forEach { dir ->
            try {
                if (dir.exists()) {
                    // deleteDirectory() removes the folder and all files inside it.
                    dir.deleteDirectory()
                }
            } catch (e: Exception) {
                println("Error cleaning up temp directory ${dir.path()}: ${e.message}")
            }
        }
        tempDirs.clear()
    }


    // --- The rest of the file is unchanged ---

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
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
        writeByte((value ushr 16) and 0xFF)
        writeByte((value ushr 24) and 0xFF)
    }

    private fun DataOutputStream.writeLittleEndianShort(value: Short) {
        writeByte(value.toInt() and 0xFF)
        writeByte((value.toInt() ushr 8) and 0xFF)
    }

    private fun convertToWav(pcmData: ShortArray): ByteArray {
        val byteStream = ByteArrayOutputStream()
        val dataStream = DataOutputStream(byteStream)
        val byteData = ByteArray(pcmData.size * 2)
        pcmData.forEachIndexed { i, sh ->
            byteData[i * 2] = (sh.toInt() and 0xff).toByte()
            byteData[i * 2 + 1] = (sh.toInt() shr 8 and 0xff).toByte()
        }

        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val subChunk2Size = pcmData.size * channels * bitsPerSample / 8
        val chunkSize = 36 + subChunk2Size

        dataStream.writeBytes("RIFF")
        dataStream.writeLittleEndianInt(chunkSize)
        dataStream.writeBytes("WAVE")
        dataStream.writeBytes("fmt ")
        dataStream.writeLittleEndianInt(16)
        dataStream.writeLittleEndianShort(1.toShort())
        dataStream.writeLittleEndianShort(channels.toShort())
        dataStream.writeLittleEndianInt(SAMPLE_RATE)
        dataStream.writeLittleEndianInt(byteRate)
        dataStream.writeLittleEndianShort((channels * bitsPerSample / 8).toShort())
        dataStream.writeLittleEndianShort(bitsPerSample.toShort())
        dataStream.writeBytes("data")
        dataStream.writeLittleEndianInt(subChunk2Size)
        dataStream.write(byteData)
        dataStream.flush()

        return byteStream.toByteArray()
    }

    private fun Float.pow(n: Int): Float {
        var result = 1.0f
        for (i in 1..n) result *= this
        return result
    }
}
