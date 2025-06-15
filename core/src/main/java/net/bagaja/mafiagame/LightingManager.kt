package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array

class LightingManager {
    private lateinit var environment: Environment
    private lateinit var dayNightCycle: DayNightCycle
    private var directionalLight: DirectionalLight? = null

    // Light management
    private val lightSources = mutableMapOf<Int, LightSource>()
    private val lightInstances = mutableMapOf<Int, Pair<ModelInstance, ModelInstance>>() // invisible, debug
    private val activeLights = Array<PointLight>()
    private val maxLights = 128
    private var lightUpdateCounter = 0
    private val lightUpdateInterval = 15 // Update every 15 frames

    fun initialize() {
        environment = Environment()
        println("LightingManager.initialize: Created environment, hash: ${environment.hashCode()}")

        dayNightCycle = DayNightCycle()

        // Create directional light but don't add it yet
        directionalLight = DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f)

        // Set initial lighting
        updateLighting()
    }

    fun update(deltaTime: Float, cameraPosition: Vector3) {
        // Update day/night cycle
        dayNightCycle.update(deltaTime)

        // Update lighting periodically for performance
        lightUpdateCounter++
        if (lightUpdateCounter >= lightUpdateInterval) {
            updateActiveLights(cameraPosition)
            updateLighting()
            lightUpdateCounter = 0
        }
    }

    private fun updateLighting() {
        // Clear existing lights
        environment.clear()

        // Update ambient light based on time of day
        val ambientIntensity = dayNightCycle.getAmbientIntensity()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight,
            ambientIntensity, ambientIntensity, ambientIntensity, 1f))

        // Add directional light only if sun is up
        val sunIntensity = dayNightCycle.getSunIntensity()
        if (sunIntensity > 0f) {
            val (r, g, b) = dayNightCycle.getSunColor()
            directionalLight?.set(
                r * sunIntensity,
                g * sunIntensity,
                b * sunIntensity,
                -1f, -0.8f, -0.2f
            )
            environment.add(directionalLight)
        }

        // Re-add all point lights
        for (pointLight in activeLights) {
            environment.add(pointLight)
        }
    }

    private fun updateActiveLights(cameraPosition: Vector3) {
        // Clear current active lights
        activeLights.clear()
        environment.clear() // This removes all lights from environment

        // Re-add ambient and directional light
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.1f, 0.1f, 0.1f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))

        // Create list of lights with their distances
        val lightsWithDistance = mutableListOf<Pair<LightSource, Float>>()

        for (lightSource in lightSources.values) {
            if (lightSource.isEnabled && lightSource.pointLight != null) {
                val distance = lightSource.position.dst(cameraPosition)
                lightsWithDistance.add(Pair(lightSource, distance))
            }
        }

        // Sort by distance (closest first)
        lightsWithDistance.sortBy { it.second }

        // Add the closest lights up to our maximum
        var addedLights = 0
        for ((lightSource, distance) in lightsWithDistance) {
            if (addedLights >= maxLights) break

            // Optional: Skip lights that are too far away
            if (distance > lightSource.range * 2f) continue

            lightSource.pointLight?.let { pointLight ->
                environment.add(pointLight)
                activeLights.add(pointLight)
                addedLights++
            }
        }

        println("Active lights updated: $addedLights/${lightSources.size} lights active")
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

    fun getEnvironment(): Environment = environment

    fun getLightSources(): Map<Int, LightSource> = lightSources.toMap()

    fun getActiveLightsCount(): Int = activeLights.size

    fun getMaxLights(): Int = maxLights

    fun dispose() {
        // Clean up resources if needed
        lightSources.clear()
        lightInstances.clear()
        activeLights.clear()
    }
}
