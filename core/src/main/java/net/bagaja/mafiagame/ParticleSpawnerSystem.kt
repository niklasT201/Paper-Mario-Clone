package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import java.util.*
import kotlin.random.Random

/**
 * Data class to hold the spawner's instance data.
 */
data class GameParticleSpawner(
    val id: String = UUID.randomUUID().toString(),
    var position: Vector3,
    var particleEffectType: ParticleEffectType = ParticleEffectType.SMOKE_FRAME_1,
    var minParticles: Int = 1,
    var maxParticles: Int = 3,
    var spawnInterval: Float = 1.0f, // Time in seconds between spawns
    var timer: Float = spawnInterval, // Start the timer so it spawns after the first interval
    val gameObject: GameObject
)

/**
 * Manages the update loop for all particle spawners in the scene.
 */
class ParticleSpawnerSystem {

    /**
     * Iterates through all spawners and triggers particle emissions based on their timers.
     */
    fun update(deltaTime: Float, particleSystem: ParticleSystem, spawners: Array<GameParticleSpawner>) {
        if (spawners.isEmpty) return

        for (spawner in spawners) {
            spawner.timer -= deltaTime
            if (spawner.timer <= 0f) {
                // Time to spawn!
                val particleCount = if (spawner.minParticles >= spawner.maxParticles) {
                    spawner.minParticles
                } else {
                    // Make sure maxParticles is inclusive
                    Random.nextInt(spawner.minParticles, spawner.maxParticles + 1)
                }

                for (i in 0 until particleCount) {
                    // For rising smoke, chimney smoke etc., the base direction should be UP.
                    // For other effects, a null direction lets the particle system handle randomization.
                    val baseDirection = when (spawner.particleEffectType) {
                        ParticleEffectType.SMOKE_FRAME_1,
                        ParticleEffectType.SMOKE_FRAME_2,
                        ParticleEffectType.FACTORY_SMOKE_INITIAL,
                        ParticleEffectType.FACTORY_SMOKE_THICK,
                        ParticleEffectType.FIRE_FLAME -> Vector3.Y.cpy()
                        else -> null
                    }
                    particleSystem.spawnEffect(
                        type = spawner.particleEffectType,
                        position = spawner.position,
                        baseDirection = baseDirection
                    )
                }

                // Reset timer for the next spawn. Use '+=' to handle cases where the frame takes longer than the interval.
                spawner.timer += spawner.spawnInterval
            }
        }
    }
}
