package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array

class LightingManager {
    private lateinit var environment: Environment
    private lateinit var dayNightCycle: DayNightCycle
    private val directionalLight: DirectionalLight = DirectionalLight()

    // Sky System
    private lateinit var skySystem: SkySystem

    // Light management
    private val lightSources = mutableMapOf<Int, LightSource>()
    private val lightInstances = mutableMapOf<Int, Pair<ModelInstance, ModelInstance>>() // invisible, debug
    private val activeLights = Array<PointLight>()
    private val maxLights = 128
    private var lightUpdateCounter = 0
    private val lightUpdateInterval = 15 // Update every 15 frames

    private lateinit var sunModel: ModelInstance
    private val modelBuilder = ModelBuilder()
    var isSunVisible: Boolean = false
    private val sunDistance = 2500f // How far away the sun sphere is placed
    private var isGrayscaleMode = false
    private var timeOverrideProgress: Float? = null
    private var isBuildModeBright = false

    var isLightAreaVisible = false
        private set
    private var lightAreaModel: Model? = null
    private var lightAreaInstance: ModelInstance? = null

    fun setGrayscaleMode(enabled: Boolean) {
        isGrayscaleMode = enabled
    }

    fun initialize() {
        environment = Environment()
        println("LightingManager.initialize: Created environment, hash: ${environment.hashCode()}")

        dayNightCycle = DayNightCycle()

        // Initialize sky system
        skySystem = SkySystem()
        skySystem.initialize()

        createSunModel()

        // Set initial lighting based on the starting time
        updateLighting()

        // Create model for the light area visual
        val areaMaterial = Material(
            ColorAttribute.createDiffuse(1f, 1f, 0.2f, 0.25f), // Yellow, 25% transparent
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )
        // Create a sphere with a diameter of 1 unit
        lightAreaModel = modelBuilder.createSphere(1f, 1f, 1f, 24, 24, areaMaterial,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())
        lightAreaInstance = ModelInstance(lightAreaModel!!)
    }

    fun toggleLightAreaVisibility() {
        isLightAreaVisible = !isLightAreaVisible
    }

    private fun createSunModel() {
        val sunMaterial = Material(
            ColorAttribute.createEmissive(Color.WHITE)
        )
        val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        val model = modelBuilder.createSphere(150f, 150f, 150f, 20, 20, sunMaterial, attributes)
        sunModel = ModelInstance(model)
    }

    fun toggleBuildModeBrightness(): Boolean {
        isBuildModeBright = !isBuildModeBright
        println("Build Mode Brightness toggled to: $isBuildModeBright")
        return isBuildModeBright
    }

    fun update(deltaTime: Float, cameraPosition: Vector3, timeMultiplier: Float = 1.0f) {
        // Update day/night cycle with the multiplier
        dayNightCycle.update(deltaTime, timeMultiplier)
        skySystem.update(dayNightCycle, cameraPosition)

        // NEW: Update flickering lights logic
        updateFlickeringLights(deltaTime)

        updateLighting()

        lightUpdateCounter++
        if (lightUpdateCounter >= lightUpdateInterval) {
            updateActiveLights(cameraPosition)
            lightUpdateCounter = 0
        }
    }

    fun overrideTime(progress: Float) {
        timeOverrideProgress = progress
    }

    fun clearTimeOverride() {
        timeOverrideProgress = null
    }

    fun renderSky(modelBatch: ModelBatch, camera: Camera) {
        // Render sky first (before everything else)
        skySystem.render(modelBatch, camera, environment)
    }

    fun renderSun(modelBatch: ModelBatch, camera: Camera) {
        // Don't render if it's disabled or night time
        if (!isSunVisible || dayNightCycle.getSunIntensity() <= 0f) {
            return
        }

        // Get the sun's current properties
        val sunDirection = dayNightCycle.getSunDirection()
        val (r, g, b) = dayNightCycle.getSunColor()

        // Update the sun model's material color to match the light color
        sunModel.materials.first().set(ColorAttribute.createEmissive(r, g, b, 1f))

        // Position the sun sphere very far from the camera in the opposite direction of the light
        val sunPosition = Vector3(sunDirection).scl(-sunDistance).add(camera.position)
        sunModel.transform.setToTranslation(sunPosition)

        // Render it
        modelBatch.render(sunModel)
    }

    private fun updateLighting() {
        // 1. Update Ambient Light
        val visualProgress = timeOverrideProgress ?: dayNightCycle.getDayProgress()

        val tempCycleForVisuals = DayNightCycle()
        tempCycleForVisuals.setDayProgress(visualProgress)

        // Determine the final ambient light intensity
        val ambientIntensity = if (isBuildModeBright) {
            0.8f // A fixed bright value for building, regardless of time
        } else {
            tempCycleForVisuals.getAmbientIntensity() // Use the normal calculated intensity
        }

        // Remove the old directional light before adding the new one
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, ambientIntensity, ambientIntensity, ambientIntensity, 1f))
        environment.remove(directionalLight)

        // 2. Update Directional Light (The Sun)
        val sunIntensity = tempCycleForVisuals.getSunIntensity()

        // The sun only shines if its intensity is greater than 0
        if (sunIntensity > 0f) {
            val (r, g, b) = tempCycleForVisuals.getSunColor()
            val sunDirection = tempCycleForVisuals.getSunDirection()

            directionalLight.set(
                r * sunIntensity,
                g * sunIntensity,
                b * sunIntensity,
                sunDirection
            )
            environment.add(directionalLight)
        }
    }

    private fun updateActiveLights(cameraPosition: Vector3) {
        // Sort all available point lights by distance to the camera
        val lightsWithDistance = lightSources.values
            .filter { it.isEnabled && it.pointLight != null }
            .map { it to it.position.dst(cameraPosition) }
            .sortedBy { it.second }

        // Remove all old point lights from the environment and our active list
        activeLights.forEach { environment.remove(it) }
        activeLights.clear()

        // Add the closest point lights up to the maximum allowed
        for ((lightSource, distance) in lightsWithDistance) {
            if (activeLights.size >= maxLights) break

            // Optional: A performance check to not add lights that are very far away
            if (distance > lightSource.range * 3.5) continue

            lightSource.pointLight?.let {
                activeLights.add(it)
                environment.add(it) // Re-add the active point light to the environment
            }
        }
    }

    fun addLightSource(lightSource: LightSource, instances: Pair<ModelInstance, ModelInstance>): Boolean {
        // Store the light source and its instances
        lightSources[lightSource.id] = lightSource
        lightInstances[lightSource.id] = instances

        // Create and add the actual point light for rendering
        val pointLight = lightSource.createPointLight()
        environment.add(pointLight)
        activeLights.add(pointLight)

        println("Light source #${lightSource.id} added (Total lights: ${activeLights.size})")
        return true
    }

    fun removeLightSource(lightId: Int): Boolean {
        val lightSource = lightSources.remove(lightId)
        if (lightSource != null) {
            // Remove from environment
            lightSource.pointLight?.let { pointLight ->
                environment.remove(pointLight)
                activeLights.removeValue(pointLight, true)
            }

            // Remove instances
            lightInstances.remove(lightId)

            println("Light source #$lightId removed (Remaining lights: ${activeLights.size})")
            return true
        }
        return false
    }

    fun getLightSourceAt(position: Vector3, radius: Float): LightSource? {
        return lightSources.values.find { lightSource ->
            lightSource.position.dst(position) <= radius
        }
    }

    fun moveLightSource(lightId: Int, deltaX: Float, deltaY: Float, deltaZ: Float): Boolean {
        val lightSource = lightSources[lightId]
        if (lightSource != null) {
            lightSource.position.add(deltaX, deltaY, deltaZ)
            lightInstances[lightId]?.let { (invisible, debug) ->
                invisible.transform.setTranslation(lightSource.position)
                debug.transform.setTranslation(lightSource.position)
            }
            lightSource.updatePointLight()
            return true
        }
        return false
    }

    fun renderLightInstances(modelBatch: ModelBatch, environment: Environment, debugMode: Boolean) {
        if (!debugMode) {
            return
        }

        for ((lightId, instances) in lightInstances) {
            val (invisibleInstance, debugInstance) = instances
            modelBatch.render(debugInstance, environment)
        }
    }

    fun renderLightAreas(modelBatch: ModelBatch) {
        if (!isLightAreaVisible || lightAreaInstance == null) {
            return
        }

        // Enable blending for transparency
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDepthMask(false)

        for (lightSource in lightSources.values) {
            if (lightSource.isEnabled) {
                val scale = lightSource.range * 2f // Scale diameter to match the light's range
                lightAreaInstance!!.transform.setToTranslation(lightSource.position)
                lightAreaInstance!!.transform.scale(scale, scale, scale)
                modelBatch.render(lightAreaInstance!!)
            }
        }

        // Restore the default OpenGL state
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun updateFlickeringLights(deltaTime: Float) {
        if (lightSources.isEmpty()) return

        for (lightSource in lightSources.values) {
            // Skip lights that are already marked for removal or not set to flicker
            if (lightSource.isMarkedForRemoval || lightSource.flickerMode == FlickerMode.NONE) {
                continue
            }

            when (lightSource.flickerMode) {
                FlickerMode.LOOP -> {
                    lightSource.flickerTimer += deltaTime
                    val isOn = lightSource.intensity > 0
                    if (isOn) {
                        if (lightSource.flickerTimer >= lightSource.loopOnDuration) {
                            lightSource.intensity = 0f
                            lightSource.flickerTimer = 0f
                            lightSource.updatePointLight()
                        }
                    } else { // Is off
                        if (lightSource.flickerTimer >= lightSource.loopOffDuration) {
                            lightSource.intensity = lightSource.baseIntensity
                            lightSource.flickerTimer = 0f
                            lightSource.updatePointLight()
                        }
                    }
                }
                FlickerMode.TIMED_FLICKER_OFF -> {
                    lightSource.lifetime += deltaTime
                    if (lightSource.lifetime >= lightSource.timedFlickerLifetime) {
                        // Time's up, mark for removal
                        if (!lightSource.isMarkedForRemoval) {
                            lightSource.intensity = 0f
                            lightSource.isEnabled = false
                            lightSource.updatePointLight()
                            lightSource.isMarkedForRemoval = true
                        }
                    } else {
                        // Still flickering during its lifetime
                        lightSource.flickerTimer += deltaTime
                        val isOn = lightSource.intensity > 0
                        if (isOn) {
                            if (lightSource.flickerTimer >= lightSource.loopOnDuration) {
                                lightSource.intensity = 0f
                                lightSource.flickerTimer = 0f
                                lightSource.updatePointLight()
                            }
                        } else { // Is off
                            if (lightSource.flickerTimer >= lightSource.loopOffDuration) {
                                lightSource.intensity = lightSource.baseIntensity
                                lightSource.flickerTimer = 0f
                                lightSource.updatePointLight()
                            }
                        }
                    }
                }
                FlickerMode.NONE -> {} // Should not happen, but here for completeness
            }
        }
    }

    fun collectAndClearExpiredLights(): List<Int> {
        val expiredIds = lightSources.values
            .filter { it.isMarkedForRemoval }
            .map { it.id }

        if (expiredIds.isNotEmpty()) {
            println("Cleaning up ${expiredIds.size} expired light source(s).")
            expiredIds.forEach { id ->
                removeLightSource(id)
            }
        }
        return expiredIds
    }

    fun updateSkyPaletteColor(timeOfDay: DayNightCycle.TimeOfDay, colorType: SkySystem.SkyColorType, newColor: Color) {
        skySystem.updatePaletteColor(timeOfDay, colorType, newColor)
    }

    fun getSkyPalettes(): Map<DayNightCycle.TimeOfDay, SkySystem.SkyColors> {
        return skySystem.getPalettes()
    }

    fun getEnvironment(): Environment = environment

    fun getLightSources(): Map<Int, LightSource> = lightSources.toMap()

    fun getActiveLightsCount(): Int = activeLights.size

    fun getMaxLights(): Int = maxLights

    // Get current sky color for other systems that might need it
    fun getCurrentSkyColor(): Color {
        // Always return the true, full-color value from the sky system.
        return skySystem.getCurrentSkyColor()
    }

    // Get day/night cycle for other systems
    fun getDayNightCycle(): DayNightCycle = dayNightCycle

    fun disablePointLights() {
        activeLights.forEach { environment.remove(it) }
    }

    /**
     * Restores all active point lights to the environment.
     */
    fun enablePointLights() {
        activeLights.forEach { environment.add(it) }
    }

    fun dispose() {
        // Clean up resources if needed
        sunModel.model?.dispose()
        skySystem.dispose()
        lightSources.clear()
        lightInstances.clear()
        activeLights.clear()
    }
}
