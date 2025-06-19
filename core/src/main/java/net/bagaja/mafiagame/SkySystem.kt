package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3

class SkySystem {
    private lateinit var skyboxModel: Model
    private lateinit var skyboxInstance: ModelInstance
    private val modelBuilder = ModelBuilder()

    // Sky colors for different times of day
    private data class SkyColors(
        val topColor: Color,
        val horizonColor: Color,
        val bottomColor: Color
    )

    // Predefined sky color palettes
    private val dayColors = SkyColors(
        topColor = Color(0.4f, 0.7f, 1.0f, 1.0f),      // Light blue
        horizonColor = Color(0.7f, 0.9f, 1.0f, 1.0f),   // Very light blue
        bottomColor = Color(0.8f, 0.95f, 1.0f, 1.0f)    // Almost white blue
    )

    private val sunriseColors = SkyColors(
        topColor = Color(0.3f, 0.4f, 0.7f, 1.0f),       // Deep blue
        horizonColor = Color(1.0f, 0.6f, 0.3f, 1.0f),   // Orange
        bottomColor = Color(1.0f, 0.8f, 0.4f, 1.0f)     // Light orange
    )

    private val sunsetColors = SkyColors(
        topColor = Color(0.2f, 0.3f, 0.6f, 1.0f),       // Darker blue
        horizonColor = Color(1.0f, 0.4f, 0.2f, 1.0f),   // Red-orange
        bottomColor = Color(1.0f, 0.7f, 0.3f, 1.0f)     // Orange
    )

    private val nightColors = SkyColors(
        topColor = Color(0.02f, 0.02f, 0.08f, 1.0f),    // Very dark blue
        horizonColor = Color(0.05f, 0.05f, 0.15f, 1.0f), // Dark purple-blue
        bottomColor = Color(0.08f, 0.08f, 0.2f, 1.0f)   // Slightly lighter dark blue
    )

    private val currentSkyColors = SkyColors(
        topColor = Color(),
        horizonColor = Color(),
        bottomColor = Color()
    )

    fun initialize() {
        createSkybox()
        println("SkySystem initialized")
    }

    private fun createSkybox() {
        // Create a large sphere that will act as our skybox
        val skyboxRadius = 5000f
        val material = Material()

        // Use vertex colors for the gradient effect
        val attributes = (VertexAttributes.Usage.Position or
            VertexAttributes.Usage.ColorUnpacked).toLong()

        skyboxModel = modelBuilder.createSphere(
            skyboxRadius, skyboxRadius, skyboxRadius,
            32, 32, // Higher detail for smoother gradients
            material,
            attributes
        )

        skyboxInstance = ModelInstance(skyboxModel)

        // Apply initial colors
        updateSkyColors(dayColors)
    }

    fun update(dayNightCycle: DayNightCycle, cameraPosition: Vector3) {
        // Update skybox position to follow camera
        skyboxInstance.transform.setToTranslation(cameraPosition)

        // Calculate current sky colors based on time of day
        val timeOfDay = dayNightCycle.getCurrentTimeOfDay()
        val sunIntensity = dayNightCycle.getSunIntensity()

        when (timeOfDay) {
            DayNightCycle.TimeOfDay.DAY -> {
                updateSkyColors(dayColors)
            }
            DayNightCycle.TimeOfDay.SUNRISE -> {
                // Interpolate between night and sunrise colors
                val progress = sunIntensity // 0 to 1 during sunrise
                interpolateColors(nightColors, sunriseColors, progress)
            }
            DayNightCycle.TimeOfDay.SUNSET -> {
                // Interpolate between day and sunset colors
                val progress = 1.0f - sunIntensity // 0 to 1 during sunset
                interpolateColors(dayColors, sunsetColors, progress)
            }
            DayNightCycle.TimeOfDay.NIGHT -> {
                updateSkyColors(nightColors)
            }
        }
    }

    private fun interpolateColors(from: SkyColors, to: SkyColors, progress: Float) {
        currentSkyColors.topColor.set(from.topColor).lerp(to.topColor, progress)
        currentSkyColors.horizonColor.set(from.horizonColor).lerp(to.horizonColor, progress)
        currentSkyColors.bottomColor.set(from.bottomColor).lerp(to.bottomColor, progress)

        applySkyColors()
    }

    private fun updateSkyColors(colors: SkyColors) {
        currentSkyColors.topColor.set(colors.topColor)
        currentSkyColors.horizonColor.set(colors.horizonColor)
        currentSkyColors.bottomColor.set(colors.bottomColor)

        applySkyColors()
    }

    private fun applySkyColors() {
        // Update the material with gradient colors
        // This is a simplified approach - for better gradients, you'd want to modify vertex colors directly
        val material = skyboxInstance.materials.first()

        // Use the horizon color as the main color
        material.set(ColorAttribute.createDiffuse(currentSkyColors.horizonColor))

        // You could also set emissive to make it self-lit
        material.set(ColorAttribute.createEmissive(
            currentSkyColors.horizonColor.r * 0.3f,
            currentSkyColors.horizonColor.g * 0.3f,
            currentSkyColors.horizonColor.b * 0.3f,
            1.0f
        ))
    }

    fun render(modelBatch: ModelBatch, camera: Camera, environment: Environment) {
        // Disable depth writing for skybox so it renders behind everything
        Gdx.gl.glDepthMask(false)

        // Render the skybox
        modelBatch.render(skyboxInstance, environment)

        // Re-enable depth writing
        Gdx.gl.glDepthMask(true)
    }

    fun getCurrentSkyColor(): Color {
        return currentSkyColors.horizonColor
    }

    fun dispose() {
        skyboxModel.dispose()
    }
}
