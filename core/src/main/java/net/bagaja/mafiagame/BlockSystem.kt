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
    // Caches for the two different model types
    private val blockFaceModels = mutableMapOf<BlockType, Map<BlockFace, Model>>()
    private val customShapeModels = mutableMapOf<Pair<BlockType, BlockShape>, Model>()

    private val blockTextures = mutableMapOf<BlockType, Texture>()
    var currentSelectedBlock = BlockType.GRASS
        private set
    var currentSelectedBlockIndex = 0
        private set
    var currentSelectedShape = BlockShape.FULL_BLOCK
        private set
    var currentBlockRotation = 0f
        private set

    private lateinit var modelBuilder: ModelBuilder
    private var internalBlockSize: Float = 4f

    fun initialize(blockSize: Float) {
        this.internalBlockSize = blockSize
        modelBuilder = ModelBuilder()

        // Load textures and create models for each block type
        for (blockType in BlockType.entries) {
            try {
                val texture = Texture(Gdx.files.internal(blockType.texturePath))
                blockTextures[blockType] = texture
            } catch (e: Exception) {
                println("Failed to load texture for ${blockType.displayName}: ${e.message}")
            }
            // Pre-generate face models for culling
            createFaceModelsForBlockType(blockType, blockSize)
        }
    }

    /**
     * Creates a GameBlock, deciding whether to use face-culling or a custom model.
     */
    fun createGameBlock(type: BlockType, shape: BlockShape, position: Vector3, rotation: Float): GameBlock {
        return if (shape == BlockShape.FULL_BLOCK) {
            val faceInstances = createFaceInstances(type)!!
            GameBlock(type, shape, position, rotation, faceInstances = faceInstances)
        } else {
            val modelInstance = createCustomShapeInstance(type, shape)!!
            GameBlock(type, shape, position, rotation, modelInstance = modelInstance)
        }
    }

    private fun createFaceModelsForBlockType(blockType: BlockType, blockSize: Float) {
        val material = blockTextures[blockType]?.let {
            Material(TextureAttribute.createDiffuse(it))
        } ?: Material(ColorAttribute.createDiffuse(Color.MAGENTA))

        val modelsForBlock = mutableMapOf<BlockFace, Model>()
        for (face in BlockFace.entries) {
            modelsForBlock[face] = createFaceModel(blockSize, blockType.height, material, face)
        }
        blockFaceModels[blockType] = modelsForBlock
        println("Loaded models for block type: ${blockType.displayName}")
    }

    private fun createFaceModel(size: Float, heightMultiplier: Float, material: Material, face: BlockFace): Model {
        val halfSize = size / 2f
        val halfHeight = (size * heightMultiplier) / 2f
        val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()

        modelBuilder.begin()
        val partBuilder = modelBuilder.part(face.name, GL20.GL_TRIANGLES, attributes, material)

        when (face) {
            BlockFace.TOP -> partBuilder.rect(Vector3(-halfSize, halfHeight, halfSize), Vector3(halfSize, halfHeight, halfSize), Vector3(halfSize, halfHeight, -halfSize), Vector3(-halfSize, halfHeight, -halfSize), Vector3(0f, 1f, 0f))
            BlockFace.BOTTOM -> partBuilder.rect(Vector3(-halfSize, -halfHeight, -halfSize), Vector3(halfSize, -halfHeight, -halfSize), Vector3(halfSize, -halfHeight, halfSize), Vector3(-halfSize, -halfHeight, halfSize), Vector3(0f, -1f, 0f))
            BlockFace.FRONT -> partBuilder.rect(Vector3(-halfSize, -halfHeight, halfSize), Vector3(halfSize, -halfHeight, halfSize), Vector3(halfSize, halfHeight, halfSize), Vector3(-halfSize, halfHeight, halfSize), Vector3(0f, 0f, 1f))
            BlockFace.BACK -> partBuilder.rect(Vector3(halfSize, -halfHeight, -halfSize), Vector3(-halfSize, -halfHeight, -halfSize), Vector3(-halfSize, halfHeight, -halfSize), Vector3(halfSize, halfHeight, -halfSize), Vector3(0f, 0f, -1f))
            BlockFace.RIGHT -> partBuilder.rect(Vector3(halfSize, -halfHeight, halfSize), Vector3(halfSize, -halfHeight, -halfSize), Vector3(halfSize, halfHeight, -halfSize), Vector3(halfSize, halfHeight, halfSize), Vector3(1f, 0f, 0f))
            BlockFace.LEFT -> partBuilder.rect(Vector3(-halfSize, -halfHeight, -halfSize), Vector3(-halfSize, -halfHeight, halfSize), Vector3(-halfSize, halfHeight, halfSize), Vector3(-halfSize, halfHeight, -halfSize), Vector3(-1f, 0f, 0f))
        }
        return modelBuilder.end()
    }

    fun createFaceInstances(blockType: BlockType): Map<BlockFace, ModelInstance>? {
        val models = blockFaceModels[blockType] ?: return null
        return models.mapValues { ModelInstance(it.value) }
    }

    private fun createCustomShapeInstance(blockType: BlockType, shape: BlockShape): ModelInstance? {
        val model = getOrCreateModel(blockType, shape)
        return model?.let { ModelInstance(it) }
    }

    private fun getOrCreateModel(blockType: BlockType, shape: BlockShape): Model? {
        val key = Pair(blockType, shape)
        if (!customShapeModels.containsKey(key)) {
            val material = blockTextures[blockType]?.let {
                Material(TextureAttribute.createDiffuse(it))
            } ?: Material(ColorAttribute.createDiffuse(Color.MAGENTA))

            val newModel = createCustomModel(shape, material)
            if (newModel != null) {
                customShapeModels[key] = newModel
            }
        }
        return customShapeModels[key]
    }

    private fun createCustomModel(shape: BlockShape, material: Material): Model? {
        val attributes = (VertexAttributes.Usage.Position or
            VertexAttributes.Usage.Normal or
            VertexAttributes.Usage.TextureCoordinates).toLong()

        return when (shape) {
            BlockShape.FULL_BLOCK -> {
                modelBuilder.createBox(internalBlockSize, internalBlockSize, internalBlockSize, material, attributes)
            }
            BlockShape.SLAB_BOTTOM, BlockShape.SLAB_TOP -> {
                modelBuilder.createBox(internalBlockSize, internalBlockSize / 2f, internalBlockSize, material, attributes)
            }
            BlockShape.WEDGE -> {
                modelBuilder.begin()
                val part = modelBuilder.part("model", GL20.GL_TRIANGLES, attributes, material)
                val half = internalBlockSize / 2f

                // Defines a ramp that is high at the +Z side and low at the -Z side.
                val v0 = Vector3(-half, -half, +half) // Bottom-front-left (high side)
                val v1 = Vector3(+half, -half, +half) // Bottom-front-right (high side)
                val v2 = Vector3(-half, +half, +half) // Top-front-left (high side)
                val v3 = Vector3(+half, +half, +half) // Top-front-right (high side)
                val v4 = Vector3(-half, -half, -half) // Bottom-back-left (low side)
                val v5 = Vector3(+half, -half, -half) // Bottom-back-right (low side)

                // 1. Bottom face (a flat rectangle)
                part.rect(v4, v5, v1, v0, Vector3(0f, -1f, 0f)) // Bottom face
                part.rect(v0, v1, v3, v2, Vector3(0f, 0f, 1f))  // Front face

                // Left side face (a triangle)
                val leftNormal = Vector3(-1f, 0f, 0f)
                val l1 = part.vertex(v4, leftNormal, null, com.badlogic.gdx.math.Vector2(0f, 0f))
                val l2 = part.vertex(v0, leftNormal, null, com.badlogic.gdx.math.Vector2(1f, 0f))
                val l3 = part.vertex(v2, leftNormal, null, com.badlogic.gdx.math.Vector2(1f, 1f))
                part.triangle(l1, l2, l3)

                // Right side face (a triangle)
                val rightNormal = Vector3(1f, 0f, 0f)
                val r1 = part.vertex(v1, rightNormal, null, com.badlogic.gdx.math.Vector2(0f, 0f))
                val r2 = part.vertex(v5, rightNormal, null, com.badlogic.gdx.math.Vector2(1f, 0f))
                val r3 = part.vertex(v3, rightNormal, null, com.badlogic.gdx.math.Vector2(0f, 1f))
                part.triangle(r1, r2, r3)

                // Sloped top face (built with two textured triangles)
                val slopeNormal = Vector3(v2).sub(v4).crs(Vector3(v5).sub(v4)).nor()
                val s_v2 = part.vertex(v2, slopeNormal, null, com.badlogic.gdx.math.Vector2(0f, 1f))
                val s_v3 = part.vertex(v3, slopeNormal, null, com.badlogic.gdx.math.Vector2(1f, 1f))
                val s_v4 = part.vertex(v4, slopeNormal, null, com.badlogic.gdx.math.Vector2(0f, 0f))
                val s_v5 = part.vertex(v5, slopeNormal, null, com.badlogic.gdx.math.Vector2(1f, 0f))
                part.triangle(s_v2, s_v3, s_v5)
                part.triangle(s_v2, s_v5, s_v4)

                modelBuilder.end()
            }

            BlockShape.CORNER_WEDGE -> {
                modelBuilder.begin()
                val part = modelBuilder.part("model", GL20.GL_TRIANGLES, attributes, material)
                val half = internalBlockSize / 2f
                val slabHeight = internalBlockSize

                // Define the 6 corners of the "cake slice"
                val v_bottom_corner = Vector3(-half, -half, -half) // The pointy corner
                val v_bottom_x = Vector3(half, -half, -half)      // The corner along the X axis
                val v_bottom_z = Vector3(-half, -half, half)      // The corner along the Z axis

                val topY = -half + slabHeight
                val v_top_corner = Vector3(-half, topY, -half)
                val v_top_x = Vector3(half, topY, -half)
                val v_top_z = Vector3(-half, topY, half)

                // 1. Bottom face (triangle)
                val b1 = part.vertex(v_bottom_corner, Vector3.Y.scl(-1f), null, com.badlogic.gdx.math.Vector2(0f, 0f))
                val b2 = part.vertex(v_bottom_x, Vector3.Y.scl(-1f), null, com.badlogic.gdx.math.Vector2(1f, 0f))
                val b3 = part.vertex(v_bottom_z, Vector3.Y.scl(-1f), null, com.badlogic.gdx.math.Vector2(0f, 1f))
                part.triangle(b1, b2, b3)

                // 2. Top face (triangle)
                val t1 = part.vertex(v_top_corner, Vector3.Y, null, com.badlogic.gdx.math.Vector2(0f, 0f))
                val t2 = part.vertex(v_top_z, Vector3.Y, null, com.badlogic.gdx.math.Vector2(0f, 1f))
                val t3 = part.vertex(v_top_x, Vector3.Y, null, com.badlogic.gdx.math.Vector2(1f, 0f))
                part.triangle(t1, t2, t3)

                // 3. Back face (rectangle, along XZ plane at Z=-half)
                part.rect(v_bottom_x, v_bottom_corner, v_top_corner, v_top_x, Vector3(0f, 0f, -1f))

                // 4. Left face (rectangle, along YZ plane at X=-half)
                part.rect(v_bottom_corner, v_bottom_z, v_top_z, v_top_corner, Vector3(-1f, 0f, 0f))

                // 5. Sloped face (the diagonal "cut" of the cake)
                val slopedNormal = Vector3(v_bottom_z).sub(v_bottom_x).crs(Vector3(v_top_x).sub(v_bottom_x)).nor()
                part.rect(v_bottom_z, v_bottom_x, v_top_x, v_top_z, slopedNormal)

                modelBuilder.end()
            }
        }
    }

    fun rotateCurrentBlock() { currentBlockRotation = (currentBlockRotation + 90f) % 360f }
    fun rotateCurrentBlockReverse() { currentBlockRotation = (currentBlockRotation - 90f + 360f) % 360f }
    fun nextShape() {
        val shapes = BlockShape.entries
        currentSelectedShape = shapes[(shapes.indexOf(currentSelectedShape) + 1) % shapes.size]
    }
    fun previousShape() {
        val shapes = BlockShape.entries
        val current = shapes.indexOf(currentSelectedShape)
        currentSelectedShape = if (current > 0) shapes[current - 1] else shapes.last()
    }
    fun nextBlock() {
        currentSelectedBlockIndex = (currentSelectedBlockIndex + 1) % BlockType.entries.size
        currentSelectedBlock = BlockType.entries[currentSelectedBlockIndex]
        println("Selected block: ${currentSelectedBlock.displayName}")
    }

    fun previousBlock() {
        currentSelectedBlockIndex = if (currentSelectedBlockIndex > 0) currentSelectedBlockIndex - 1 else BlockType.entries.size - 1
        currentSelectedBlock = BlockType.entries[currentSelectedBlockIndex]
    }
    fun getCurrentBlockTexture(): Texture? = blockTextures[currentSelectedBlock]
    fun setSelectedBlock(index: Int) {
        if (index in BlockType.entries.indices) {
            currentSelectedBlockIndex = index
            currentSelectedBlock = BlockType.entries[index]
            println("Selected block: ${currentSelectedBlock.displayName}")
        }
    }

    fun dispose() {
        blockFaceModels.values.forEach { map -> map.values.forEach { it.dispose() } }
        customShapeModels.values.forEach { it.dispose() }
        blockTextures.values.forEach { it.dispose() }
    }
}
