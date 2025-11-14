package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Timer
import kotlin.random.Random

data class FadingOneShotSound(
    val sound: Sound,
    val id: Long,
    var timer: Float,
    val duration: Float,
    val initialVolume: Float
)

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
        BLOOD_SQUISH,
        WOOD_SPLINTER,
        LOOT_DROP
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
        val maxRange: Float,
        val falloffMode: EmitterFalloffMode,
        var volumeMultiplier: Float = 1.0f,
        var isFadingOut: Boolean = false,
        var fadeOutTimer: Float = 0f,
        var fadeOutDuration: Float = 1.0f
    )

    private val mutedSoundIds = mutableSetOf<String>()
    private val loadedSounds = mutableMapOf<String, Sound>()
    private val activeLoopingSounds = Array<ActiveSound>()
    private val activeFadingSounds = Array<FadingOneShotSound>()
    private var listenerPosition: Vector3 = Vector3.Zero
    private var masterVolume = 1.0f
    private var sfxVolume = 1.0f

    /**
     * No-op. Sounds are now loaded manually via the `load` methods.
     */
    fun initialize() {
        println("SoundManager initialized. Load sounds using load() methods.")
    }

    fun getAllSoundIds(): List<String> = loadedSounds.keys.toList()

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

    fun setLoopingSoundPitch(instanceId: Long, pitch: Float) {
        val activeSound = activeLoopingSounds.find { it.id == instanceId }
        activeSound?.sound?.setPitch(activeSound.id, pitch.coerceIn(0.5f, 2.0f))
    }

    fun rampUpLoopingSoundVolume(instanceId: Long, duration: Float) {
        val activeSound = activeLoopingSounds.find { it.id == instanceId } ?: return

        // Use a Timer to gradually increase the volume multiplier over the specified duration
        val steps = 20 // Number of steps for a smooth fade
        val stepTime = duration / steps
        var currentStep = 0

        Timer.schedule(object : Timer.Task() {
            override fun run() {
                currentStep++
                val progress = currentStep.toFloat() / steps
                // Interpolate the multiplier from 0 to 1
                activeSound.volumeMultiplier = Interpolation.fade.apply(progress)

                if (currentStep >= steps) {
                    activeSound.volumeMultiplier = 1.0f // Ensure it ends at full volume
                    this.cancel() // Stop the timer
                }
            }
        }, 0f, stepTime, steps)
    }

    fun fadeOutAndStopLoopingSound(instanceId: Long, duration: Float) {
        val soundToFade = activeLoopingSounds.find { it.id == instanceId }
        if (soundToFade != null && !soundToFade.isFadingOut) {
            soundToFade.isFadingOut = true
            soundToFade.fadeOutTimer = duration
            soundToFade.fadeOutDuration = duration
        }
    }

    fun update(listenerPos: Vector3) {
        this.listenerPosition = listenerPos

        val iterator = activeLoopingSounds.iterator()
        // Calculate the base volume from distance and settings
        while (iterator.hasNext()) {
            val activeSound = iterator.next()

            // Handle fade out logic
            if (activeSound.isFadingOut) {
                activeSound.fadeOutTimer -= Gdx.graphics.deltaTime
                if (activeSound.fadeOutTimer <= 0f) {
                    activeSound.sound.stop(activeSound.id)
                    iterator.remove() // Safely remove the sound from the list
                    continue // Skip to the next active sound
                }
            }

            // Calculate base volume and pan
            val (baseVolume, pan) = calculateVolumeAndPan(activeSound.position, activeSound.maxRange, activeSound.falloffMode)

            // Calculate fade progress if the sound is fading out
            val fadeMultiplier = if (activeSound.isFadingOut) {
                (activeSound.fadeOutTimer / activeSound.fadeOutDuration).coerceIn(0f, 1f)
            } else {
                1.0f
            }

            // Apply the specific sound's multiplier
            val finalVolume = baseVolume * activeSound.volumeMultiplier * fadeMultiplier
            activeSound.sound.setPan(activeSound.id, pan, finalVolume)
        }

        val fadingIterator = activeFadingSounds.iterator()
        while (fadingIterator.hasNext()) {
            val fadingSound = fadingIterator.next()
            fadingSound.timer -= Gdx.graphics.deltaTime

            if (fadingSound.timer <= 0f) {
                fadingSound.sound.stop(fadingSound.id)
                fadingIterator.remove()
            } else {
                val fadeProgress = (fadingSound.timer / fadingSound.duration).coerceIn(0f, 1f)
                val currentVolume = fadingSound.initialVolume * Interpolation.fade.apply(fadeProgress)
                fadingSound.sound.setVolume(fadingSound.id, currentVolume)
            }
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
        volumeMultiplier: Float = 1.0f,
        fadeOutDuration: Float? = null,
        falloffMode: EmitterFalloffMode = EmitterFalloffMode.LINEAR
    ): Long? {
        return playSoundById(id, position, loop, maxRange, reverbProfile, pitch, volumeMultiplier, fadeOutDuration, falloffMode)
    }

    private fun playSoundById(
        id: String,
        position: Vector3,
        loop: Boolean,
        maxRange: Float,
        reverbProfile: ReverbProfile?,
        pitch: Float,
        volumeMultiplier: Float,
        fadeOutDuration: Float? = null,
        falloffMode: EmitterFalloffMode = EmitterFalloffMode.LINEAR
    ): Long? {
        if (id in mutedSoundIds) {
            println("SoundManager: Suppressed playback of muted sound '$id'.")
            return null
        }

        val sound = loadedSounds[id]
        if (sound == null) {
            println("SoundManager WARN: Sound with ID '$id' not loaded.")
            return null
        }

        val (baseVolume, pan) = calculateVolumeAndPan(position, maxRange, falloffMode)
        val finalVolume = baseVolume * volumeMultiplier

        // Add a tiny bit of random pitch variation to make sounds less repetitive
        val finalPitch = pitch * (1.0f + (Random.nextFloat() * 0.04f - 0.02f))

        if (loop) {
            if (activeLoopingSounds.any { it.soundId == id && it.position.epsilonEquals(position, 0.1f) }) {
                return null
            }
            val soundInstanceId = sound.loop(finalVolume, finalPitch, pan)
            activeLoopingSounds.add(ActiveSound(sound, soundInstanceId, id, position, maxRange, falloffMode))
            return soundInstanceId
        } else {
            if (finalVolume > 0.01f) {
                val soundInstanceId = sound.play(finalVolume, finalPitch, pan)

                // NEW LOGIC: If a fade-out is requested, track this sound
                if (fadeOutDuration != null && fadeOutDuration > 0f) {
                    activeFadingSounds.add(FadingOneShotSound(sound, soundInstanceId, fadeOutDuration, fadeOutDuration, finalVolume))
                }

                if (reverbProfile != null) {
                    playEchoes(sound, finalVolume, pan, finalPitch, reverbProfile)
                }
                return soundInstanceId
            }
        }
        return null
    }

    fun muteCategory(soundIdsToMute: List<String>) {
        mutedSoundIds.addAll(soundIdsToMute)
        println("Muted ${soundIdsToMute.size} sounds.")
    }

    fun unmuteCategory(soundIdsToUnmute: List<String>) {
        mutedSoundIds.removeAll(soundIdsToUnmute.toSet())
        println("Unmuted ${soundIdsToUnmute.size} sounds.")
    }

    fun stopAllSounds() {
        println("SoundManager: Stopping all active looping sounds.")
        val iterator = activeLoopingSounds.iterator()
        while (iterator.hasNext()) {
            val activeSound = iterator.next()
            activeSound.sound.stop(activeSound.id)
            iterator.remove()
        }
        // We can also stop one-shot sounds that are fading out
        val fadingIterator = activeFadingSounds.iterator()
        while(fadingIterator.hasNext()) {
            val fadingSound = fadingIterator.next()
            fadingSound.sound.stop(fadingSound.id)
            fadingIterator.remove()
        }
    }

    fun muteSound(soundId: String) {
        mutedSoundIds.add(soundId)
        // Also stop any currently playing instance of this sound
        val soundsToStop = activeLoopingSounds.filter { it.soundId == soundId }
        soundsToStop.forEach { stopLoopingSound(it.id) }
    }

    fun unmuteAllSounds() {
        mutedSoundIds.clear()
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

    private fun calculateVolumeAndPan(soundPosition: Vector3, maxRange: Float = 60f, falloffMode: EmitterFalloffMode): Pair<Float, Float> {
        val distance = listenerPosition.dst(soundPosition)

        val distanceVolume = when (falloffMode) {
            EmitterFalloffMode.LINEAR -> {
                // Your original smooth fade logic
                (1.0f - (distance / maxRange)).coerceIn(0f, 1f)
            }
            EmitterFalloffMode.NONE -> {
                // New logic: 100% volume if in range, 0% if out of range
                if (distance < maxRange) 1.0f else 0.0f
            }
        }

        val finalVolume = distanceVolume * masterVolume * sfxVolume
        val pan = (soundPosition.x - listenerPosition.x) / (maxRange * 0.5f).coerceIn(-1.0f, 1.0f)
        return Pair(finalVolume, pan)
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
