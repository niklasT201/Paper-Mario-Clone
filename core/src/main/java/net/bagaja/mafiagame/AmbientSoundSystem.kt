package net.bagaja.mafiagame

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Timer
import kotlin.random.Random

// Defines the context for a sound, crucial for the indoor/outdoor logic.
private enum class AmbientSoundCategory {
    WEATHER, // Will only play outdoors
    CITY,    // Can play anywhere
    TRAFFIC  // Can play anywhere
}

// A data class to hold all the rules for a specific ambient sound type.
private data class AmbientSoundDefinition(
    val id: String,
    val soundFileIds: List<String>,
    val category: AmbientSoundCategory,
    val minTimeBetweenPlays: Float,
    val maxTimeBetweenPlays: Float,
    val minSpawnRadius: Float,
    val maxSpawnRadius: Float,
    val isLoop: Boolean = false,
    val loopDuration: Float? = null,
    val fadeOutDuration: Float? = null,
    val minPitch: Float = 1.0f, // NEW: For randomizing pitch/speed
    val maxPitch: Float = 1.0f  // NEW: For randomizing pitch/speed
)

// An internal class to track the timer for each sound type.
private data class AmbientSoundInstance(
    val definition: AmbientSoundDefinition,
    var timer: Float = 0f
)

class AmbientSoundSystem : Disposable {
    private lateinit var soundManager: SoundManager
    private lateinit var playerSystem: PlayerSystem
    private lateinit var sceneManager: SceneManager
    private lateinit var lightingManager: LightingManager // NEW: To know the time of day
    private lateinit var weatherSystem: WeatherSystem     // NEW: To know if it's raining

    private val soundsToManage = mutableListOf<AmbientSoundInstance>()
    private val activeLoopingSounds = mutableMapOf<String, Long>()

    // Wind State Logic
    private var currentWindIntensity = 0f
    private var targetWindIntensity = 0f
    private var windStateChangeTimer = 0f
    private val timeBetweenWindChanges = 20f

    // This is our "database" of all ambient sounds with new pitch settings.
    private val soundDefinitions = listOf(
        // --- CITY AMBIENCE ---
        AmbientSoundDefinition("AMBIENCE_CITY", (1..6).map { "CITY_AMBIENCE_V$it" }, AmbientSoundCategory.CITY, 12f, 28f, 30f, 60f, fadeOutDuration = 2.5f, minPitch = 0.95f, maxPitch = 1.05f),
        AmbientSoundDefinition(
            id = "AMBIENCE_FOOTSTEPS",
            soundFileIds = listOf("FOOTSTEP_LOOP"), // Still uses your one dedicated sound file
            category = AmbientSoundCategory.CITY,
            minTimeBetweenPlays = 0.6f,  // A footstep will play every 0.6 to 0.8 seconds
            maxTimeBetweenPlays = 0.8f,
            minSpawnRadius = 15f,
            maxSpawnRadius = 30f,
            isLoop = false,              // IMPORTANT: This is no longer a loop
            minPitch = 0.85f,            // Pitch will vary between 85% (deeper) and 105% (higher)
            maxPitch = 1.05f
        ),

        // --- TRAFFIC & VEHICLE SOUNDS ---
        AmbientSoundDefinition("AMBIENCE_TRAFFIC", (1..4).map { "TRAFFIC_AMBIENCE_V$it" }, AmbientSoundCategory.TRAFFIC, 8f, 18f, 15f, 35f, fadeOutDuration = 2.0f, minPitch = 0.9f, maxPitch = 1.1f),
        AmbientSoundDefinition("AMBIENCE_CAR", (1..4).map { "VEHICLE_CAR_V$it" }, AmbientSoundCategory.TRAFFIC, 6f, 15f, 10f, 30f, fadeOutDuration = 1.5f, minPitch = 0.85f, maxPitch = 1.15f),
        AmbientSoundDefinition("AMBIENCE_BIKE", (1..3).map { "VEHICLE_BIKE_V$it" }, AmbientSoundCategory.TRAFFIC, 15f, 35f, 10f, 25f, fadeOutDuration = 1.0f, minPitch = 0.98f, maxPitch = 1.02f)
    )

    fun initialize(soundManager: SoundManager, playerSystem: PlayerSystem, sceneManager: SceneManager, lightingManager: LightingManager, weatherSystem: WeatherSystem) {
        this.soundManager = soundManager
        this.playerSystem = playerSystem
        this.sceneManager = sceneManager
        this.lightingManager = lightingManager // NEW
        this.weatherSystem = weatherSystem     // NEW

        soundDefinitions.forEach { def ->
            soundsToManage.add(AmbientSoundInstance(def).apply { resetTimer(this) })
        }
        windStateChangeTimer = timeBetweenWindChanges
    }

    fun update(deltaTime: Float) {
        val playerPos = playerSystem.getControlledEntityPosition()
        val currentSceneType = sceneManager.currentScene
        val isInWorld = currentSceneType == SceneType.WORLD

        // If the player is NOT in the outside world
        if (!isInWorld) {
            if (activeLoopingSounds.isNotEmpty()) {
                println("Player entered interior. Stopping all ambient sounds.")
                activeLoopingSounds.values.forEach { soundManager.stopLoopingSound(it) }
                activeLoopingSounds.clear()
            }
            return
        }

        // --- Wind State Machine ---
        updateWindStateMachine(deltaTime, playerPos)

        // --- Contextual Frequency Multiplier ---
        val timeOfDay = lightingManager.getDayNightCycle().getCurrentTimeOfDay()
        val rainIntensity = weatherSystem.getRainIntensity()
        var trafficFrequencyMultiplier = 1.0f
        // If it's night, make traffic sounds 80% less frequent
        if (timeOfDay == DayNightCycle.TimeOfDay.NIGHT) {
            trafficFrequencyMultiplier *= 0.2f
        }
        // If it's raining heavily, make traffic sounds 70% less frequent
        if (rainIntensity > 0.7f) {
            trafficFrequencyMultiplier *= 0.3f
        }

        // --- Update Standard Ambient Sounds (City, Traffic) ---
        for (soundInstance in soundsToManage) {
            var effectiveDeltaTime = deltaTime
            // If this is a traffic sound, apply our multiplier
            if (soundInstance.definition.category == AmbientSoundCategory.TRAFFIC) {
                effectiveDeltaTime *= trafficFrequencyMultiplier
            }
            soundInstance.timer -= effectiveDeltaTime

            if (soundInstance.timer <= 0) {
                playSound(soundInstance.definition, playerPos)
                resetTimer(soundInstance)
            }
        }
    }

    private fun updateWindStateMachine(deltaTime: Float, playerPos: Vector3) {
        // NEW: Check for "wind-free" time of day
        val dayProgress = lightingManager.getDayNightCycle().getDayProgress()
        // This defines the calm period (e.g., from 08:24 to 18:00)
        val isCalmPeriod = dayProgress > 0.35f && dayProgress < 0.75f

        if (isCalmPeriod) {
            targetWindIntensity = 0f // Force the wind to die down
        } else {
            // Only change wind target if not in the calm period
            windStateChangeTimer -= deltaTime
            if (windStateChangeTimer <= 0) {
                windStateChangeTimer = timeBetweenWindChanges + Random.nextFloat() * 10f
                targetWindIntensity = when {
                    targetWindIntensity < 0.3f -> Random.nextFloat() * 0.6f
                    targetWindIntensity < 0.7f -> Random.nextFloat() * 0.8f
                    else -> Random.nextFloat() * 1.0f
                }
                println("AmbientSystem: New target wind intensity: %.2f".format(targetWindIntensity))
            }
        }

        currentWindIntensity = Interpolation.fade.apply(currentWindIntensity, targetWindIntensity, 0.5f * deltaTime)

        // The rest of the wind playing logic remains the same...
        if (currentWindIntensity > 0.1f && Random.nextFloat() < (currentWindIntensity * 0.005f)) {
            val soundId: String
            val fadeOut: Float
            when {
                currentWindIntensity > 0.7f -> {
                    soundId = listOf("WIND_HEAVY_V1", "WIND_HEAVY_V2", "WIND_HEAVY_V3", "WIND_HEAVY_V4", "WIND_HEAVY_V5").random()
                    fadeOut = 2.0f
                }
                currentWindIntensity > 0.3f -> {
                    soundId = listOf("WIND_MEDIUM_V1", "WIND_MEDIUM_V2").random()
                    fadeOut = 1.5f
                }
                else -> {
                    soundId = "WIND_LOOP"
                    playSound(AmbientSoundDefinition("", listOf(soundId), AmbientSoundCategory.WEATHER, 0f, 0f, 20f, 40f, isLoop = true, loopDuration = 7f), playerPos)
                    return
                }
            }
            playSound(AmbientSoundDefinition("", listOf(soundId), AmbientSoundCategory.WEATHER, 0f, 0f, 25f, 50f, fadeOutDuration = fadeOut, minPitch = 0.9f, maxPitch = 1.1f), playerPos)
        }
    }

    private fun playSound(definition: AmbientSoundDefinition, playerPos: Vector3) {
        val soundId = definition.soundFileIds.random()

        val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
        val distance = Random.nextFloat() * (definition.maxSpawnRadius - definition.minSpawnRadius) + definition.minSpawnRadius
        val spawnPos = playerPos.cpy().add(kotlin.math.cos(angle) * distance, 0f, kotlin.math.sin(angle) * distance)

        var volumeMultiplier = 1.0f - (Random.nextFloat() * 0.4f)

        if (definition.id == "AMBIENCE_FOOTSTEPS") {
            volumeMultiplier *= 0.05f // Make them only 35% as loud as other ambient sounds
        }

        // Calculate random pitch for this specific sound instance
        val randomPitch = if (definition.minPitch < definition.maxPitch) {
            Random.nextFloat() * (definition.maxPitch - definition.minPitch) + definition.minPitch
        } else {
            1.0f
        }

        if (definition.isLoop) {
            val instanceId = soundManager.playSound(id = soundId, position = spawnPos, loop = true, volumeMultiplier = volumeMultiplier, pitch = randomPitch)
            if (instanceId != null) {
                activeLoopingSounds[definition.id] = instanceId
                Timer.schedule(object : Timer.Task() {
                    override fun run() {
                        soundManager.stopLoopingSound(instanceId)
                        activeLoopingSounds.remove(definition.id)
                    }
                }, definition.loopDuration ?: 5f)
            }
        } else {
            soundManager.playSound(
                id = soundId,
                position = spawnPos,
                fadeOutDuration = definition.fadeOutDuration,
                volumeMultiplier = volumeMultiplier,
                pitch = randomPitch // Pass the random pitch to the sound manager
            )
        }
    }

    private fun resetTimer(instance: AmbientSoundInstance) {
        instance.timer = Random.nextFloat() * (instance.definition.maxTimeBetweenPlays - instance.definition.minTimeBetweenPlays) + instance.definition.minTimeBetweenPlays
    }

    override fun dispose() {
        activeLoopingSounds.values.forEach { soundManager.stopLoopingSound(it) }
        activeLoopingSounds.clear()
    }
}
