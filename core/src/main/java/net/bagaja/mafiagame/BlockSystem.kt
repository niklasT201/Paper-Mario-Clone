package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class BuildMode(val size: Int, val isWall: Boolean) {
    SINGLE(1, false),
    WALL_3x3(3, true),
    WALL_5x5(5, true),
    FLOOR_3x3(3, false),
    FLOOR_4x4(4, false),
    FLOOR_5x5(5, false),
    FLOOR_8x8(8, false),
    FLOOR_16x16(16, false);

    fun getDisplayName(): String {
        return when(this) {
            SINGLE -> "1x1 Area"
            WALL_3x3 -> "3x3 Wall"
            FLOOR_3x3 -> "3x3 Floor"
            WALL_5x5 -> "5x5 Wall"
            FLOOR_4x4 -> "4x4 Floor"
            FLOOR_5x5 -> "5x5 Floor"
            FLOOR_8x8 -> "8x8 Floor"
            FLOOR_16x16 -> "16x16 Floor"
        }
    }
}

private enum class AreaFillState {
    IDLE,                  // Normal placement mode
    AWAITING_SECOND_CORNER // First corner has been placed, waiting for the second
}

// Block system class to manage different block types
class BlockSystem {
    // Caches for the two different model types
    private val blockFaceModels = mutableMapOf<BlockType, Map<BlockFace, Map<Float, Model>>>()
    private val customShapeModels = mutableMapOf<Triple<BlockType, BlockShape, Pair<Float, Float>>, Model>()
    private val waterModels = mutableMapOf<BlockType, Model>()
    private var areaFillState = AreaFillState.IDLE
    private var firstCornerGridPosition: Vector3? = null
    val isAreaFillModeActive: Boolean get() = areaFillState != AreaFillState.IDLE
    val areaFillFirstCorner: Vector3? get() = firstCornerGridPosition

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
    var currentTopTextureRotation = 0f
        private set
    var rotationMode = BlockRotationMode.GEOMETRY
        private set
    var currentBuildMode = BuildMode.SINGLE
        private set
    var currentCameraVisibility = CameraVisibility.ALWAYS_VISIBLE
        private set

    private lateinit var modelBuilder: ModelBuilder
    private var internalBlockSize: Float = 4f

    lateinit var sceneManager: SceneManager
    private lateinit var raycastSystem: RaycastSystem
    private val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()

    fun initialize(blockSize: Float) {
        this.internalBlockSize = blockSize
        modelBuilder = ModelBuilder()
        this.raycastSystem = RaycastSystem(blockSize)

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

    fun handlePlaceAction(ray: Ray) {
        // If we are in the middle of an area fill operation, handle that logic.
        if (areaFillState != AreaFillState.IDLE) {
            handleAreaFillAction(ray)
            return // IMPORTANT: Prevent the old logic from running.
        }

        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())

        if (hitBlock != null) {
            // We hit an existing block, place new block adjacent to it
            placeBlockAdjacentTo(ray, hitBlock)
        } else {
            // No block hit, place on ground
            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
                // Snap to grid
                val gridX = floor(tempVec3.x / internalBlockSize) * internalBlockSize
                val gridZ = floor(tempVec3.z / internalBlockSize) * internalBlockSize
                placeBlockArea(gridX, 0f, gridZ)
            }
        }
    }

    // NEW: Toggles the Area Fill mode on and off.
    fun toggleAreaFillMode(): String {
        return if (areaFillState == AreaFillState.IDLE) {
            areaFillState = AreaFillState.AWAITING_SECOND_CORNER // We go straight to waiting for the second corner, but the first is null
            firstCornerGridPosition = null // Ensure it's null when we start
            "Area Fill Mode: ON. Select first corner."
        } else {
            // If we are already in the mode, toggling it again cancels it.
            cancelAreaFill()
            "Area Fill Mode: OFF."
        }
    }

    // NEW: Cancels an in-progress Area Fill operation.
    fun cancelAreaFill() {
        if (areaFillState != AreaFillState.IDLE) {
            areaFillState = AreaFillState.IDLE
            firstCornerGridPosition = null
            println("Area fill cancelled.")
            sceneManager.game.uiManager.updatePlacementInfo("Area fill cancelled.")
        }
    }

    private fun handleAreaFillAction(ray: Ray) {
        val gridPosition = getGridPositionFromRay(ray) ?: return

        if (firstCornerGridPosition == null) {
            firstCornerGridPosition = gridPosition
            println("First corner set at: $gridPosition")
            sceneManager.game.uiManager.updatePlacementInfo("First corner set. Select second corner.")
            // State remains AWAITING_SECOND_CORNER
        } else {
            println("Second corner set at: $gridPosition. Filling area...")
            sceneManager.game.uiManager.updatePlacementInfo("Filling area...")

            fillAreaBetween(firstCornerGridPosition!!, gridPosition)

            firstCornerGridPosition = null
            sceneManager.game.uiManager.updatePlacementInfo("Area fill complete. Select first corner for new area.")
        }
    }

    fun getGridPositionFromRay(ray: Ray): Vector3? {
        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())
        if (hitBlock != null) {
            // Logic to place adjacent to an existing block
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, hitBlock.getBoundingBox(internalBlockSize, BoundingBox()), tempVec3)) {
                val relativePos = Vector3(tempVec3).sub(hitBlock.position)
                var newX = hitBlock.position.x; var newY = hitBlock.position.y; var newZ = hitBlock.position.z
                val absX = kotlin.math.abs(relativePos.x); val absY = kotlin.math.abs(relativePos.y); val absZ = kotlin.math.abs(relativePos.z)

                when {
                    absY >= absX && absY >= absZ -> newY += if (relativePos.y > 0) internalBlockSize else -internalBlockSize
                    absX >= absY && absX >= absZ -> newX += if (relativePos.x > 0) internalBlockSize else -internalBlockSize
                    else -> newZ += if (relativePos.z > 0) internalBlockSize else -internalBlockSize
                }
                return Vector3(
                    floor(newX / internalBlockSize) * internalBlockSize,
                    floor(newY / internalBlockSize) * internalBlockSize,
                    floor(newZ / internalBlockSize) * internalBlockSize
                )
            }
        } else {
            // Logic to place on the ground
            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
                return Vector3(
                    floor(tempVec3.x / internalBlockSize) * internalBlockSize,
                    0f, // Start at ground level for simplicity
                    floor(tempVec3.z / internalBlockSize) * internalBlockSize
                )
            }
        }
        return null
    }

    private fun fillAreaBetween(corner1: Vector3, corner2: Vector3) {
        val blockType = this.currentSelectedBlock
        val step = internalBlockSize.toInt()

        // Determine the min and max coordinates for the bounding box
        val minX = min(corner1.x, corner2.x)
        val maxX = max(corner1.x, corner2.x)
        val minY = min(corner1.y, corner2.y)
        val maxY = max(corner1.y, corner2.y)
        val minZ = min(corner1.z, corner2.z)
        val maxZ = max(corner1.z, corner2.z)

        var blocksPlaced = 0
        // Loop through every grid cell within the bounding box
        for (x in minX.toInt()..maxX.toInt() step step) {
            for (y in minY.toInt()..maxY.toInt() step step) {
                for (z in minZ.toInt()..maxZ.toInt() step step) {
                    val placePosition = Vector3(
                        x + internalBlockSize / 2f,
                        y + (internalBlockSize * blockType.height) / 2f,
                        z + internalBlockSize / 2f
                    )
                    // Check if a block already exists to avoid duplicates
                    if (sceneManager.activeChunkManager.getBlockAtWorld(placePosition) == null) {
                        addBlock(x.toFloat(), y.toFloat(), z.toFloat(), blockType)
                        blocksPlaced++
                    }
                }
            }
        }
        println("Area fill complete. Placed $blocksPlaced new blocks.")
    }

    fun handleRemoveAction(ray: Ray): Boolean {
        val blockToRemove = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())
        if (blockToRemove != null) {
            removeBlockArea(blockToRemove)
            return true
        }
        return false
    }

    private fun placeBlockAdjacentTo(ray: Ray, hitBlock: GameBlock) {
        // Calculate intersection point with the hit block
        val blockBounds = BoundingBox()
        hitBlock.getBoundingBox(internalBlockSize, blockBounds)

        if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, blockBounds, tempVec3)) {
            // Determine which face was hit by finding the closest face
            val relativePos = Vector3(tempVec3).sub(hitBlock.position)
            var newX = hitBlock.position.x
            var newY = hitBlock.position.y
            var newZ = hitBlock.position.z

            // Find the dominant axis (which face was hit)
            val absX = kotlin.math.abs(relativePos.x)
            val absY = kotlin.math.abs(relativePos.y)
            val absZ = kotlin.math.abs(relativePos.z)

            when {
                // Hit top or bottom face
                absY >= absX && absY >= absZ -> newY += if (relativePos.y > 0) internalBlockSize else -internalBlockSize
                // Hit left or right face
                absX >= absY && absX >= absZ -> newX += if (relativePos.x > 0) internalBlockSize else -internalBlockSize
                // Hit front or back face
                else -> newZ += if (relativePos.z > 0) internalBlockSize else -internalBlockSize
            }
            val gridX = floor(newX / internalBlockSize) * internalBlockSize
            val gridY = floor(newY / internalBlockSize) * internalBlockSize
            val gridZ = floor(newZ / internalBlockSize) * internalBlockSize

            placeBlockArea(gridX, gridY, gridZ)
        }
    }

    private fun placeBlockArea(cornerX: Float, cornerY: Float, cornerZ: Float) {
        val buildMode = this.currentBuildMode
        val blockType = this.currentSelectedBlock
        val size = buildMode.size

        // If it's just a 1x1 area
        if (size == 1) {
            val position = Vector3(cornerX + internalBlockSize / 2, cornerY + (internalBlockSize * blockType.height) / 2, cornerZ + internalBlockSize / 2)
            // Check if block already exists at this position
            val existingBlock = sceneManager.activeChunkManager.getAllBlocks().find { gameBlock ->
                // This check might need to be more robust for different shapes and sizes
                gameBlock.position.dst(position) < 0.1f
            }
            if (existingBlock == null) {
                addBlock(cornerX, cornerY, cornerZ, blockType)
                println("${blockType.displayName} (${this.currentSelectedShape.getDisplayName()}) placed at: $cornerX, $cornerY, $cornerZ")
            } else {
                println("Block already exists at this position")
            }
            return
        }

        // For 3x3 or 5x5 areas
        val offset = (size - 1) / 2
        val isWall = buildMode.isWall

        for (i in 0 until size) {
            for (j in 0 until size) {
                // Calculate the grid coordinates (bottom-left corner) for the current block in the area
                val currentBlockX: Float
                val currentBlockY: Float
                val currentBlockZ: Float

                if (isWall) {
                    // Placing a wall (X, Y plane). The provided corner is the center block's corner.
                    currentBlockX = cornerX + (i - offset) * internalBlockSize
                    currentBlockY = cornerY + (j - offset) * internalBlockSize
                    currentBlockZ = cornerZ
                } else {
                    // Placing a floor (X, Z plane)
                    currentBlockX = cornerX + (i - offset) * internalBlockSize
                    currentBlockY = cornerY
                    currentBlockZ = cornerZ + (j - offset) * internalBlockSize
                }

                // Calculate the center position for the existence check
                val checkPosition = Vector3(
                    currentBlockX + internalBlockSize / 2,
                    currentBlockY + (internalBlockSize * blockType.height) / 2,
                    currentBlockZ + internalBlockSize / 2
                )

                // Check if a block already exists at this position
                val existingBlock = sceneManager.activeChunkManager.getAllBlocks().find { gameBlock ->
                    gameBlock.position.dst(checkPosition) < 0.1f
                }

                if (existingBlock == null) {
                    // Pass the calculated corner coordinates to addBlock
                    addBlock(currentBlockX, currentBlockY, currentBlockZ, blockType)
                }
            }
        }
        val areaType = if (isWall) "Wall" else "Floor"
        println("${blockType.displayName} $size x $size $areaType placed around center corner: $cornerX, $cornerY, $cornerZ")
    }

    private fun removeBlockArea(centerBlock: GameBlock) {
        val buildMode = this.currentBuildMode
        val size = buildMode.size

        // If it's just a 1x1 area
        if (size == 1) {
            removeBlock(centerBlock)
            return
        }

        // For 3x3 or 5x5 areas
        val blocksToRemoveList = mutableListOf<GameBlock>()
        val centerPos = centerBlock.position
        val offset = (size - 1) / 2
        val isWall = buildMode.isWall

        for (i in 0 until size) {
            for (j in 0 until size) {
                // Calculate the target center position for each block in the area
                val targetX: Float
                val targetY: Float
                val targetZ: Float

                if (isWall) {
                    // Removing a wall area (X, Y plane), centered on the block that was clicked
                    targetX = centerPos.x + (i - offset) * internalBlockSize
                    targetY = centerPos.y + (j - offset) * internalBlockSize
                    targetZ = centerPos.z
                } else {
                    // Removing a floor area (X, Z plane)
                    targetX = centerPos.x + (i - offset) * internalBlockSize
                    targetY = centerPos.y
                    targetZ = centerPos.z + (j - offset) * internalBlockSize
                }
                val targetPos = Vector3(targetX, targetY, targetZ)

                // Find if a block exists at this target position
                val blockFound = sceneManager.activeChunkManager.getBlockAtWorld(targetPos)
                if (blockFound != null) {
                    blocksToRemoveList.add(blockFound)
                }
            }
        }

        if (blocksToRemoveList.isNotEmpty()) {
            for (blockToRemove in blocksToRemoveList) {
                removeBlock(blockToRemove)
            }
            println("Removed ${blocksToRemoveList.size} blocks in a $size x $size area.")
        }
    }

    private fun addBlock(x: Float, y: Float, z: Float, blockType: BlockType) {
        val shape = this.currentSelectedShape
        val blockHeight = internalBlockSize * blockType.height

        val position = when {
            blockType == BlockType.WATER -> Vector3(x + internalBlockSize / 2, y + blockHeight, z + internalBlockSize / 2)
            shape == BlockShape.SLAB_BOTTOM -> Vector3(x + internalBlockSize / 2, y + blockHeight / 4, z + internalBlockSize / 2)
            shape == BlockShape.SLAB_TOP -> Vector3(x + internalBlockSize / 2, y + (blockHeight * 0.75f), z + internalBlockSize / 2)
            else -> Vector3(x + internalBlockSize / 2, y + blockHeight / 2, z + internalBlockSize / 2)
        }

        val gameBlock = this.createGameBlock(
            type = blockType,
            shape = shape,
            position = position,
            geometryRotation = this.currentGeometryRotation,
            textureRotation = this.currentTextureRotation,
            topTextureRotation = this.currentTopTextureRotation
        )

        // The only call needed now
        sceneManager.addBlock(gameBlock)
    }

    private fun removeBlock(blockToRemove: GameBlock) {
        sceneManager.removeBlock(blockToRemove)
        println("${blockToRemove.blockType.displayName} block removed at: ${blockToRemove.position}")
    }

    fun toggleRotationMode() {
        rotationMode = when (rotationMode) {
            BlockRotationMode.GEOMETRY -> BlockRotationMode.TEXTURE_SIDES
            BlockRotationMode.TEXTURE_SIDES -> BlockRotationMode.TEXTURE_TOP
            BlockRotationMode.TEXTURE_TOP -> BlockRotationMode.GEOMETRY
        }
        println("Block rotation mode set to: ${rotationMode.getDisplayName()}")
    }

    fun createGameBlock(type: BlockType, shape: BlockShape, position: Vector3, geometryRotation: Float, textureRotation: Float, topTextureRotation: Float): GameBlock {
        if (type == BlockType.WATER) {
            // Water ignores selected shape and always uses a single top plane
            val material = Material(
                TextureAttribute.createDiffuse(blockTextures[type]),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.7f)
            )
            val waterModel = getOrCreateWaterModel(type, material)
            val modelInstance = ModelInstance(waterModel!!)

            // return a block with a modelInstance, not faceInstances
            return GameBlock(
                type,
                BlockShape.SLAB_BOTTOM, // Using a non-FULL_BLOCK shape ensures the correct render path
                position,
                rotationY = geometryRotation,
                textureRotationY = textureRotation,
                topTextureRotationY = topTextureRotation,
                modelInstance = modelInstance
            )
        }

        return if (shape == BlockShape.FULL_BLOCK) {
            val faceInstances = createFaceInstances(type, textureRotation, topTextureRotation)!!
            GameBlock(
                type,
                shape,
                position,
                rotationY = geometryRotation,
                textureRotationY = textureRotation,
                topTextureRotationY = topTextureRotation,
                cameraVisibility = currentCameraVisibility,
                faceInstances = faceInstances
            )
        } else {
            val modelInstance = createCustomShapeInstance(type, shape, textureRotation, topTextureRotation)!!
            GameBlock(
                type,
                shape,
                position,
                rotationY = geometryRotation,
                textureRotationY = textureRotation,
                topTextureRotationY = topTextureRotation,
                cameraVisibility = currentCameraVisibility,
                modelInstance = modelInstance
            )
        }
    }

    private fun createFaceModelsForBlockType(blockType: BlockType, blockSize: Float) {
        val material: Material = if (blockType == BlockType.WATER) {
            Material(
                TextureAttribute.createDiffuse(blockTextures[blockType]),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.7f) // 0.7f opacity
            )
        } else {
            blockTextures[blockType]?.let {
                Material(TextureAttribute.createDiffuse(it))
            } ?: Material(ColorAttribute.createDiffuse(Color.MAGENTA))
        }

        val modelsForBlock = mutableMapOf<BlockFace, Map<Float, Model>>()
        val rotations = listOf(0f, 90f, 180f, 270f)

        for (face in BlockFace.entries) {
            val modelsForFace = mutableMapOf<Float, Model>()
            if (face == BlockFace.BOTTOM) {
                // Bottom face is not rotated.
                modelsForFace[0f] = createFaceModel(blockSize, blockType.height, material, face, 0f)
            } else {
                // Top and Side faces need a model for each rotation angle.
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

    private fun createFaceInstances(blockType: BlockType, textureRotation: Float, topTextureRotation: Float): Map<BlockFace, ModelInstance>? {
        val typeModels = blockFaceModels[blockType] ?: return null
        val instances = mutableMapOf<BlockFace, ModelInstance>()

        for (face in BlockFace.entries) {
            val faceModels = typeModels[face] ?: continue
            val model = when (face) {
                BlockFace.TOP -> faceModels[topTextureRotation] // Use top rotation for top face
                BlockFace.BOTTOM -> faceModels[0f] // Bottom face is never rotated
                else -> faceModels[textureRotation] // Side faces use the side texture rotation
            }
            if (model != null) {
                instances[face] = ModelInstance(model)
            }
        }
        return instances
    }

    private fun createCustomShapeInstance(blockType: BlockType, shape: BlockShape, textureRotation: Float, topTextureRotation: Float): ModelInstance? {
        val model = getOrCreateModel(blockType, shape, textureRotation, topTextureRotation)
        return model?.let { ModelInstance(it) }
    }

    private fun getOrCreateModel(blockType: BlockType, shape: BlockShape, textureRotation: Float, topTextureRotation: Float): Model? {
        val key = Triple(blockType, shape, Pair(textureRotation, topTextureRotation))
        if (!customShapeModels.containsKey(key)) {
            val material = blockTextures[blockType]?.let {
                Material(TextureAttribute.createDiffuse(it))
            } ?: Material(ColorAttribute.createDiffuse(Color.MAGENTA))

            val newModel = createCustomModel(blockType, shape, material, textureRotation, topTextureRotation)
            if (newModel != null) {
                customShapeModels[key] = newModel
            }
        }
        return customShapeModels[key]
    }

    private fun createCustomModel(blockType: BlockType, shape: BlockShape, material: Material, textureRotation: Float, topTextureRotation: Float): Model? {
        val attributes = (VertexAttributes.Usage.Position or
            VertexAttributes.Usage.Normal or
            VertexAttributes.Usage.TextureCoordinates).toLong()

        val uv00 = Vector2(0f, 1f); val uv10 = Vector2(1f, 1f)
        val uv11 = Vector2(1f, 0f); val uv01 = Vector2(0f, 0f)

        // Calculate rotated UVs for side faces
        val side_uvs = arrayOf(uv00, uv10, uv11, uv01)
        val side_rotationSteps = (textureRotation / 90f).toInt().let { if (it < 0) it + 4 else it } % 4
        val r_uv00_side = side_uvs[(0 + side_rotationSteps) % 4]
        val r_uv10_side = side_uvs[(1 + side_rotationSteps) % 4]
        val r_uv11_side = side_uvs[(2 + side_rotationSteps) % 4]
        val r_uv01_side = side_uvs[(3 + side_rotationSteps) % 4]

        // Calculate rotated UVs for top face
        val top_uvs = arrayOf(uv00, uv10, uv11, uv01)
        val top_rotationSteps = (topTextureRotation / 90f).toInt().let { if (it < 0) it + 4 else it } % 4
        val r_uv00_top = top_uvs[(0 + top_rotationSteps) % 4]
        val r_uv10_top = top_uvs[(1 + top_rotationSteps) % 4]
        val r_uv11_top = top_uvs[(2 + top_rotationSteps) % 4]
        val r_uv01_top = top_uvs[(3 + top_rotationSteps) % 4]


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
                modelBuilder.createBox(internalBlockSize, internalBlockSize * blockType.height, internalBlockSize, material, attributes)
            }
            BlockShape.SLAB_BOTTOM, BlockShape.SLAB_TOP -> {
                modelBuilder.createBox(internalBlockSize, (internalBlockSize * blockType.height) / 2f, internalBlockSize, material, attributes)
            }
            BlockShape.VERTICAL_SLAB -> {
                modelBuilder.begin()
                val part = modelBuilder.part("v_slab_${textureRotation.toInt()}_${topTextureRotation.toInt()}",
                    GL20.GL_TRIANGLES, attributes, material)
                val hx = internalBlockSize / 2f; val hy = (internalBlockSize * blockType.height) / 2f; val hz = internalBlockSize / 4f

                val v_blf = Vector3(-hx, -hy,  hz); val v_brf = Vector3( hx, -hy,  hz)
                val v_trf = Vector3( hx,  hy,  hz); val v_tlf = Vector3(-hx,  hy,  hz)
                val v_blb = Vector3(-hx, -hy, -hz); val v_brb = Vector3( hx, -hy, -hz)
                val v_trb = Vector3( hx,  hy, -hz); val v_tlb = Vector3(-hx,  hy, -hz)

                // Top face uses rotated top UVs. Bottom face uses standard UVs.
                buildRectWithUVs(part, v_tlf, v_trf, v_trb, v_tlb, Vector3.Y, r_uv00_top, r_uv10_top, r_uv11_top, r_uv01_top)
                buildRectWithUVs(part, v_blb, v_brb, v_brf, v_blf, Vector3.Y.cpy().scl(-1f), uv00, uv10, uv11, uv01)

                // Sides use rotated side UVs.
                buildRectWithUVs(part, v_blf, v_brf, v_trf, v_tlf, Vector3.Z, r_uv00_side, r_uv10_side, r_uv11_side, r_uv01_side) // Front
                buildRectWithUVs(part, v_brb, v_blb, v_tlb, v_trb, Vector3.Z.cpy().scl(-1f), r_uv00_side, r_uv10_side, r_uv11_side, r_uv01_side) // Back
                buildRectWithUVs(part, v_brf, v_brb, v_trb, v_trf, Vector3.X, r_uv00_side, r_uv10_side, r_uv11_side, r_uv01_side) // Right
                buildRectWithUVs(part, v_blb, v_blf, v_tlf, v_tlb, Vector3.X.cpy().scl(-1f), r_uv00_side, r_uv10_side, r_uv11_side, r_uv01_side) // Left

                modelBuilder.end()
            }
            BlockShape.PILLAR -> {
                modelBuilder.begin()
                val part = modelBuilder.part("pillar", GL20.GL_TRIANGLES, attributes, material)
                val half = internalBlockSize / 2f
                val topY = (internalBlockSize * blockType.height) / 2f
                val bottomY = -topY
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
                val halfHeight = (internalBlockSize * blockType.height) / 2f
                val v0 = Vector3(-half, -halfHeight, +half); val v1 = Vector3(+half, -halfHeight, +half)
                val v2 = Vector3(-half, +halfHeight, +half); val v3 = Vector3(+half, +halfHeight, +half)
                val v4 = Vector3(-half, -halfHeight, -half); val v5 = Vector3(+half, -halfHeight, -half)
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
                val part = modelBuilder.part("c_wedge_${textureRotation.toInt()}_${topTextureRotation.toInt()}", GL20.GL_TRIANGLES, attributes, material)
                val half = internalBlockSize / 2f
                val bottomY = -(internalBlockSize * blockType.height) / 2f
                val topY = (internalBlockSize * blockType.height) / 2f

                val v_bottom_corner = Vector3(-half, bottomY, -half); val v_bottom_x = Vector3(half, bottomY, -half)
                val v_bottom_z = Vector3(-half, bottomY, half)
                val v_top_corner = Vector3(-half, topY, -half); val v_top_x = Vector3(half, topY, -half)
                val v_top_z = Vector3(-half, topY, half)

                // Bottom face (triangle)
                val b1 = part.vertex(v_bottom_corner, Vector3.Y.cpy().scl(-1f), null, Vector2(0f, 0f))
                val b2 = part.vertex(v_bottom_x, Vector3.Y.cpy().scl(-1f), null, Vector2(1f, 0f))
                val b3 = part.vertex(v_bottom_z, Vector3.Y.cpy().scl(-1f), null, Vector2(0f, 1f))
                part.triangle(b1, b3, b2)

                // Top face (triangle) - Rotated UVs for the top
                val top_uv_coords = arrayOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f))
                val steps = (topTextureRotation / 90f).toInt().let { if (it < 0) it + 4 else it } % 4
                val uv_corner = top_uv_coords[(0 + steps) % 4]
                val uv_x_axis = top_uv_coords[(1 + steps) % 4]
                val uv_z_axis = top_uv_coords[(3 + steps) % 4]

                val t1 = part.vertex(v_top_corner, Vector3.Y, null, uv_corner)
                val t2 = part.vertex(v_top_z, Vector3.Y, null, uv_z_axis)
                val t3 = part.vertex(v_top_x, Vector3.Y, null, uv_x_axis)
                part.triangle(t1, t2, t3)

                // Back face (rectangle)
                buildRectWithUVs(part, v_bottom_x, v_bottom_corner, v_top_corner, v_top_x, Vector3(0f, 0f, -1f), r_uv00_side, r_uv10_side, r_uv11_side, r_uv01_side)
                // Left face (rectangle)
                buildRectWithUVs(part, v_bottom_corner, v_bottom_z, v_top_z, v_top_corner, Vector3(-1f, 0f, 0f), r_uv00_side, r_uv10_side, r_uv11_side, r_uv01_side)

                // Sloped face - Corrected UV mapping for proper texturing
                val slopedNormal = Vector3(v_bottom_z).sub(v_bottom_x).crs(Vector3(v_top_x).sub(v_bottom_x)).nor()
                val s1 = part.vertex(v_bottom_z, slopedNormal, null, r_uv00_side)
                val s2 = part.vertex(v_bottom_x, slopedNormal, null, r_uv10_side)
                val s3 = part.vertex(v_top_x, slopedNormal, null, r_uv11_side)
                val s4 = part.vertex(v_top_z, slopedNormal, null, r_uv01_side)
                part.triangle(s1, s2, s3)
                part.triangle(s3, s4, s1)

                modelBuilder.end()
            }
            BlockShape.PLANE_TOP,
            BlockShape.PLANE_BOTTOM,
            BlockShape.PLANE_FRONT,
            BlockShape.PLANE_BACK,
            BlockShape.PLANE_LEFT,
            BlockShape.PLANE_RIGHT -> {
                modelBuilder.begin()
                val part = modelBuilder.part(
                    "${shape.name}_${textureRotation.toInt()}_${topTextureRotation.toInt()}",
                    GL20.GL_TRIANGLES, attributes, material
                )

                val halfSize = internalBlockSize / 2f
                val halfHeight = (internalBlockSize * blockType.height) / 2f

                // Define the UV arrays based on rotation
                val uvs_top = arrayOf(r_uv00_top, r_uv10_top, r_uv11_top, r_uv01_top)
                val uvs_sides = arrayOf(r_uv00_side, r_uv10_side, r_uv11_side, r_uv01_side)
                val uvs_bottom = arrayOf(uv00, uv10, uv11, uv01) // Bottom face doesn't rotate texture

                // Select the correct UV set for the current plane shape
                val final_uvs = when (shape) {
                    BlockShape.PLANE_TOP -> uvs_top
                    BlockShape.PLANE_BOTTOM -> uvs_bottom
                    else -> uvs_sides
                }

                // Build the rect using the selected UVs
                when (shape) {
                    BlockShape.PLANE_TOP -> buildRectWithUVs(part,
                        Vector3(-halfSize, halfHeight, halfSize), Vector3(halfSize, halfHeight, halfSize),
                        Vector3(halfSize, halfHeight, -halfSize), Vector3(-halfSize, halfHeight, -halfSize),
                        Vector3(0f, 1f, 0f), final_uvs[0], final_uvs[1], final_uvs[2], final_uvs[3])
                    BlockShape.PLANE_BOTTOM -> buildRectWithUVs(part,
                        Vector3(-halfSize, -halfHeight, -halfSize), Vector3(halfSize, -halfHeight, -halfSize),
                        Vector3(halfSize, -halfHeight, halfSize), Vector3(-halfSize, -halfHeight, halfSize),
                        Vector3(0f, -1f, 0f), final_uvs[0], final_uvs[1], final_uvs[2], final_uvs[3])
                    BlockShape.PLANE_FRONT -> buildRectWithUVs(part,
                        Vector3(-halfSize, -halfHeight, halfSize), Vector3(halfSize, -halfHeight, halfSize),
                        Vector3(halfSize, halfHeight, halfSize), Vector3(-halfSize, halfHeight, halfSize),
                        Vector3(0f, 0f, 1f), final_uvs[0], final_uvs[1], final_uvs[2], final_uvs[3])
                    BlockShape.PLANE_BACK -> buildRectWithUVs(part,
                        Vector3(halfSize, -halfHeight, -halfSize), Vector3(-halfSize, -halfHeight, -halfSize),
                        Vector3(-halfSize, halfHeight, -halfSize), Vector3(halfSize, halfHeight, -halfSize),
                        Vector3(0f, 0f, -1f), final_uvs[0], final_uvs[1], final_uvs[2], final_uvs[3])
                    BlockShape.PLANE_LEFT -> buildRectWithUVs(part,
                        Vector3(-halfSize, -halfHeight, -halfSize), Vector3(-halfSize, -halfHeight, halfSize),
                        Vector3(-halfSize, halfHeight, halfSize), Vector3(-halfSize, halfHeight, -halfSize),
                        Vector3(-1f, 0f, 0f), final_uvs[0], final_uvs[1], final_uvs[2], final_uvs[3])
                    BlockShape.PLANE_RIGHT -> buildRectWithUVs(part,
                        Vector3(halfSize, -halfHeight, halfSize), Vector3(halfSize, -halfHeight, -halfSize),
                        Vector3(halfSize, halfHeight, -halfSize), Vector3(halfSize, halfHeight, halfSize),
                        Vector3(1f, 0f, 0f), final_uvs[0], final_uvs[1], final_uvs[2], final_uvs[3])
                    else -> {}
                }
                modelBuilder.end()
            }
        }
    }

    private fun getOrCreateWaterModel(blockType: BlockType, material: Material): Model? {
        if (!waterModels.containsKey(blockType)) {
            modelBuilder.begin()
            val partBuilder = modelBuilder.part(
                "water_top",
                GL20.GL_TRIANGLES,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong(),
                material
            )

            val halfSize = internalBlockSize / 2f
            val corner1 = Vector3(-halfSize, 0f, -halfSize) // Front-left
            val corner2 = Vector3(halfSize, 0f, -halfSize)  // Front-right
            val corner3 = Vector3(halfSize, 0f, halfSize)   // Back-right
            val corner4 = Vector3(-halfSize, 0f, halfSize)  // Back-left

            // The normal vector points straight up (0, 1, 0)
            val normal = Vector3(0f, 1f, 0f)

            // UV coordinates for the texture
            val uv1 = Vector2(0f, 0f)
            val uv2 = Vector2(1f, 0f)
            val uv3 = Vector2(1f, 1f)
            val uv4 = Vector2(0f, 1f)

            // Build the rectangle from two triangles
            partBuilder.rect(corner1, corner4, corner3, corner2, normal)

            waterModels[blockType] = modelBuilder.end()
        }
        return waterModels[blockType]
    }

    fun rotateCurrentBlock() {
        when (rotationMode) {
            BlockRotationMode.GEOMETRY -> {
                currentGeometryRotation = (currentGeometryRotation + 90f) % 360f
            }
            BlockRotationMode.TEXTURE_SIDES -> {
                val supportedShapes = listOf(BlockShape.FULL_BLOCK, BlockShape.VERTICAL_SLAB, BlockShape.CORNER_WEDGE)
                if (currentSelectedShape in supportedShapes) {
                    currentTextureRotation = (currentTextureRotation + 90f) % 360f
                }
            }
            BlockRotationMode.TEXTURE_TOP -> {
                val supportedShapes = listOf(BlockShape.FULL_BLOCK, BlockShape.VERTICAL_SLAB, BlockShape.CORNER_WEDGE)
                if (currentSelectedShape in supportedShapes) {
                    currentTopTextureRotation = (currentTopTextureRotation + 90f) % 360f
                }
            }
        }
    }

    fun rotateCurrentBlockReverse() {
        when (rotationMode) {
            BlockRotationMode.GEOMETRY -> {
                currentGeometryRotation = (currentGeometryRotation - 90f + 360f) % 360f
            }
            BlockRotationMode.TEXTURE_SIDES -> {
                val supportedShapes = listOf(BlockShape.FULL_BLOCK, BlockShape.VERTICAL_SLAB, BlockShape.CORNER_WEDGE)
                if (currentSelectedShape in supportedShapes) {
                    currentTextureRotation = (currentTextureRotation - 90f + 360f) % 360f
                }
            }
            BlockRotationMode.TEXTURE_TOP -> {
                val supportedShapes = listOf(BlockShape.FULL_BLOCK, BlockShape.VERTICAL_SLAB, BlockShape.CORNER_WEDGE)
                if (currentSelectedShape in supportedShapes) {
                    currentTopTextureRotation = (currentTopTextureRotation - 90f + 360f) % 360f
                }
            }
        }
    }

    fun nextCameraVisibility() {
        val visibilities = CameraVisibility.entries.toTypedArray()

        // Find the index of the current selection
        val currentIndex = visibilities.indexOf(currentCameraVisibility)

        // Calculate the next index, wrapping around using the modulo operator
        val nextIndex = (currentIndex + 1) % visibilities.size

        // Set the new current visibility mode
        currentCameraVisibility = visibilities[nextIndex]

        println("Block camera visibility set to: ${currentCameraVisibility.getDisplayName()}")
    }

    fun nextBuildMode() {
        val modes = BuildMode.entries.toTypedArray()
        currentBuildMode = modes[(modes.indexOf(currentBuildMode) + 1) % modes.size]
        println("Build mode set to: ${currentBuildMode.getDisplayName()}")
    }

    fun previousBuildMode() {
        val modes = BuildMode.entries.toTypedArray()
        val currentIndex = modes.indexOf(currentBuildMode)
        currentBuildMode = if (currentIndex > 0) modes[currentIndex - 1] else modes.last()
        println("Build mode set to: ${currentBuildMode.getDisplayName()}")
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
