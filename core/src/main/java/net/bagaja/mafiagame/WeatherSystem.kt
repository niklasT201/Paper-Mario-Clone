package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs
import kotlin.random.Random

// Represents the natural weather state
private enum class WeatherState {
    CLEAR,
    RAINING
}

class WeatherSystem : Disposable {
    private data class Raindrop(
        val instance: ModelInstance,
        val position: Vector3 = Vector3(),
        val velocity: Vector3 = Vector3()
    )
    lateinit var particleSystem: ParticleSystem
    lateinit var sceneManager: SceneManager

    private lateinit var rainModel: Model
    private lateinit var modelBatch: ModelBatch
    private lateinit var camera: Camera
    lateinit var lightingManager: LightingManager

    private val raindrops = Array<Raindrop>()
    private val renderableInstances = Array<ModelInstance>()

    // --- Weather State ---
    private var worldRainIntensity = 0f // The "true" intensity of rain in the world.
    private var currentRainIntensity = 0f // The visually rendered intensity (fades in/out).
    private var targetRainIntensity = 0f  // The intensity the world is transitioning towards.
    private var weatherState = WeatherState.CLEAR

    // --- Random Weather Logic ---
    private var isRandomWeatherEnabled = true
    private var weatherChangeTimer = 0f
    private val timeBetweenWeatherChecks = 60f // Check for a weather change every 60 seconds.
    private val chanceToStartRaining = 0.25f   // 25% chance to start raining when clear.
    private val chanceToStopRaining = 0.50f    // 50% chance to stop raining when it is.

    // --- Mission Control Logic ---
    private var isMissionControlled = false
    private var missionRainDelayTimer = -1f
    private var missionRainDurationTimer = -1f

    // --- Configuration ---
    private val maxRaindrops = 2000
    private val rainAreaSize = Vector3(120f, 60f, 120f)
    private val rainColor = Color(0.7f, 0.8f, 1.0f, 0.5f)

    private var lightningTimer = 0f
    private val timeBetweenLightning = 15f // Average time between strikes

    private var lightRainSoundId: Long? = null
    private var mediumRainSoundId: Long? = null
    private var heavyRainSoundId: Long? = null
    private var stormAmbienceSoundId: Long? = null
    private var currentHeavyRainId: String? = null

    // Store the sound IDs in lists for easy access
    private val heavyRainSoundIds = (1..10).mapNotNull {
        when(it) {
            1 -> "HEAVY_RAIN_V1"
            2 -> "HEAVY_RAIN_V2"
            3 -> "HEAVY_RAIN_V3"
            4 -> "HEAVY_RAIN_V4"
            5 -> null // V5 doesn't exist in your list
            6 -> "HEAVY_RAIN_V6"
            7 -> "HEAVY_RAIN_V7"
            8 -> null // V8 doesn't exist
            9 -> "HEAVY_RAIN_V9"
            10 -> "HEAVY_RAIN_V10"
            else -> null
        }
    }
    private val lightningSoundIds = listOf("LIGHTNING_V1", "LIGHTNING_V2", "LIGHTNING_V3")

    fun initialize(camera: Camera) {
        this.camera = camera
        this.modelBatch = ModelBatch()
        weatherChangeTimer = timeBetweenWeatherChecks // Start the timer

        // Create a single, reusable model for all raindrops (a simple vertical line)
        val modelBuilder = ModelBuilder()
        val material = Material(
            ColorAttribute.createDiffuse(rainColor),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )
        modelBuilder.begin()
        val partBuilder = modelBuilder.part("raindrop", GL20.GL_LINES, (VertexAttributes.Usage.Position).toLong(), material)
        // A short, thin line to represent a rain streak
        partBuilder.line(0f, 0f, 0f, 0f, -0.8f, 0f)
        rainModel = modelBuilder.end()

        // Create a pool of raindrop objects to be reused
        for (i in 0 until maxRaindrops) {
            val drop = Raindrop(ModelInstance(rainModel))
            resetRaindrop(drop, true) // Initialize with a random position
            raindrops.add(drop)
        }
    }

    private fun resetRaindrop(drop: Raindrop, initialSpawn: Boolean = false) {
        val rainCenter = camera.position
        val halfSize = rainAreaSize.cpy().scl(0.5f)

        // Give it a random X and Z position within the rain area
        drop.position.x = rainCenter.x + Random.nextFloat() * rainAreaSize.x - halfSize.x
        drop.position.z = rainCenter.z + Random.nextFloat() * rainAreaSize.z - halfSize.z

        // If it's the first time, scatter them vertically. Otherwise, place at the top.
        if (initialSpawn) {
            drop.position.y = rainCenter.y + Random.nextFloat() * rainAreaSize.y - halfSize.y
        } else {
            drop.position.y = rainCenter.y + halfSize.y
        }

        // Give it a downward velocity with some variance
        val baseSpeed = 40f
        val speedVariance = 15f
        drop.velocity.set(0f, -(baseSpeed + Random.nextFloat() * speedVariance), 0f)
    }

    fun update(deltaTime: Float, isInInterior: Boolean) {
        // --- 1. Handle Master Weather State (Random or Mission) ---
        if (isMissionControlled) {
            handleMissionWeather(deltaTime)
        } else if (isRandomWeatherEnabled) {
            handleRandomWeather(deltaTime)
        }

        // --- 2. Smoothly transition the "world" weather towards its target ---
        val previousWorldIntensity = worldRainIntensity
        worldRainIntensity = Interpolation.fade.apply(worldRainIntensity, targetRainIntensity, 0.1f * deltaTime)

        // Check if the rain has just stopped
        if (previousWorldIntensity > 0.1f && worldRainIntensity <= 0.1f) {
            // Play the "storm declain" sound as the storm ends
            sceneManager.game.soundManager.playSound("STORM_END", camera.position, reverbProfile = SoundManager.DEFAULT_REVERB)
        }

        // --- 3. Smoothly transition the "visual" weather based on location (inside/outside) ---
        val visualTarget = if (isInInterior) 0f else worldRainIntensity
        currentRainIntensity = Interpolation.fade.apply(currentRainIntensity, visualTarget, 2.0f * deltaTime)

        // --- 4. Update the lighting manager with the current LOGICAL intensity for color tinting ---
        lightingManager.setRainFactor(worldRainIntensity) // Lighting should change with the sky, not the drops

        // --- 5. UPDATE LAYERED AUDIO (REVISED) ---
        val interiorMuffle = if (isInInterior && worldRainIntensity > 0.7f) 0.15f else if (isInInterior) 0.0f else 1.0f
        updateRainAudio(worldRainIntensity, interiorMuffle)

        // --- 6. Update particle physics if there is visible rain ---
        if (currentRainIntensity > 0.01f) {

            // SPAWN RAIN SPLASHES
            if (!isInInterior) {
                // Number of splashes depends on intensity, spawn up to 10 per frame in heaviest rain
                val splashesToSpawn = (currentRainIntensity * 10 * Random.nextFloat()).toInt()
                for (i in 0 until splashesToSpawn) {
                    spawnRainSplash()
                }
            }

            val rainCenter = camera.position
            val bottomY = rainCenter.y - rainAreaSize.y / 2f
            val activeDropCount = (maxRaindrops * currentRainIntensity).toInt()

            // Update only the active raindrops
            for (i in 0 until activeDropCount) {
                val drop = raindrops.get(i)

                // Move the drop
                drop.position.mulAdd(drop.velocity, deltaTime)

                // If the drop has fallen below the rain area, reset it
                if (drop.position.y < bottomY) {
                    resetRaindrop(drop)
                }
            }

            // LIGHTNING AND THUNDER
            if (worldRainIntensity > 0.7f && !isInInterior) { // Only have lightning in very heavy rain
                lightningTimer -= deltaTime
                if (lightningTimer <= 0) {
                    // Reset timer with some randomness
                    lightningTimer = timeBetweenLightning + (Random.nextFloat() * 10f - 5f)

                    // Trigger the effects
                    lightingManager.triggerLightningFlash()

                    // Play a random thunder sound
                    lightningSoundIds.randomOrNull()?.let { soundId ->
                        sceneManager.game.soundManager.playSound(soundId, camera.position, reverbProfile = SoundManager.DEFAULT_REVERB, maxRange = 150f)
                    }

                    val shakeIntensity = if (isInInterior) 0.1f else 0.4f
                    val shakeDuration = if (isInInterior) 0.3f else 0.5f
                    lightingManager.game.cameraManager.startShake(shakeDuration, shakeIntensity)
                }
            }
        }
    }

    private fun updateRainAudio(worldIntensity: Float, interiorMuffle: Float) {
        val soundManager = sceneManager.game.soundManager

        // --- Define Intensity Thresholds ---
        val lightMax = 0.4f
        val mediumMax = 0.7f
        val heavyMin = 0.6f

        // --- Calculate Volume for Each Layer ---
        val lightVol = (1.0f - (worldIntensity / lightMax)).coerceIn(0f, 1f)

        // Medium rain: Fades in after light starts fading, peaks at medium, fades out as heavy starts
        val mediumVol = if (worldIntensity > lightMax / 2f && worldIntensity < heavyMin + 0.1f) {
            1.0f - (abs(worldIntensity - 0.55f) / 0.35f)
        } else {
            0f
        }.coerceIn(0f, 1f)

        // Heavy rain: Fades in from heavyMin onwards
        val heavyVol = ((worldIntensity - heavyMin) / (1.0f - heavyMin)).coerceIn(0f, 1f)

        // Storm ambience: Fades in only during the heaviest rain
        val stormVol = ((worldIntensity - 0.7f) / 0.3f).coerceIn(0f, 1f)

        // Light Rain
        if (lightVol > 0.01f && lightRainSoundId == null) {
            lightRainSoundId = soundManager.playSound("RAIN_LIGHT", camera.position, loop = true)
        }
        lightRainSoundId?.let { soundManager.setLoopingSoundVolumeMultiplier(it, lightVol * 1.5f * interiorMuffle) } // Boosted volume

        // Medium Rain
        if (mediumVol > 0.01f && mediumRainSoundId == null) {
            mediumRainSoundId = soundManager.playSound("RAIN_MEDIUM", camera.position, loop = true)
        }
        mediumRainSoundId?.let { soundManager.setLoopingSoundVolumeMultiplier(it, mediumVol * 1.4f * interiorMuffle) } // Boosted volume

        // Heavy Rain (with random variation switching)
        if (heavyVol > 0.01f && heavyRainSoundId == null) {
            currentHeavyRainId = heavyRainSoundIds.random()
            heavyRainSoundId = soundManager.playSound(currentHeavyRainId!!, camera.position, loop = true)
        } else if (heavyVol < 0.01f && heavyRainSoundId != null) {
            soundManager.stopLoopingSound(heavyRainSoundId!!)
            heavyRainSoundId = null
            currentHeavyRainId = null
        }
        heavyRainSoundId?.let { soundManager.setLoopingSoundVolumeMultiplier(it, heavyVol * interiorMuffle) }

        // Storm Ambience
        if (stormVol > 0.01f && stormAmbienceSoundId == null) {
            stormAmbienceSoundId = soundManager.playSound("STORM_AMBIENCE", camera.position, loop = true)
        }
        stormAmbienceSoundId?.let { soundManager.setLoopingSoundVolumeMultiplier(it, stormVol * interiorMuffle) }
    }

    private fun spawnRainSplash() {
        val spawnRadius = 60f
        val playerPos = camera.position // Using camera position is better for visuals that appear around the screen

        val randomX = playerPos.x + (Random.nextFloat() * 2f - 1f) * spawnRadius
        val randomZ = playerPos.z + (Random.nextFloat() * 2f - 1f) * spawnRadius

        // STEP 1: Use the new, more robust function from SceneManager.
        var groundY = sceneManager.findAbsoluteHighestSurfaceY(randomX, randomZ)

        // STEP 2: If the function returned its "not found" value, default to Y=0, just like triggers.
        if (groundY == -Float.MAX_VALUE) {
            groundY = 0f
        }

        // Don't spawn splashes way above or below the camera
        if (abs(groundY - playerPos.y) > 30f) return

        val position = Vector3(randomX, groundY, randomZ)
        particleSystem.spawnEffect(ParticleEffectType.RAIN_SPLASH, position)
    }

    private fun handleRandomWeather(deltaTime: Float) {
        weatherChangeTimer -= deltaTime
        if (weatherChangeTimer <= 0) {
            weatherChangeTimer = timeBetweenWeatherChecks // Reset timer

            if (weatherState == WeatherState.CLEAR) {
                if (Random.nextFloat() < chanceToStartRaining) {
                    println("Weather: It's starting to rain.")
                    weatherState = WeatherState.RAINING
                    targetRainIntensity = Random.nextFloat() * 0.7f + 0.3f // Random heavy rain (0.3 to 1.0)
                }
            } else { // It's raining
                if (Random.nextFloat() < chanceToStopRaining) {
                    println("Weather: The rain is stopping.")
                    weatherState = WeatherState.CLEAR
                    targetRainIntensity = 0f
                }
            }
        }
    }

    private fun handleMissionWeather(deltaTime: Float) {
        // Handle delayed start
        if (missionRainDelayTimer > 0) {
            missionRainDelayTimer -= deltaTime
            if (missionRainDelayTimer <= 0) {
                println("Mission Weather: Delay finished, starting rain.")
                worldRainIntensity = targetRainIntensity // Snap to target intensity after delay
            }
            return // Don't process duration while in delay
        }

        // Handle timed duration
        if (missionRainDurationTimer > 0) {
            missionRainDurationTimer -= deltaTime
            if (missionRainDurationTimer <= 0) {
                println("Mission Weather: Timed rain finished.")
                targetRainIntensity = 0f // Start fading out
                // Once it's faded, the mission override will be cleared.
            }
        }
    }

    fun render(environment: Environment) {
        if (currentRainIntensity < 0.01f) return

        val activeDropCount = (maxRaindrops * currentRainIntensity).toInt()
        if (activeDropCount == 0) return

        renderableInstances.clear()
        for (i in 0 until activeDropCount) {
            val drop = raindrops.get(i)
            // Update the transform of the instance to match the particle's position
            drop.instance.transform.setToTranslation(drop.position)
            renderableInstances.add(drop.instance)
        }

        // --- Render with Transparency ---
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDepthMask(false) // Disable writing to the depth buffer for transparency

        modelBatch.begin(camera)
        modelBatch.render(renderableInstances, environment)
        modelBatch.end()

        // --- Restore GL State ---
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    // --- PUBLIC API for Mission Control ---

    /** Forcibly sets the weather, disabling random weather. Called by missions. */
    fun setMissionWeather(intensity: Float, duration: Float? = null, delay: Float? = null) {
        println("Weather override active. Intensity: $intensity, Duration: $duration, Delay: $delay")
        isMissionControlled = true
        isRandomWeatherEnabled = false // Disable random changes
        targetRainIntensity = intensity.coerceIn(0f, 1f) // Ensure target is valid
        weatherState = if (targetRainIntensity > 0) WeatherState.RAINING else WeatherState.CLEAR

        if (delay != null && delay > 0) {
            missionRainDelayTimer = delay
            // When delaying, start with no rain, then it will fade to the target
            worldRainIntensity = 0f
        } else {
            missionRainDelayTimer = -1f
            // If no delay, SNAP both the current and target intensity for an immediate change
            worldRainIntensity = targetRainIntensity
        }

        missionRainDurationTimer = duration ?: -1f // -1 means infinite duration
    }

    /** Releases mission control and re-enables the random weather system. */
    fun clearMissionOverride() {
        if (!isMissionControlled) return
        println("Mission has released control of the weather.")
        isMissionControlled = false
        isRandomWeatherEnabled = true
        missionRainDelayTimer = -1f
        missionRainDurationTimer = -1f
        weatherState = if (worldRainIntensity > 0) WeatherState.RAINING else WeatherState.CLEAR
    }

    fun getRainIntensity(): Float = worldRainIntensity
    fun getVisualRainIntensity(): Float = currentRainIntensity

    override fun dispose() {
        rainModel.dispose()
        modelBatch.dispose()
    }
}
