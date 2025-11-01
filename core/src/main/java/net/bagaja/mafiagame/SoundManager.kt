package net.bagaja.mafiagame

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Timer

/**
 * Manages all game sound effects, including 3D positioning, volume attenuation,
 * looping, and a simulated reverb/echo effect.
 */
class SoundManager : Disposable {

    enum class SoundEffect {
        GUNSHOT_REVOLVER,
        FIRE_LOOP
    }

    private data class ActiveSound(
        val sound: Sound,
        val id: Long,
        val effect: SoundEffect,
        val position: Vector3
    )

    private val sounds = mutableMapOf<SoundEffect, Sound>()
    private val activeLoopingSounds = Array<ActiveSound>()
    private lateinit var listenerPosition: Vector3

    fun initialize() {
        println("Initializing SoundManager and generating procedural SFX...")
        sounds[SoundEffect.GUNSHOT_REVOLVER] = ProceduralSFXGenerator.generate(SoundEffect.GUNSHOT_REVOLVER)
        sounds[SoundEffect.FIRE_LOOP] = ProceduralSFXGenerator.generate(SoundEffect.FIRE_LOOP)
        println("Procedural SFX generation complete.")
    }

    fun update(listenerPos: Vector3) {
        this.listenerPosition = listenerPos
        activeLoopingSounds.forEach { activeSound ->
            val (volume, pan) = calculateVolumeAndPan(activeSound.position)
            activeSound.sound.setPan(activeSound.id, pan, volume)
        }
    }

    fun playSound(
        effect: SoundEffect,
        position: Vector3,
        loop: Boolean = false,
        maxRange: Float = 60f,
        reverb: Boolean = false
    ) {
        val sound = sounds[effect] ?: return
        val (volume, pan) = calculateVolumeAndPan(position, maxRange)

        if (volume > 0.01f) {
            if (loop) {
                val id = sound.loop(volume, 1.0f, pan)
                activeLoopingSounds.add(ActiveSound(sound, id, effect, position))
            } else {
                sound.play(volume, 1.0f, pan)
                if (reverb) {
                    playEchoes(sound, volume, pan)
                }
            }
        }
    }

    fun stopLoopingSound(effect: SoundEffect, position: Vector3) {
        val soundToStop = activeLoopingSounds.find { it.effect == effect && it.position == position }
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
        return volume to pan
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
        println("Disposing SoundManager and all procedural sounds.")
        sounds.values.forEach { it.dispose() }
        sounds.clear()
        activeLoopingSounds.clear()
        // MODIFIED: This now calls our new cleanup method.
        ProceduralSFXGenerator.dispose()
    }
}
