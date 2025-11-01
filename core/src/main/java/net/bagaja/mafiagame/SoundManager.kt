package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Timer

/**
 * Manages all game sound effects, including 3D positioning, volume attenuation,
 * looping, and a simulated reverb/echo effect. Supports both procedural and file-based sounds.
 */
class SoundManager : Disposable {

    /**
     * A unique identifier for a procedurally generated sound effect.
     */
    enum class Effect {
        GUNSHOT_REVOLVER,
        FIRE_LOOP
    }

    private data class ActiveSound(
        val sound: Sound,
        val id: Long,
        val soundId: String,
        val position: Vector3
    )

    private val loadedSounds = mutableMapOf<String, Sound>()
    private val activeLoopingSounds = Array<ActiveSound>()
    private var listenerPosition: Vector3 = Vector3.Zero

    /**
     * No-op. Sounds are now loaded manually via the `load` methods.
     */
    fun initialize() {
        println("SoundManager initialized. Load sounds using load() methods.")
    }

    /**
     * Loads a procedural sound effect and stores it by its enum name.
     * @param effect The procedural effect to generate and load.
     */
    fun load(effect: Effect) {
        if (loadedSounds.containsKey(effect.name)) return
        println("Generating procedural SFX for: ${effect.name}")
        val sound = ProceduralSFXGenerator.generate(effect)
        loadedSounds[effect.name] = sound
    }

    /**
     * Loads a sound effect from an internal file path (e.g., "sounds/explosion.ogg").
     * @param id A unique string ID to refer to this sound.
     * @param filePath The path to the .ogg file inside the assets folder.
     */
    fun load(id: String, filePath: String) {
        if (loadedSounds.containsKey(id)) return
        try {
            println("Loading sound file: $filePath")
            val sound = Gdx.audio.newSound(Gdx.files.internal(filePath))
            loadedSounds[id] = sound
        } catch (e: Exception) {
            println("SoundManager ERROR: Could not load sound file at '$filePath': ${e.message}")
        }
    }

    fun update(listenerPos: Vector3) {
        this.listenerPosition = listenerPos
        activeLoopingSounds.forEach { activeSound ->
            val (volume, pan) = calculateVolumeAndPan(activeSound.position)
            activeSound.sound.setPan(activeSound.id, pan, volume)
        }
    }

    /** Overload for playing procedural effects easily. */
    fun playSound(
        effect: Effect,
        position: Vector3,
        loop: Boolean = false,
        maxRange: Float = 60f,
        reverb: Boolean = false
    ) {
        playSoundById(effect.name, position, loop, maxRange, reverb)
    }

    /** Main function to play any loaded sound by its string ID. */
    fun playSound(
        id: String,
        position: Vector3,
        loop: Boolean = false,
        maxRange: Float = 60f,
        reverb: Boolean = false
    ) {
        playSoundById(id, position, loop, maxRange, reverb)
    }

    private fun playSoundById(
        id: String,
        position: Vector3,
        loop: Boolean,
        maxRange: Float,
        reverb: Boolean
    ) {
        val sound = loadedSounds[id]
        if (sound == null) {
            println("SoundManager WARN: Sound with ID '$id' not loaded.")
            return
        }

        val (volume, pan) = calculateVolumeAndPan(position, maxRange)

        if (volume > 0.01f) {
            if (loop) {
                // Ensure we don't start a duplicate loop at the exact same position
                if (activeLoopingSounds.any { it.soundId == id && it.position.epsilonEquals(position, 0.1f) }) return
                val soundId = sound.loop(volume, 1.0f, pan)
                activeLoopingSounds.add(ActiveSound(sound, soundId, id, position))
            } else {
                sound.play(volume, 1.0f, pan)
                if (reverb) {
                    playEchoes(sound, volume, pan)
                }
            }
        }
    }

    /** Overload for stopping procedural effects. */
    fun stopLoopingSound(effect: Effect, position: Vector3) {
        stopLoopingSoundById(effect.name, position)
    }

    /** Main function to stop a looping sound by its ID and position. */
    fun stopLoopingSound(id: String, position: Vector3) {
        stopLoopingSoundById(id, position)
    }

    private fun stopLoopingSoundById(id: String, position: Vector3) {
        val soundToStop = activeLoopingSounds.find { it.soundId == id && it.position.epsilonEquals(position, 0.1f) }
        if (soundToStop != null) {
            soundToStop.sound.stop(soundToStop.id)
            activeLoopingSounds.removeValue(soundToStop, true)
        }
    }

    private fun calculateVolumeAndPan(soundPosition: Vector3, maxRange: Float = 60f): Pair<Float, Float> {
        val distance = listenerPosition.dst(soundPosition)
        val volume = (1.0f - (distance / maxRange)).coerceIn(0f, 1f)
        val direction = soundPosition.x - listenerPosition.x
        val pan = (direction / (maxRange * 0.5f)).coerceIn(-1.0f, 1.0f)
        return Pair(volume, pan)
    }

    private fun playEchoes(sound: Sound, initialVolume: Float, initialPan: Float) {
        val echoDelays = listOf(0.05f, 0.1f, 0.15f)
        val echoVolumeMultipliers = listOf(0.4f, 0.2f, 0.1f)

        for (i in echoDelays.indices) {
            Timer.schedule(object : Timer.Task() {
                override fun run() {
                    val echoVolume = initialVolume * echoVolumeMultipliers[i]
                    val echoPan = initialPan * -0.5f
                    sound.play(echoVolume, 0.95f, echoPan)
                }
            }, echoDelays[i])
        }
    }

    override fun dispose() {
        println("Disposing SoundManager and all loaded sounds.")
        // Use the correct variable name: loadedSounds
        loadedSounds.values.forEach { it.dispose() }
        loadedSounds.clear()
        activeLoopingSounds.forEach { it.sound.stop(it.id) }
        activeLoopingSounds.clear()
        ProceduralSFXGenerator.dispose()
    }
}
