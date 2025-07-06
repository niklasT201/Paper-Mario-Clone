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
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3

// Block system class to manage different block types
class BlockSystem {
    // Caches for the two different model types
    private val blockFaceModels = mutableMapOf<BlockType, Map<BlockFace, Map<Float, Model>>>()
    // MODIFIED: The key now includes the texture rotation to cache rotated models
    private val customShapeModels = mutableMapOf<Triple<BlockType, BlockShape, Float>, Model>()

    private val blockTextures = mutableMapOf<BlockType, Texture>()
    var currentSelectedBlock = BlockType.GRASS
        private set
    var currentSelectedBlockIndex = 0
        private set
    var currentSelectedShape = BlockShape.FULL_BLOCK
        private set
    var currentGeometryRotation = 0f
        private set
    var currentTextureRotation = 0f
        private set
    var rotationMode = BlockRotationMode.GEOMETRY
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

    fun toggleRotationMode() {
        rotationMode = if (rotationMode == BlockRotationMode.GEOMETRY) {
            BlockRotationMode.TEXTURE_SIDES
        } else {
            BlockRotationMode.GEOMETRY
        }
        println("Block rotation mode set to: ${rotationMode.getDisplayName()}")
    }

    /**
     * Creates a GameBlock, deciding whether to use face-culling or a custom model.
     */
    fun createGameBlock(type: BlockType, shape: BlockShape, position: Vector3, geometryRotation: Float, textureRotation: Float): GameBlock {
        return if (shape == BlockShape.FULL_BLOCK) {
            // Always use the textureRotation parameter to create the faces.
            val faceInstances = createFaceInstances(type, textureRotation)!!
            // Create the GameBlock, storing both rotations.
            GameBlock(
                type,
                shape,
                position,
                rotationY = geometryRotation,
                textureRotationY = textureRotation, // Store the texture rotation
                faceInstances = faceInstances
            )
        } else {
            val modelInstance = createCustomShapeInstance(type, shape, textureRotation)!!
            GameBlock(
                type,
                shape,
                position,
                rotationY = geometryRotation,
                textureRotationY = textureRotation,
                modelInstance = modelInstance
            )
        }
    }

    private fun createFaceModelsForBlockType(blockType: BlockType, blockSize: Float) {
        val material = blockTextures[blockType]?.let {
            Material(TextureAttribute.createDiffuse(it))
        } ?: Material(ColorAttribute.createDiffuse(Color.MAGENTA))

        val modelsForBlock = mutableMapOf<BlockFace, Map<Float, Model>>()
        val rotations = listOf(0f, 90f, 180f, 270f)

        for (face in BlockFace.entries) {
            val modelsForFace = mutableMapOf<Float, Model>()
            if (face == BlockFace.TOP || face == BlockFace.BOTTOM) {
                // Top and bottom faces are not affected by side texture rotation.
                modelsForFace[0f] = createFaceModel(blockSize, blockType.height, material, face, 0f)
            } else {
                // Side faces need a model for each rotation angle.
                for (rotation in rotations) {
                    modelsForFace[rotation] = createFaceModel(blockSize, blockType.height, material, face, rotation)
                }
            }
            modelsForBlock[face] = modelsForFace
        }
        blockFaceModels[blockType] = modelsForBlock
        println("Loaded models for block type: ${blockType.displayName}")
    }

    private fun createFaceModel(size: Float, heightMultiplier: Float, material: Material, face: BlockFace, textureRotation: Float): Model {
        val halfSize = size / 2f
        val halfHeight = (size * heightMultiplier) / 2f
        val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()

        modelBuilder.begin()
        val partBuilder = modelBuilder.part("${face.name}_${textureRotation.toInt()}", GL20.GL_TRIANGLES, attributes, material)

        // Standard UV coordinates for a quad.
        val uv00 = Vector2(0f, 1f) // Bottom-left
        val uv10 = Vector2(1f, 1f) // Bottom-right
        val uv11 = Vector2(1f, 0f) // Top-right
        val uv01 = Vector2(0f, 0f) // Top-left

        // Create an array of the UVs in the correct order for rotation
        val uvs = arrayOf(uv00, uv10, uv11, uv01)
        val rotationSteps = (textureRotation / 90f).toInt().let { if (it < 0) it + 4 else it } % 4

        // Apply rotation by shifting the UV array
        val r_uv00 = uvs[(0 + rotationSteps) % 4]
        val r_uv10 = uvs[(1 + rotationSteps) % 4]
        val r_uv11 = uvs[(2 + rotationSteps) % 4]
        val r_uv01 = uvs[(3 + rotationSteps) % 4]

        val tmpV1 = Vector3()
        val tmpV2 = Vector3()

        fun buildRect(corner00: Vector3, corner10: Vector3, corner11: Vector3, corner01: Vector3, normal: Vector3) {
            tmpV1.set(corner00).lerp(corner11, 0.5f)
            tmpV2.set(corner10).lerp(corner01, 0.5f)
            val numV = tmpV2.sub(tmpV1).len() / (tmpV1.set(corner00).sub(corner10).len() + tmpV1.set(corner10).sub(corner11).len())

            val p1 = partBuilder.vertex(corner00, normal, null, r_uv00)
            val p2 = partBuilder.vertex(corner10, normal, null, r_uv10)
            val p3 = partBuilder.vertex(corner11, normal, null, r_uv11)
            val p4 = partBuilder.vertex(corner01, normal, null, r_uv01)
            partBuilder.triangle(p1, p2, p3)
            partBuilder.triangle(p3, p4, p1)
        }

        when (face) {
            BlockFace.TOP -> buildRect(
                Vector3(-halfSize, halfHeight, halfSize), Vector3(halfSize, halfHeight, halfSize),
                Vector3(halfSize, halfHeight, -halfSize), Vector3(-halfSize, halfHeight, -halfSize),
                Vector3(0f, 1f, 0f)
            )
            BlockFace.BOTTOM -> buildRect(
                Vector3(-halfSize, -halfHeight, -halfSize), Vector3(halfSize, -halfHeight, -halfSize),
                Vector3(halfSize, -halfHeight, halfSize), Vector3(-halfSize, -halfHeight, halfSize),
                Vector3(0f, -1f, 0f)
            )
            BlockFace.FRONT -> buildRect(
                Vector3(-halfSize, -halfHeight, halfSize), Vector3(halfSize, -halfHeight, halfSize),
                Vector3(halfSize, halfHeight, halfSize), Vector3(-halfSize, halfHeight, halfSize),
                Vector3(0f, 0f, 1f)
            )
            BlockFace.BACK -> buildRect(
                Vector3(halfSize, -halfHeight, -halfSize), Vector3(-halfSize, -halfHeight, -halfSize),
                Vector3(-halfSize, halfHeight, -halfSize), Vector3(halfSize, halfHeight, -halfSize),
                Vector3(0f, 0f, -1f)
            )
            BlockFace.RIGHT -> buildRect(
                Vector3(halfSize, -halfHeight, halfSize), Vector3(halfSize, -halfHeight, -halfSize),
                Vector3(halfSize, halfHeight, -halfSize), Vector3(halfSize, halfHeight, halfSize),
                Vector3(1f, 0f, 0f)
            )
            BlockFace.LEFT -> buildRect(
                Vector3(-halfSize, -halfHeight, -halfSize), Vector3(-halfSize, -halfHeight, halfSize),
                Vector3(-halfSize, halfHeight, halfSize), Vector3(-halfSize, halfHeight, -halfSize),
                Vector3(-1f, 0f, 0f)
            )
        }
        return modelBuilder.end()
    }

    private fun createFaceInstances(blockType: BlockType, textureRotation: Float): Map<BlockFace, ModelInstance>? {
        val typeModels = blockFaceModels[blockType] ?: return null
        val instances = mutableMapOf<BlockFace, ModelInstance>()

        for (face in BlockFace.entries) {
            val faceModels = typeModels[face] ?: continue
            val model = if (face == BlockFace.TOP || face == BlockFace.BOTTOM) {
                faceModels[0f] // Top/Bottom faces are never rotated
            } else {
                faceModels[textureRotation] // Side faces use the specified rotation
            }
            if (model != null) {
                instances[face] = ModelInstance(model)
            }
        }
        return instances
    }

    private fun createCustomShapeInstance(blockType: BlockType, shape: BlockShape, textureRotation: Float): ModelInstance? {
        val model = getOrCreateModel(blockType, shape, textureRotation)
        return model?.let { ModelInstance(it) }
    }

    private fun getOrCreateModel(blockType: BlockType, shape: BlockShape, textureRotation: Float): Model? {
        val key = Triple(blockType, shape, textureRotation)
        if (!customShapeModels.containsKey(key)) {
            val material = blockTextures[blockType]?.let {
                Material(TextureAttribute.createDiffuse(it))
            } ?: Material(ColorAttribute.createDiffuse(Color.MAGENTA))

            val newModel = createCustomModel(shape, material, textureRotation)
            if (newModel != null) {
                customShapeModels[key] = newModel
            }
        }
        return customShapeModels[key]
    }

    private fun createCustomModel(shape: BlockShape, material: Material, textureRotation: Float): Model? {
        val attributes = (VertexAttributes.Usage.Position or
            VertexAttributes.Usage.Normal or
            VertexAttributes.Usage.TextureCoordinates).toLong()

        val uv00 = Vector2(0f, 1f); val uv10 = Vector2(1f, 1f)
        val uv11 = Vector2(1f, 0f); val uv01 = Vector2(0f, 0f)

        // Calculate rotated UVs for side faces
        val uvs = arrayOf(uv00, uv10, uv11, uv01)
        val rotationSteps = (textureRotation / 90f).toInt().let { if (it < 0) it + 4 else it } % 4
        val r_uv00 = uvs[(0 + rotationSteps) % 4]
        val r_uv10 = uvs[(1 + rotationSteps) % 4]
        val r_uv11 = uvs[(2 + rotationSteps) % 4]
        val r_uv01 = uvs[(3 + rotationSteps) % 4]

        fun buildRectWithUVs(partBuilder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder,
                             corner00: Vector3, corner10: Vector3, corner11: Vector3, corner01: Vector3, normal: Vector3,
                             _uv00: Vector2, _uv10: Vector2, _uv11: Vector2, _uv01: Vector2) {
            val p1 = partBuilder.vertex(corner00, normal, null, _uv00)
            val p2 = partBuilder.vertex(corner10, normal, null, _uv10)
            val p3 = partBuilder.vertex(corner11, normal, null, _uv11)
            val p4 = partBuilder.vertex(corner01, normal, null, _uv01)
            partBuilder.triangle(p1, p2, p3)
            partBuilder.triangle(p3, p4, p1)
        }

        return when (shape) {
            BlockShape.FULL_BLOCK -> {
                modelBuilder.createBox(internalBlockSize, internalBlockSize, internalBlockSize, material, attributes)
            }
            BlockShape.SLAB_BOTTOM, BlockShape.SLAB_TOP -> {
                modelBuilder.createBox(internalBlockSize, internalBlockSize / 2f, internalBlockSize, material, attributes)
            }
            BlockShape.VERTICAL_SLAB -> {
                modelBuilder.begin()
                val part = modelBuilder.part("v_slab_${textureRotation.toInt()}", GL20.GL_TRIANGLES, attributes, material)
                val hx = internalBlockSize / 2f; val hy = internalBlockSize / 2f; val hz = internalBlockSize / 4f

                val v_blf = Vector3(-hx, -hy,  hz); val v_brf = Vector3( hx, -hy,  hz)
                val v_trf = Vector3( hx,  hy,  hz); val v_tlf = Vector3(-hx,  hy,  hz)
                val v_blb = Vector3(-hx, -hy, -hz); val v_brb = Vector3( hx, -hy, -hz)
                val v_trb = Vector3( hx,  hy, -hz); val v_tlb = Vector3(-hx,  hy, -hz)

                // Top and Bottom (Standard UVs)
                buildRectWithUVs(part, v_tlf, v_trf, v_trb, v_tlb, Vector3.Y, uv00, uv10, uv11, uv01)
                buildRectWithUVs(part, v_blb, v_brb, v_brf, v_blf, Vector3.Y.cpy().scl(-1f), uv00, uv10, uv11, uv01)

                // Sides (Rotated UVs)
                buildRectWithUVs(part, v_blf, v_brf, v_trf, v_tlf, Vector3.Z, r_uv00, r_uv10, r_uv11, r_uv01) // Front
                buildRectWithUVs(part, v_brb, v_blb, v_tlb, v_trb, Vector3.Z.cpy().scl(-1f), r_uv00, r_uv10, r_uv11, r_uv01) // Back
                buildRectWithUVs(part, v_brf, v_brb, v_trb, v_trf, Vector3.X, r_uv00, r_uv10, r_uv11, r_uv01) // Right
                buildRectWithUVs(part, v_blb, v_blf, v_tlf, v_tlb, Vector3.X.cpy().scl(-1f), r_uv00, r_uv10, r_uv11, r_uv01) // Left

                modelBuilder.end()
            }
            BlockShape.PILLAR -> {
                modelBuilder.begin()
                val part = modelBuilder.part("pillar", GL20.GL_TRIANGLES, attributes, material)
                val half = internalBlockSize / 2f
                val topY = half
                val bottomY = -half
                val cornerOffset = half * 0.414f
                val topVerts = arrayOf(
                    Vector3(-half + cornerOffset, topY, -half), Vector3(half - cornerOffset,  topY, -half),
                    Vector3(half,                 topY, -half + cornerOffset), Vector3(half,                 topY, half - cornerOffset),
                    Vector3(half - cornerOffset,  topY, half), Vector3(-half + cornerOffset, topY, half),
                    Vector3(-half,                topY, half - cornerOffset), Vector3(-half,                topY, -half + cornerOffset)
                )
                val bottomVerts = topVerts.map { it.cpy().set(it.x, bottomY, it.z) }.toTypedArray()
                val topNormal = Vector3.Y
                val bottomNormal = Vector3.Y.cpy().scl(-1f)

                // TOP FACE
                part.triangle(part.vertex(topVerts[0], topNormal, null, Vector2(0.293f, 0f)), part.vertex(topVerts[2], topNormal, null, Vector2(1f, 0.293f)), part.vertex(topVerts[1], topNormal, null, Vector2(0.707f, 0f)))
                part.triangle(part.vertex(topVerts[0], topNormal, null, Vector2(0.293f, 0f)), part.vertex(topVerts[7], topNormal, null, Vector2(0f, 0.293f)), part.vertex(topVerts[2], topNormal, null, Vector2(1f, 0.293f)))
                part.triangle(part.vertex(topVerts[7], topNormal, null, Vector2(0f, 0.293f)), part.vertex(topVerts[6], topNormal, null, Vector2(0f, 0.707f)), part.vertex(topVerts[2], topNormal, null, Vector2(1f, 0.293f)))
                part.triangle(part.vertex(topVerts[6], topNormal, null, Vector2(0f, 0.707f)), part.vertex(topVerts[3], topNormal, null, Vector2(1f, 0.707f)), part.vertex(topVerts[2], topNormal, null, Vector2(1f, 0.293f)))
                part.triangle(part.vertex(topVerts[6], topNormal, null, Vector2(0f, 0.707f)), part.vertex(topVerts[5], topNormal, null, Vector2(0.293f, 1f)), part.vertex(topVerts[3], topNormal, null, Vector2(1f, 0.707f)))
                part.triangle(part.vertex(topVerts[5], topNormal, null, Vector2(0.293f, 1f)), part.vertex(topVerts[4], topNormal, null, Vector2(0.707f, 1f)), part.vertex(topVerts[3], topNormal, null, Vector2(1f, 0.707f)))

                // BOTTOM FACE
                part.triangle(part.vertex(bottomVerts[0], bottomNormal, null, Vector2(0.293f, 0f)), part.vertex(bottomVerts[2], bottomNormal, null, Vector2(1f, 0.293f)), part.vertex(bottomVerts[1], bottomNormal, null, Vector2(0.707f, 0f)))
                part.triangle(part.vertex(bottomVerts[0], bottomNormal, null, Vector2(0.293f, 0f)), part.vertex(bottomVerts[7], bottomNormal, null, Vector2(0f, 0.293f)), part.vertex(bottomVerts[2], bottomNormal, null, Vector2(1f, 0.293f)))
                part.triangle(part.vertex(bottomVerts[7], bottomNormal, null, Vector2(0f, 0.293f)), part.vertex(bottomVerts[6], bottomNormal, null, Vector2(0f, 0.707f)), part.vertex(bottomVerts[2], bottomNormal, null, Vector2(1f, 0.293f)))
                part.triangle(part.vertex(bottomVerts[6], bottomNormal, null, Vector2(0f, 0.707f)), part.vertex(bottomVerts[3], bottomNormal, null, Vector2(1f, 0.707f)), part.vertex(bottomVerts[2], bottomNormal, null, Vector2(1f, 0.293f)))
                part.triangle(part.vertex(bottomVerts[6], bottomNormal, null, Vector2(0f, 0.707f)), part.vertex(bottomVerts[5], bottomNormal, null, Vector2(0.293f, 1f)), part.vertex(bottomVerts[3], bottomNormal, null, Vector2(1f, 0.707f)))
                part.triangle(part.vertex(bottomVerts[5], bottomNormal, null, Vector2(0.293f, 1f)), part.vertex(bottomVerts[4], bottomNormal, null, Vector2(0.707f, 1f)), part.vertex(bottomVerts[3], bottomNormal, null, Vector2(1f, 0.707f)))

                for (i in 0 until 8) {
                    val p1 = bottomVerts[i]
                    val p2 = bottomVerts[(i + 1) % 8]
                    val p3 = topVerts[(i + 1) % 8]
                    val p4 = topVerts[i]

                    // Calculate the outward-facing normal properly
                    val edge1 = Vector3(p2).sub(p1)  // Bottom edge
                    val edge2 = Vector3(p4).sub(p1)  // Vertical edge
                    val normal = edge1.crs(edge2).nor()
                    part.rect(p1, p4, p3, p2, normal)
                }
                modelBuilder.end()
            }
            BlockShape.WEDGE -> {
                modelBuilder.begin()
                val part = modelBuilder.part("model", GL20.GL_TRIANGLES, attributes, material)
                val half = internalBlockSize / 2f
                val v0 = Vector3(-half, -half, +half); val v1 = Vector3(+half, -half, +half)
                val v2 = Vector3(-half, +half, +half); val v3 = Vector3(+half, +half, +half)
                val v4 = Vector3(-half, -half, -half); val v5 = Vector3(+half, -half, -half)
                part.rect(v4, v5, v1, v0, Vector3(0f, -1f, 0f))
                part.rect(v0, v1, v3, v2, Vector3(0f, 0f, 1f))
                val l1 = part.vertex(v4, Vector3(-1f, 0f, 0f), null, Vector2(0f, 0f))
                val l2 = part.vertex(v0, Vector3(-1f, 0f, 0f), null, Vector2(1f, 0f))
                val l3 = part.vertex(v2, Vector3(-1f, 0f, 0f), null, Vector2(1f, 1f))
                part.triangle(l1, l2, l3)
                val r1 = part.vertex(v1, Vector3(1f, 0f, 0f), null, Vector2(0f, 0f))
                val r2 = part.vertex(v5, Vector3(1f, 0f, 0f), null, Vector2(1f, 0f))
                val r3 = part.vertex(v3, Vector3(1f, 0f, 0f), null, Vector2(0f, 1f))
                part.triangle(r1, r2, r3)

                // Sloped top face (built with two textured triangles)
                val slopeNormal = Vector3(v2).sub(v4).crs(Vector3(v5).sub(v4)).nor()
                val s_v2 = part.vertex(v2, slopeNormal, null, Vector2(0f, 1f))
                val s_v3 = part.vertex(v3, slopeNormal, null, Vector2(1f, 1f))
                val s_v4 = part.vertex(v4, slopeNormal, null, Vector2(0f, 0f))
                val s_v5 = part.vertex(v5, slopeNormal, null, Vector2(1f, 0f))
                part.triangle(s_v2, s_v3, s_v5)
                part.triangle(s_v2, s_v5, s_v4)

                modelBuilder.end()
            }
            BlockShape.CORNER_WEDGE -> {
                modelBuilder.begin()
                val part = modelBuilder.part("c_wedge_${textureRotation.toInt()}", GL20.GL_TRIANGLES, attributes, material)
                val half = internalBlockSize / 2f
                val slabHeight = internalBlockSize
                val v_bottom_corner = Vector3(-half, -half, -half); val v_bottom_x = Vector3(half, -half, -half)
                val v_bottom_z = Vector3(-half, -half, half); val topY = -half + slabHeight
                val v_top_corner = Vector3(-half, topY, -half); val v_top_x = Vector3(half, topY, -half)
                val v_top_z = Vector3(-half, topY, half)

                // Bottom face (triangle) - Standard UVs
                val b1 = part.vertex(v_bottom_corner, Vector3.Y.cpy().scl(-1f), null, Vector2(0f, 0f))
                val b2 = part.vertex(v_bottom_x, Vector3.Y.cpy().scl(-1f), null, Vector2(1f, 0f))
                val b3 = part.vertex(v_bottom_z, Vector3.Y.cpy().scl(-1f), null, Vector2(0f, 1f))
                part.triangle(b1, b2, b3)

                // Top face (triangle) - Standard UVs
                val t1 = part.vertex(v_top_corner, Vector3.Y, null, Vector2(0f, 0f))
                val t2 = part.vertex(v_top_z, Vector3.Y, null, Vector2(0f, 1f))
                val t3 = part.vertex(v_top_x, Vector3.Y, null, Vector2(1f, 0f))
                part.triangle(t1, t2, t3)

                // Back face (rectangle) - Rotated UVs
                buildRectWithUVs(part, v_bottom_x, v_bottom_corner, v_top_corner, v_top_x, Vector3(0f, 0f, -1f), r_uv00, r_uv10, r_uv11, r_uv01)

                // Left face (rectangle) - Rotated UVs
                buildRectWithUVs(part, v_bottom_corner, v_bottom_z, v_top_z, v_top_corner, Vector3(-1f, 0f, 0f), r_uv00, r_uv10, r_uv11, r_uv01)

                // Sloped face - Rotated UVs
                val slopedNormal = Vector3(v_bottom_z).sub(v_bottom_x).crs(Vector3(v_top_x).sub(v_bottom_x)).nor()
                buildRectWithUVs(part, v_bottom_z, v_bottom_x, v_top_x, v_top_z, slopedNormal, r_uv00, r_uv10, r_uv11, r_uv01)

                modelBuilder.end()
            }
        }
    }

    fun rotateCurrentBlock() {
        if (rotationMode == BlockRotationMode.GEOMETRY) {
            currentGeometryRotation = (currentGeometryRotation + 90f) % 360f
        } else {
            val supportedShapes = listOf(BlockShape.FULL_BLOCK, BlockShape.VERTICAL_SLAB, BlockShape.CORNER_WEDGE)
            if (currentSelectedShape in supportedShapes) {
                currentTextureRotation = (currentTextureRotation + 90f) % 360f
            }
        }
    }
    fun rotateCurrentBlockReverse() {
        if (rotationMode == BlockRotationMode.GEOMETRY) {
            currentGeometryRotation = (currentGeometryRotation - 90f + 360f) % 360f
        } else {
            val supportedShapes = listOf(BlockShape.FULL_BLOCK, BlockShape.VERTICAL_SLAB, BlockShape.CORNER_WEDGE)
            if (currentSelectedShape in supportedShapes) {
                currentTextureRotation = (currentTextureRotation - 90f + 360f) % 360f
            }
        }
    }
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
        blockFaceModels.values.forEach { faceMap ->
            faceMap.values.forEach { rotationMap ->
                rotationMap.values.forEach { it.dispose() }
            }
        }
        customShapeModels.values.forEach { it.dispose() }
        blockTextures.values.forEach { it.dispose() }
    }
}
