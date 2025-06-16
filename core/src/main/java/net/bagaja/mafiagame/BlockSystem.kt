package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
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
    private val blockModels = mutableMapOf<BlockType, Model>()
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
            try {
                // Load texture
                val texture = Texture(Gdx.files.internal(blockType.texturePath))
                blockTextures[blockType] = texture

                // Create material with texture
                val material = Material(TextureAttribute.createDiffuse(texture))

                // Use the block's height multiplier
                val blockHeight = blockSize * blockType.height

                val model = modelBuilder.createBox(
                    blockSize, blockHeight, blockSize,  // Height varies by block type
                    material,
                    (VertexAttributes.Usage.Position or
                        VertexAttributes.Usage.Normal or
                        VertexAttributes.Usage.TextureCoordinates).toLong()
                )
                blockModels[blockType] = model

                println("Loaded block type: ${blockType.displayName} (height: ${blockType.height})")
            } catch (e: Exception) {
                println("Failed to load texture for ${blockType.displayName}: ${e.message}")
                // Create fallback colored block
                val fallbackColor = when (blockType) {
                    BlockType.GRASS -> Color.GREEN
                    BlockType.COBBLESTONE -> Color.GRAY
                    BlockType.ROOM_FLOOR -> Color.BROWN
                    BlockType.STONE -> Color.GRAY
                    BlockType.WINDOW_OPENED -> Color.WHITE
                    BlockType.WINDOW_CLOSE -> Color.WHITE
                    BlockType.RESTAURANT_FLOOR -> Color.GOLD
                    BlockType.CARGO_FLOOR -> Color.WHITE
                    BlockType.BRICK_WALL -> Color.ORANGE
                    BlockType.STREET_LOW -> Color.BLUE

                    BlockType.BETON_TILE -> Color.LIGHT_GRAY
                    BlockType.BRICK_WALL_PNG -> Color.ORANGE
                    BlockType.BROKEN_CEILING -> Color(0.8f, 0.8f, 0.8f, 1f)
                    BlockType.BROKEN_WALL -> Color(0.7f, 0.6f, 0.5f, 1f)
                    BlockType.BROWN_BRICK_WALL -> Color(0.6f, 0.4f, 0.3f, 1f)
                    BlockType.BROWN_CLEAR_FLOOR -> Color(0.7f, 0.5f, 0.3f, 1f)
                    BlockType.BROWN_FLOOR -> Color.BROWN
                    BlockType.CARD_FLOOR -> Color(0.9f, 0.9f, 0.8f, 1f)
                    BlockType.CARPET -> Color.MAROON
                    BlockType.CEILING_WITH_LAMP -> Color.WHITE
                    BlockType.CEILING -> Color.WHITE
                    BlockType.CLUSTER_FLOOR -> Color(0.6f, 0.6f, 0.7f, 1f)
                    BlockType.CRACKED_WALL -> Color(0.8f, 0.8f, 0.7f, 1f)
                    BlockType.DARK_WALL -> Color(0.3f, 0.3f, 0.3f, 1f)
                    BlockType.DARK_YELLOW_FLOOR -> Color(0.7f, 0.6f, 0.2f, 1f)
                    BlockType.DIRTY_GROUND -> Color(0.5f, 0.4f, 0.3f, 1f)
                    BlockType.FLIESSEN -> Color(0.9f, 0.95f, 1f, 1f)
                    BlockType.FLOOR -> Color(0.8f, 0.8f, 0.8f, 1f)
                    BlockType.GRAY_FLOOR -> Color.GRAY
                    BlockType.LIGHT_CEILING -> Color(0.95f, 0.95f, 1f, 1f)
                    BlockType.OFFICE_WALL -> Color(0.9f, 0.9f, 0.85f, 1f)
                    BlockType.SIDEWALK_POOR -> Color.LIGHT_GRAY
                    BlockType.SIDEWALK -> Color.LIGHT_GRAY
                    BlockType.SIDEWALK_START -> Color.LIGHT_GRAY
                    BlockType.SPRAYED_WALL -> Color(0.7f, 0.8f, 0.6f, 1f)
                    BlockType.STREET_TILE -> Color(0.4f, 0.4f, 0.4f, 1f)
                    BlockType.STRIPED_FLOOR -> Color(0.8f, 0.7f, 0.6f, 1f)
                    BlockType.STRIPED_TAPETE -> Color(0.9f, 0.8f, 0.7f, 1f)
                    BlockType.TAPETE -> Color(0.8f, 0.7f, 0.6f, 1f)
                    BlockType.TAPETE_WALL -> Color(0.85f, 0.75f, 0.65f, 1f)
                    BlockType.TRANS_WALL -> Color(0.9f, 0.9f, 0.9f, 0.8f)
                    BlockType.WALL -> Color(0.9f, 0.9f, 0.9f, 1f)
                    BlockType.WOOD_WALL -> Color(0.6f, 0.4f, 0.2f, 1f)
                    BlockType.WOODEN_FLOOR -> Color(0.7f, 0.5f, 0.3f, 1f)
                }
                val material = Material(ColorAttribute.createDiffuse(fallbackColor))
                val blockHeight = blockSize * blockType.height
                val model = modelBuilder.createBox(
                    blockSize, blockHeight, blockSize,
                    material,
                    (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
                )
                blockModels[blockType] = model
            }
        }
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

    fun createBlockInstance(blockType: BlockType): ModelInstance? {
        val model = blockModels[blockType]
        return model?.let { ModelInstance(it) }
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
        blockModels.values.forEach { it.dispose() }
        blockTextures.values.forEach { it.dispose() }
    }
}
