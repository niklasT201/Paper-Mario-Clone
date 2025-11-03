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
        FIRE_SIZZLE,          // For dynamite, car explosions, etc.
        PUNCH_HIT,          // For melee impacts
        ITEM_PICKUP,        // A classic "blip" sound for collecting items
        RELOAD_CLICK,       // A sharp, metallic click for reloading
        GLASS_BREAK,        // For windows or destructible objects
        CAR_CRASH_HEAVY,     // For car collisions
        DOOR_LOCKED,
        CAR_DOOR_OPEN,
        CAR_DOOR_CLOSE,
        PLAYER_HURT,
        MELEE_SWOOSH,
        FOOTSTEP,
        TELEPORT,
        MISSION_START,
        OBJECTIVE_COMPLETE,
        WATER_SPLASH,
        BLOOD_SQUISH
    }

    data class ReverbProfile(
        val numEchoes: Int,
        val delayStep: Float, // Time in seconds between each echo
        val volumeFalloff: Float // How much quieter each echo gets (e.g., 0.5 = 50% quieter)
    )

    companion object {
        val DEFAULT_REVERB = ReverbProfile(numEchoes = 3, delayStep = 0.05f, volumeFalloff = 0.6f)
    }

    private data class ActiveSound(
        val sound: Sound,
        val id: Long,
        val soundId: String,
        val position: Vector3,
        var volumeMultiplier: Float = 1.0f
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

    fun setLoopingSoundVolumeMultiplier(instanceId: Long, multiplier: Float) {
        val activeSound = activeLoopingSounds.find { it.id == instanceId }
        activeSound?.volumeMultiplier = multiplier.coerceIn(0f, 2f) // Clamp to prevent silence or ear-splitting sound
    }

    fun update(listenerPos: Vector3) {
        this.listenerPosition = listenerPos
        activeLoopingSounds.forEach { activeSound ->
            // Calculate the base volume from distance and settings
            val (baseVolume, pan) = calculateVolumeAndPan(activeSound.position)
            // Apply the specific sound's multiplier
            val finalVolume = baseVolume * activeSound.volumeMultiplier
            activeSound.sound.setPan(activeSound.id, pan, finalVolume)
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
        reverbProfile: ReverbProfile? = null,
        pitch: Float = 1.0f,
        volumeMultiplier: Float = 1.0f
    ): Long? {
        return playSoundById(effect.name, position, loop, maxRange, reverbProfile, pitch, volumeMultiplier) // MODIFIED
    }

    /** Main function to play any loaded sound by its string ID. Returns the sound instance ID or null. */
    fun playSound(
        id: String,
        position: Vector3,
        loop: Boolean = false,
        maxRange: Float = 60f,
        reverbProfile: ReverbProfile? = null,
        pitch: Float = 1.0f,
        volumeMultiplier: Float = 1.0f
    ): Long? {
        return playSoundById(id, position, loop, maxRange, reverbProfile, pitch, volumeMultiplier)
    }

    private fun playSoundById(
        id: String,
        position: Vector3,
        loop: Boolean,
        maxRange: Float,
        reverbProfile: ReverbProfile?, // MODIFIED
        pitch: Float,                  // NEW
        volumeMultiplier: Float
    ): Long? {
        val sound = loadedSounds[id]
        if (sound == null) {
            println("SoundManager WARN: Sound with ID '$id' not loaded.")
            return null
        }

        val (baseVolume, pan) = calculateVolumeAndPan(position, maxRange)
        val finalVolume = baseVolume * volumeMultiplier

        // Add a tiny bit of random pitch variation to make sounds less repetitive
        val finalPitch = pitch * (1.0f + (Random.nextFloat() * 0.04f - 0.02f))

        if (loop) {
            if (activeLoopingSounds.any { it.soundId == id && it.position.epsilonEquals(position, 0.1f) }) {
                return null
            }
            val soundInstanceId = sound.loop(finalVolume, finalPitch, pan)
            activeLoopingSounds.add(ActiveSound(sound, soundInstanceId, id, position))
            return soundInstanceId
        } else {
            if (finalVolume > 0.01f) {
                val soundInstanceId = sound.play(finalVolume, finalPitch, pan)
                // If a reverb profile was provided, play the echoes
                if (reverbProfile != null) {
                    playEchoes(sound, finalVolume, pan, finalPitch, reverbProfile)
                }
                return soundInstanceId
            }
        }
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

    private fun playEchoes(sound: Sound, initialVolume: Float, initialPan: Float, basePitch: Float, profile: ReverbProfile) {
        var currentVolume = initialVolume
        for (i in 1..profile.numEchoes) {
            // Each echo is quieter than the last
            currentVolume *= profile.volumeFalloff
            val echoVolume = currentVolume

            Timer.schedule(object : Timer.Task() {
                override fun run() {
                    val echoPan = initialPan * -0.5f // Echoes are slightly panned to the opposite side
                    val echoPitch = basePitch * (1.0f - (i * 0.02f)) // Echoes get slightly deeper
                    sound.play(echoVolume, echoPitch, echoPan)
                }
            }, profile.delayStep * i) // Delay increases for each echo
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
