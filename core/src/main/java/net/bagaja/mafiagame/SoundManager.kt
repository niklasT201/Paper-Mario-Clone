package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Timer
import kotlin.random.Random

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
        FIRE_LOOP,
        EXPLOSION,          // For dynamite, car explosions, etc.
        PUNCH_HIT,          // For melee impacts
        ITEM_PICKUP,        // A classic "blip" sound for collecting items
        RELOAD_CLICK,       // A sharp, metallic click for reloading
        GLASS_BREAK,        // For windows or destructible objects
        CAR_CRASH_HEAVY     // For car collisions
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
    private var masterVolume = 1.0f
    private var sfxVolume = 1.0f

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

    fun updateLoopingSoundPosition(instanceId: Long, newPosition: Vector3) {
        val activeSound = activeLoopingSounds.find { it.id == instanceId }
        activeSound?.position?.set(newPosition)
    }

    /**
     * Pauses all currently active looping sounds.
     */
    fun pauseAllLoopingSounds() {
        if (activeLoopingSounds.isEmpty) return
        println("Pausing ${activeLoopingSounds.size} looping sound(s).")
        for (activeSound in activeLoopingSounds) {
            activeSound.sound.pause(activeSound.id)
        }
    }

    /**
     * Resumes all currently active looping sounds.
     */
    fun resumeAllLoopingSounds() {
        if (activeLoopingSounds.isEmpty) return
        println("Resuming ${activeLoopingSounds.size} looping sound(s).")
        for (activeSound in activeLoopingSounds) {
            activeSound.sound.resume(activeSound.id)
        }
    }

    /** Overload for playing procedural effects easily. Returns the sound instance ID or null. */
    fun playSound(
        effect: Effect,
        position: Vector3,
        loop: Boolean = false,
        maxRange: Float = 60f,
        reverb: Boolean = false
    ): Long? {
        return playSoundById(effect.name, position, loop, maxRange, reverb)
    }

    /** Main function to play any loaded sound by its string ID. Returns the sound instance ID or null. */
    fun playSound(
        id: String,
        position: Vector3,
        loop: Boolean = false,
        maxRange: Float = 60f,
        reverb: Boolean = false
    ): Long? {
        return playSoundById(id, position, loop, maxRange, reverb)
    }

    private fun playSoundById(
        id: String,
        position: Vector3,
        loop: Boolean,
        maxRange: Float,
        reverb: Boolean
    ): Long? {
        val sound = loadedSounds[id]
        if (sound == null) {
            println("SoundManager WARN: Sound with ID '$id' not loaded.")
            return null // Sound doesn't exist, return null
        }

        val (volume, pan) = calculateVolumeAndPan(position, maxRange)

        if (volume > 0.01f) {
            // Generate a random pitch for this specific sound instance
            val pitch = 1.0f + (Random.nextFloat() * 0.04f - 0.02f)

            if (loop) {
                // Prevent duplicate loops at the same spot
                if (activeLoopingSounds.any { it.soundId == id && it.position.epsilonEquals(position, 0.1f) }) {
                    return null
                }

                // Play the looping sound and get its unique instance ID
                val soundInstanceId = sound.loop(volume, pitch, pan)

                // Track this active loop so we can stop it later
                activeLoopingSounds.add(ActiveSound(sound, soundInstanceId, id, position))

                // Return the ID so the caller can manage it (e.g., PlayerSystem)
                return soundInstanceId
            } else {
                // This is a one-shot sound effect
                val soundInstanceId = sound.play(volume, pitch, pan)

                if (reverb) {
                    playEchoes(sound, volume, pan, pitch)
                }

                // Return the ID for the one-shot sound
                return soundInstanceId
            }
        }

        // The sound was too far away to be heard, so it wasn't played.
        return null
    }

    /** Overload for stopping procedural effects. */
    fun stopLoopingSound(effect: Effect, position: Vector3) {
        stopLoopingSoundById(effect.name, position)
    }

    /** Main function to stop a looping sound by its ID and position. */
    fun stopLoopingSound(instanceId: Long) {
        // Find the active sound by its unique instance ID
        val soundToStop = activeLoopingSounds.find { it.id == instanceId }

        if (soundToStop != null) {
            soundToStop.sound.stop(soundToStop.id)
            activeLoopingSounds.removeValue(soundToStop, true)
        }
    }

    private fun stopLoopingSoundById(id: String, position: Vector3) {
        val soundToStop = activeLoopingSounds.find { it.soundId == id && it.position.epsilonEquals(position, 0.1f) }
        if (soundToStop != null) {
            soundToStop.sound.stop(soundToStop.id)
            activeLoopingSounds.removeValue(soundToStop, true)
        }
    }

    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
    }

    fun setSfxVolume(volume: Float) {
        sfxVolume = volume.coerceIn(0f, 1f)
    }

    private fun calculateVolumeAndPan(soundPosition: Vector3, maxRange: Float = 60f): Pair<Float, Float> {
        val distance = listenerPosition.dst(soundPosition)
        val distanceVolume = (1.0f - (distance / maxRange)).coerceIn(0f, 1f)

        // NEW: Calculate final volume by multiplying all factors
        val finalVolume = distanceVolume * masterVolume * sfxVolume

        val direction = soundPosition.x - listenerPosition.x
        val pan = (direction / (maxRange * 0.5f)).coerceIn(-1.0f, 1.0f)
        return Pair(finalVolume, pan) // Return the final calculated volume
    }

    private fun playEchoes(sound: Sound, initialVolume: Float, initialPan: Float, basePitch: Float) {
        val echoDelays = listOf(0.05f, 0.1f, 0.15f)
        val echoVolumeMultipliers = listOf(0.4f, 0.2f, 0.1f)

        for (i in echoDelays.indices) {
            Timer.schedule(object : Timer.Task() {
                override fun run() {
                    val echoVolume = initialVolume * echoVolumeMultipliers[i]
                    val echoPan = initialPan * -0.5f
                    // Slightly alter the pitch of the echo for more richness
                    val echoPitch = basePitch * 0.98f
                    sound.play(echoVolume, echoPitch, echoPan)
                }
            }, echoDelays[i])
        }
    }

    fun loadWeaponSound(baseId: String, variationCount: Int, proceduralFallback: Effect) {
        if (variationCount <= 0) return

        println("Loading weapon sound set for '$baseId' with $variationCount variations...")
        for (i in 1..variationCount) {
            val variationId = "${baseId}_V$i"
            val filePath = "sounds/${baseId.lowercase()}_v$i.ogg"
            val fileHandle = Gdx.files.internal(filePath)

            val sound = if (fileHandle.exists()) {
                // File exists, load it!
                println("  -> Loading '$variationId' from file: $filePath")
                Gdx.audio.newSound(fileHandle)
            } else {
                // File NOT found, generate the fallback.
                println("  -> WARNING: File not found for '$variationId' at '$filePath'. Generating procedural fallback.")
                ProceduralSFXGenerator.generate(proceduralFallback)
            }
            loadedSounds[variationId] = sound
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
