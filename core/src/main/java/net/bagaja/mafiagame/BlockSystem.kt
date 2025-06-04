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

// Block system class to manage different block types
class BlockSystem {
    private val blockModels = mutableMapOf<BlockType, Model>()
    private val blockTextures = mutableMapOf<BlockType, Texture>()
    var currentSelectedBlock = BlockType.GRASS
        private set
    var currentSelectedBlockIndex = 0
        private set

    fun initialize(blockSize: Float) {
        val modelBuilder = ModelBuilder()

        // Load textures and create models for each block type
        for (blockType in BlockType.values()) {
            try {
                // Load texture
                val texture = Texture(Gdx.files.internal(blockType.texturePath))
                blockTextures[blockType] = texture

                // Create material with texture
                val material = Material(TextureAttribute.createDiffuse(texture))

                // Create cube model
                val model = modelBuilder.createBox(
                    blockSize, blockSize, blockSize,
                    material,
                    (VertexAttributes.Usage.Position or
                        VertexAttributes.Usage.Normal or
                        VertexAttributes.Usage.TextureCoordinates).toLong()
                )
                blockModels[blockType] = model

                println("Loaded block type: ${blockType.displayName}")
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
                }
                val material = Material(ColorAttribute.createDiffuse(fallbackColor))
                val model = modelBuilder.createBox(
                    blockSize, blockSize, blockSize,
                    material,
                    (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
                )
                blockModels[blockType] = model
            }
        }
    }

    fun nextBlock() {
        currentSelectedBlockIndex = (currentSelectedBlockIndex + 1) % BlockType.values().size
        currentSelectedBlock = BlockType.values()[currentSelectedBlockIndex]
        println("Selected block: ${currentSelectedBlock.displayName}")
    }

    fun previousBlock() {
        currentSelectedBlockIndex = if (currentSelectedBlockIndex > 0) {
            currentSelectedBlockIndex - 1
        } else {
            BlockType.values().size - 1
        }
        currentSelectedBlock = BlockType.values()[currentSelectedBlockIndex]
        println("Selected block: ${currentSelectedBlock.displayName}")
    }

    fun createBlockInstance(blockType: BlockType): ModelInstance? {
        val model = blockModels[blockType]
        return model?.let { ModelInstance(it) }
    }

    fun getCurrentBlockTexture(): Texture? {
        return blockTextures[currentSelectedBlock]
    }

    fun dispose() {
        blockModels.values.forEach { it.dispose() }
        blockTextures.values.forEach { it.dispose() }
    }
}
