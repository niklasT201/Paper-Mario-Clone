package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap // <-- FIX 1: ADDED IMPORT
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import kotlin.math.abs

class ParallaxBackgroundSystem {
    private val modelBuilder = ModelBuilder()
    private val parallaxLayers = Array<ParallaxLayer>()
    private var lastCameraPosition = Vector3()
    private var isInitialized = false

    // Configuration
    private val baseDistance = 50f // Base distance for the first layer
    private val layerDistanceMultiplier = 1.5f // Each layer is further away
    private val defaultImageSpacing = 2f // Minimum space between images in a layer

    data class ParallaxLayer(
        val depth: Float, // Distance from camera (higher = further)
        val speedMultiplier: Float, // Movement speed relative to camera (0.0 to 1.0)
        val images: Array<ParallaxImage> = Array(),
        val height: Float = 20f // Height at which this layer appears
    )

    data class ParallaxImage(
        val modelInstance: ModelInstance,
        val texture: Texture,
        val width: Float,
        val height: Float,
        var basePosition: Vector3, // Original position in the layer
        var currentPosition: Vector3, // Current rendered position
        val layerIndex: Int
    )

    enum class ParallaxImageType(
        val displayName: String,
        val texturePath: String,
        val width: Float,
        val height: Float,
        val preferredLayer: Int = 0 // 0 = background, higher = foreground
    ) {
        // Far background (slowest)
        MOUNTAINS("Mountains", "textures/parallax/mountains.png", 40f, 20f, 0),
        HILLS("Hills", "textures/parallax/hills.png", 35f, 15f, 1),

        // Mid background
        FOREST("Forest", "textures/parallax/forest.png", 30f, 18f, 2),
        CITY_SKYLINE("City Skyline", "textures/parallax/city_skyline.png", 50f, 25f, 2),

        // Near background (fastest)
        BUILDINGS("Buildings", "textures/parallax/buildings.png", 25f, 20f, 3),
        TREES("Trees", "textures/parallax/trees.png", 15f, 12f, 3),

        // Test images
        TEST_HOUSE("Test House", "textures/objects/houses/worker_house.png", 20f, 15f, 1),
        TEST_VILLA("Test Villa", "textures/objects/houses/villa.png", 25f, 18f, 2)
    }

    fun initialize() {
        if (isInitialized) return

        // Create 4 parallax layers with different speeds and distances
        parallaxLayers.clear()

        // Layer 0: Far background (very slow movement)
        parallaxLayers.add(ParallaxLayer(
            depth = baseDistance * 4f,
            speedMultiplier = 0.1f,
            height = 15f
        ))

        // Layer 1: Mid-far background
        parallaxLayers.add(ParallaxLayer(
            depth = baseDistance * 2.5f,
            speedMultiplier = 0.3f,
            height = 12f
        ))

        // Layer 2: Mid-near background
        parallaxLayers.add(ParallaxLayer(
            depth = baseDistance * 1.5f,
            speedMultiplier = 0.6f,
            height = 8f
        ))

        // Layer 3: Near background (faster movement)
        parallaxLayers.add(ParallaxLayer(
            depth = baseDistance,
            speedMultiplier = 0.8f,
            height = 5f
        ))

        isInitialized = true
        println("ParallaxBackgroundSystem initialized with ${parallaxLayers.size} layers")
    }

    private fun createParallaxModel(imageType: ParallaxImageType): Model {
        val material = try {
            val texture = Texture(Gdx.files.internal(imageType.texturePath))
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)

            Material(
                TextureAttribute.createDiffuse(texture),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                IntAttribute.createCullFace(GL20.GL_NONE),
                ColorAttribute.createDiffuse(1f, 1f, 1f, 0.8f) // Slightly transparent for depth
            )
        } catch (e: Exception) {
            println("Failed to load parallax texture ${imageType.texturePath}, using fallback")
            // Create a colored material as fallback
            Material(
                ColorAttribute.createDiffuse(0.7f, 0.8f, 0.9f, 0.6f),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                IntAttribute.createCullFace(GL20.GL_NONE)
            )
        }

        return createQuadModel(material, imageType.width, imageType.height)
    }

    private fun createQuadModel(material: Material, width: Float, height: Float): Model {
        val halfWidth = width / 2f

        modelBuilder.begin()
        val part = modelBuilder.part(
            "parallax_quad",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or
                VertexAttributes.Usage.Normal or
                VertexAttributes.Usage.TextureCoordinates).toLong(),
            material
        )

        // Create quad vertices
        part.vertex(-halfWidth, 0f, 0f, 0f, 0f, 1f, 0f, 1f) // Bottom left
        part.vertex(halfWidth, 0f, 0f, 0f, 0f, 1f, 1f, 1f)  // Bottom right
        part.vertex(halfWidth, height, 0f, 0f, 0f, 1f, 1f, 0f) // Top right
        part.vertex(-halfWidth, height, 0f, 0f, 0f, 1f, 0f, 0f) // Top left

        // Create triangles
        part.triangle(0, 1, 2)
        part.triangle(2, 3, 0)

        return modelBuilder.end()
    }

    fun addParallaxImage(imageType: ParallaxImageType, xPosition: Float, layerIndex: Int = imageType.preferredLayer): Boolean {
        if (!isInitialized) {
            println("ParallaxBackgroundSystem not initialized!")
            return false
        }

        val clampedLayerIndex = layerIndex.coerceIn(0, parallaxLayers.size - 1)
        val layer = parallaxLayers[clampedLayerIndex]

        // Check if there's enough space for this image
        val requiredSpace = imageType.width + defaultImageSpacing
        if (!hasSpaceForImage(layer, xPosition, requiredSpace)) {
            println("Not enough space to place ${imageType.displayName} at position $xPosition")
            return false
        }

        try {
            val model = createParallaxModel(imageType)
            val modelInstance = ModelInstance(model)

            // Calculate the actual 3D position for this layer
            val basePosition = Vector3(xPosition, layer.height, -layer.depth)
            val currentPosition = basePosition.cpy()

            // Set initial transform
            modelInstance.transform.setTranslation(currentPosition)

            val texture = try {
                Texture(Gdx.files.internal(imageType.texturePath))
            } catch (e: Exception) {
                null
            }

            val parallaxImage = ParallaxImage(
                modelInstance = modelInstance,
                // <-- FIX 1: CHANGED Texture.Format TO Pixmap.Format
                texture = texture ?: Texture(1, 1, Pixmap.Format.RGBA8888), // 1x1 fallback
                width = imageType.width,
                height = imageType.height,
                basePosition = basePosition,
                currentPosition = currentPosition,
                layerIndex = clampedLayerIndex
            )

            layer.images.add(parallaxImage)
            println("Added ${imageType.displayName} to layer $clampedLayerIndex at position $xPosition")
            return true

        } catch (e: Exception) {
            println("Failed to create parallax image ${imageType.displayName}: ${e.message}")
            return false
        }
    }

    private fun hasSpaceForImage(layer: ParallaxLayer, xPosition: Float, requiredWidth: Float): Boolean {
        val halfWidth = requiredWidth / 2f
        val leftBound = xPosition - halfWidth
        val rightBound = xPosition + halfWidth

        for (image in layer.images) {
            val imageHalfWidth = image.width / 2f
            val imageLeft = image.basePosition.x - imageHalfWidth
            val imageRight = image.basePosition.x + imageHalfWidth

            // Check for overlap
            if (leftBound < imageRight && rightBound > imageLeft) {
                return false
            }
        }

        return true
    }

    fun findNextAvailablePosition(layerIndex: Int, imageWidth: Float, startX: Float = 0f): Float {
        if (layerIndex < 0 || layerIndex >= parallaxLayers.size) return startX

        val layer = parallaxLayers[layerIndex]
        var testPosition = startX
        val stepSize = 1f
        val maxSearchRange = 200f

        while (abs(testPosition - startX) <= maxSearchRange) {
            if (hasSpaceForImage(layer, testPosition, imageWidth + defaultImageSpacing)) {
                return testPosition
            }
            testPosition += stepSize
        }

        // If no space found moving right, try moving left
        testPosition = startX - stepSize
        while (abs(testPosition - startX) <= maxSearchRange) {
            if (hasSpaceForImage(layer, testPosition, imageWidth + defaultImageSpacing)) {
                return testPosition
            }
            testPosition -= stepSize
        }

        // If still no space, return far right position
        return startX + maxSearchRange
    }

    fun update(cameraPosition: Vector3) {
        if (!isInitialized) return

        // Calculate camera movement delta
        val cameraDelta = Vector3(cameraPosition).sub(lastCameraPosition)

        // Update each layer based on its speed multiplier
        for (layer in parallaxLayers) {
            for (image in layer.images) {
                // Apply parallax movement (only X and Z axes typically)
                val parallaxDelta = Vector3(
                    cameraDelta.x * layer.speedMultiplier,
                    0f, // Y doesn't usually change in parallax
                    cameraDelta.z * layer.speedMultiplier
                )

                // Update current position
                image.currentPosition.add(parallaxDelta)

                // Update model transform
                image.modelInstance.transform.setTranslation(image.currentPosition)
            }
        }

        // Store current camera position for next frame
        lastCameraPosition.set(cameraPosition)
    }

    fun render(modelBatch: com.badlogic.gdx.graphics.g3d.ModelBatch,
               camera: com.badlogic.gdx.graphics.Camera,
               environment: com.badlogic.gdx.graphics.g3d.Environment) {
        if (!isInitialized) return

        // Render layers from back to front (far to near)
        for (layer in parallaxLayers) {
            for (image in layer.images) {
                modelBatch.render(image.modelInstance, environment)
            }
        }
    }

    fun removeImage(layerIndex: Int, xPosition: Float, tolerance: Float = 2f): Boolean {
        if (layerIndex < 0 || layerIndex >= parallaxLayers.size) return false

        val layer = parallaxLayers[layerIndex]
        val imageToRemove = layer.images.find { image ->
            abs(image.basePosition.x - xPosition) < tolerance
        }

        return if (imageToRemove != null) {
            layer.images.removeValue(imageToRemove, true)
            imageToRemove.texture.dispose()
            println("Removed parallax image from layer $layerIndex at position $xPosition")
            true
        } else {
            false
        }
    }

    fun getLayers(): Array<ParallaxLayer> {
        return parallaxLayers
    }

    fun getLayerInfo(): String {
        val info = StringBuilder()
        info.append("Parallax Layers:\n")
        // <-- FIX 2: REPLACED .indices with a manual range
        for (i in 0 until parallaxLayers.size) {
            val layer = parallaxLayers[i]
            info.append("Layer $i: ${layer.images.size} images, depth=${layer.depth}, speed=${layer.speedMultiplier}\n")
        }
        return info.toString()
    }

    fun clearLayer(layerIndex: Int) {
        if (layerIndex < 0 || layerIndex >= parallaxLayers.size) return

        val layer = parallaxLayers[layerIndex]
        for (image in layer.images) {
            image.texture.dispose()
        }
        layer.images.clear()
        println("Cleared parallax layer $layerIndex")
    }

    fun clearAll() {
        for (layer in parallaxLayers) {
            for (image in layer.images) {
                image.texture.dispose()
            }
            layer.images.clear()
        }
        println("Cleared all parallax layers")
    }

    fun dispose() {
        for (layer in parallaxLayers) {
            for (image in layer.images) {
                image.texture.dispose()
            }
        }
        parallaxLayers.clear()
        println("ParallaxBackgroundSystem disposed")
    }
}
