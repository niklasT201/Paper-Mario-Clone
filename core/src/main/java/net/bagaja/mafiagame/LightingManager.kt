package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
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
    }

    private fun createSunModel() {
        val sunMaterial = Material(
            ColorAttribute.createEmissive(Color.WHITE)
        )
        val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        val model = modelBuilder.createSphere(150f, 150f, 150f, 20, 20, sunMaterial, attributes)
        sunModel = ModelInstance(model)
    }

    fun update(deltaTime: Float, cameraPosition: Vector3, timeMultiplier: Float = 1.0f) {
        // Update day/night cycle with the multiplier
        dayNightCycle.update(deltaTime, timeMultiplier)

        // Update sky system
        skySystem.update(dayNightCycle, cameraPosition)

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

        // Remove the old directional light before adding the new one
        val ambientIntensity = tempCycleForVisuals.getAmbientIntensity()
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
        for ((lightId, instances) in lightInstances) {
            val (invisibleInstance, debugInstance) = instances
            if (debugMode) {
                modelBatch.render(debugInstance, environment)
            } else {
                modelBatch.render(invisibleInstance, environment)
            }
        }
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
