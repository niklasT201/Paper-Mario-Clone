package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3

// Block system class to manage different block types
class BlockSystem {
    // Store a map of models for each face of each block type
    private val blockFaceModels = mutableMapOf<BlockType, Map<BlockFace, Model>>()
    private val blockTextures = mutableMapOf<BlockType, Texture>()
    var currentSelectedBlock = BlockType.GRASS
        private set
    var currentSelectedBlockIndex = 0
        private set
    var currentBlockRotation = 0f
        private set

    fun initialize(blockSize: Float) {
        val modelBuilder = ModelBuilder()

        // Load textures and create models for each block type
        for (blockType in BlockType.entries) {
            val material = try {
                val texture = Texture(Gdx.files.internal(blockType.texturePath))
                blockTextures[blockType] = texture
                Material(TextureAttribute.createDiffuse(texture))
            } catch (e: Exception) {
                println("Failed to load texture for ${blockType.displayName}, using fallback color. Error: ${e.message}")
                Material(ColorAttribute.createDiffuse(getFallbackColor(blockType)))
            }

            val modelsForBlock = mutableMapOf<BlockFace, Model>()
            for (face in BlockFace.entries) {
                modelsForBlock[face] = createFaceModel(modelBuilder, blockSize, blockType.height, material, face)
            }
            blockFaceModels[blockType] = modelsForBlock
            println("Loaded models for block type: ${blockType.displayName}")
        }
    }

    private fun createFaceModel(
        modelBuilder: ModelBuilder,
        size: Float,
        heightMultiplier: Float,
        material: Material,
        face: BlockFace
    ): Model {
        val halfSize = size / 2f
        val halfHeight = (size * heightMultiplier) / 2f

        val attributes = (VertexAttributes.Usage.Position or
            VertexAttributes.Usage.Normal or
            VertexAttributes.Usage.TextureCoordinates).toLong()

        modelBuilder.begin()
        val partBuilder = modelBuilder.part(face.name, GL20.GL_TRIANGLES, attributes, material)

        when (face) {
            // Looking from above (+Y), this is a CCW order
            BlockFace.TOP -> partBuilder.rect(
                Vector3(-halfSize, halfHeight, halfSize),
                Vector3(halfSize, halfHeight, halfSize),
                Vector3(halfSize, halfHeight, -halfSize),
                Vector3(-halfSize, halfHeight, -halfSize),
                Vector3(0f, 1f, 0f)
            )
            // Looking from below (-Y), this is a CCW order
            BlockFace.BOTTOM -> partBuilder.rect(
                Vector3(-halfSize, -halfHeight, -halfSize),
                Vector3(halfSize, -halfHeight, -halfSize),
                Vector3(halfSize, -halfHeight, halfSize),
                Vector3(-halfSize, -halfHeight, halfSize),
                Vector3(0f, -1f, 0f)
            )
            // Looking from the front (+Z), this is a CCW order
            BlockFace.FRONT -> partBuilder.rect(
                Vector3(-halfSize, -halfHeight, halfSize),
                Vector3(halfSize, -halfHeight, halfSize),
                Vector3(halfSize, halfHeight, halfSize),
                Vector3(-halfSize, halfHeight, halfSize),
                Vector3(0f, 0f, 1f)
            )
            // Looking from the back (-Z), this is a CCW order
            BlockFace.BACK -> partBuilder.rect(
                Vector3(halfSize, -halfHeight, -halfSize),
                Vector3(-halfSize, -halfHeight, -halfSize),
                Vector3(-halfSize, halfHeight, -halfSize),
                Vector3(halfSize, halfHeight, -halfSize),
                Vector3(0f, 0f, -1f)
            )
            // Looking from the right (+X), this is a CCW order
            BlockFace.RIGHT -> partBuilder.rect(
                Vector3(halfSize, -halfHeight, halfSize),
                Vector3(halfSize, -halfHeight, -halfSize),
                Vector3(halfSize, halfHeight, -halfSize),
                Vector3(halfSize, halfHeight, halfSize),
                Vector3(1f, 0f, 0f)
            )
            // Looking from the left (-X), this is a CCW order
            BlockFace.LEFT -> partBuilder.rect(
                Vector3(-halfSize, -halfHeight, -halfSize),
                Vector3(-halfSize, -halfHeight, halfSize),
                Vector3(-halfSize, halfHeight, halfSize),
                Vector3(-halfSize, halfHeight, -halfSize),
                Vector3(-1f, 0f, 0f)
            )
        }
        return modelBuilder.end()
    }

    fun createFaceInstances(blockType: BlockType): Map<BlockFace, ModelInstance>? {
        val models = blockFaceModels[blockType] ?: return null
        return models.mapValues { ModelInstance(it.value) }
    }

    fun rotateCurrentBlock() {
        currentBlockRotation = (currentBlockRotation + 90f) % 360f
        println("Block rotation: ${currentBlockRotation}° (${getRotationDirection()})")
    }

    private fun getRotationDirection(): String {
        return when (currentBlockRotation.toInt()) {
            0 -> "North"
            90 -> "East"
            180 -> "South"
            270 -> "West"
            else -> "Unknown"
        }
    }

    // Reset rotation (useful for testing or if you want a reset key)
    fun resetRotation() {
        currentBlockRotation = 0f
        println("Block rotation reset to 0°")
    }

    fun rotateCurrentBlockReverse() {
        currentBlockRotation = (currentBlockRotation - 90f + 360f) % 360f
        println("Block rotation (reverse): ${currentBlockRotation}° (${getRotationDirection()})")
    }

    fun nextBlock() {
        currentSelectedBlockIndex = (currentSelectedBlockIndex + 1) % BlockType.entries.size
        currentSelectedBlock = BlockType.entries.toTypedArray()[currentSelectedBlockIndex]
        println("Selected block: ${currentSelectedBlock.displayName}")
    }

    fun previousBlock() {
        currentSelectedBlockIndex = if (currentSelectedBlockIndex > 0) {
            currentSelectedBlockIndex - 1
        } else {
            BlockType.entries.size - 1
        }
        currentSelectedBlock = BlockType.entries.toTypedArray()[currentSelectedBlockIndex]
        println("Selected block: ${currentSelectedBlock.displayName}")
    }

    fun getCurrentBlockTexture(): Texture? {
        return blockTextures[currentSelectedBlock]
    }

    fun setSelectedBlock(index: Int) {
        if (index >= 0 && index < BlockType.entries.size) {
            currentSelectedBlockIndex = index
            currentSelectedBlock = BlockType.entries.toTypedArray()[index]
            println("Selected block: ${currentSelectedBlock.displayName}")
        }
    }

    fun dispose() {
        blockFaceModels.values.forEach { map -> map.values.forEach { it.dispose() } }
        blockTextures.values.forEach { it.dispose() }
    }

    private fun getFallbackColor(blockType: BlockType): Color {
        return Color.MAGENTA // Default fallback
    }
}
