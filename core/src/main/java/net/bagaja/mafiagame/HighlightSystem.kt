package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import kotlin.math.floor
import com.badlogic.gdx.utils.Array

class HighlightSystem(private val blockSize: Float) {
    private var highlightModel: Model? = null
    private var highlightInstance: ModelInstance? = null
    private var highlightMaterial: Material? = null
    private var isHighlightVisible = false
    private var highlightPosition = Vector3()
    private var currentHighlightSize = Vector3(blockSize, blockSize, blockSize)

    // Colors for different states
    private val placeColor = Color(0f, 1f, 0f, 0.3f) // Green for placement
    private val removeColor = Color(1f, 0f, 0f, 0.3f) // Red for removal
    private val invisibleOutlineColor = Color(0.8f, 0.8f, 1f, 0.25f)
    private val toolColors = mapOf(
        UIManager.Tool.BLOCK to Color(0f, 1f, 0f, 0.3f),      // Green
        UIManager.Tool.OBJECT to Color(0f, 0f, 1f, 0.3f),     // Blue
        UIManager.Tool.ITEM to Color(1f, 1f, 0f, 0.3f),       // Yellow
        UIManager.Tool.CAR to Color(1f, 0f, 1f, 0.3f),        // Purple
        UIManager.Tool.PLAYER to Color(0f, 1f, 0f, 0.3f),     // Green
        UIManager.Tool.HOUSE to Color(0f, 1f, 1f, 0.3f),      // Cyan
        UIManager.Tool.BACKGROUND to Color(1f, 0.5f, 0f, 0.3f), // Orange
        UIManager.Tool.PARALLAX to Color(0.4f, 0.8f, 0.7f, 0.3f),  // Teal
        UIManager.Tool.INTERIOR to Color(0.8f, 0.5f, 0.2f, 0.3f), // Brown
        UIManager.Tool.ENEMY to Color(1f, 0f, 0f, 0.4f), // Red highlight for enemies
        UIManager.Tool.NPC to Color(0.2f, 0.8f, 1f, 0.4f),
        UIManager.Tool.PARTICLE to Color(1f, 0.5f, 0.2f, 0.4f)

    )

    fun initialize() {
        val modelBuilder = ModelBuilder()

        // Create a transparent material with blending
        highlightMaterial = Material(
            ColorAttribute.createDiffuse(placeColor),
            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(
                GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f
            )
        )

        // Create initial highlight model
        createHighlightModel(Vector3(blockSize, blockSize, blockSize))
    }

    private fun createHighlightModel(size: Vector3) {
        // Dispose old model if it exists
        highlightModel?.dispose()

        val modelBuilder = ModelBuilder()
        val highlightSize = size.cpy().add(0.2f, 0.2f, 0.2f) // Slightly larger for visibility

        highlightModel = modelBuilder.createBox(
            highlightSize.x, highlightSize.y, highlightSize.z,
            highlightMaterial!!,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
        highlightInstance = ModelInstance(highlightModel!!)
        currentHighlightSize.set(size)
    }

    private fun updateHighlightSize(newSize: Vector3) {
        if (!currentHighlightSize.epsilonEquals(newSize, 0.1f)) {
            createHighlightModel(newSize)
        }
    }

    fun update(
        cameraManager: CameraManager,
        uiManager: UIManager,
        blockSystem: BlockSystem,
        gameBlocks: Array<GameBlock>,
        gameObjects: Array<GameObject>,
        gameCars: Array<GameCar>,
        gameHouses: Array<GameHouse>,
        backgroundSystem: BackgroundSystem,
        parallaxSystem: ParallaxBackgroundSystem,
        itemSystem: ItemSystem,
        objectSystem: ObjectSystem,
        raycastSystem: RaycastSystem,
        gameInteriors: Array<GameInterior>,
        interiorSystem: InteriorSystem,
        gameEnemies: Array<GameEnemy>,
        gameNPCs: Array<GameNPC>,
        particleSystem: ParticleSystem,
    ) {
        val mouseX = Gdx.input.x.toFloat()
        val mouseY = Gdx.input.y.toFloat()
        val ray = cameraManager.camera.getPickRay(mouseX, mouseY)

        when (uiManager.selectedTool) {
            UIManager.Tool.BLOCK -> updateBlockHighlight(ray, gameBlocks, raycastSystem, blockSystem)
            UIManager.Tool.OBJECT -> updateObjectHighlight(ray, gameObjects, objectSystem, raycastSystem)
            UIManager.Tool.ITEM -> updateItemHighlight(ray, itemSystem, raycastSystem)
            UIManager.Tool.CAR -> updateCarHighlight(ray, gameCars, raycastSystem, uiManager)
            UIManager.Tool.PLAYER -> updatePlayerHighlight(ray, gameBlocks, raycastSystem)
            UIManager.Tool.HOUSE -> updateHouseHighlight(ray, gameHouses, raycastSystem, uiManager)
            UIManager.Tool.BACKGROUND -> updateBackgroundHighlight(ray, backgroundSystem, raycastSystem, uiManager)
            UIManager.Tool.PARALLAX -> updateParallaxHighlight(ray, uiManager, parallaxSystem, raycastSystem)
            UIManager.Tool.INTERIOR -> updateInteriorHighlight(ray, gameInteriors, interiorSystem, raycastSystem)
            UIManager.Tool.ENEMY -> updateEnemyHighlight(ray, gameEnemies, raycastSystem)
            UIManager.Tool.NPC -> updateNPCHighlight(ray, gameNPCs, raycastSystem)
            UIManager.Tool.PARTICLE -> updateParticleHighlight(ray, particleSystem)
        }
        if (uiManager.selectedTool != UIManager.Tool.INTERIOR) {
            interiorSystem.hidePreview()
        }
    }

    fun renderInvisibleBlockOutlines(
        modelBatch: ModelBatch,
        environment: Environment,
        camera: Camera,
        blocks: Array<GameBlock>
    ) {
        if (highlightInstance == null) return

        // Ensure the highlight box is the correct size for a standard block
        updateHighlightSize(Vector3(blockSize, blockSize, blockSize))
        setHighlightColor(invisibleOutlineColor)

        // Set up rendering for transparency
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDepthMask(false)

        modelBatch.begin(camera)
        for (block in blocks) {
            if (block.blockType == BlockType.INVISIBLE) {
                // Move the single highlight instance to the block's position and render it
                highlightInstance!!.transform.setTranslation(block.position)
                modelBatch.render(highlightInstance!!, environment)
            }
        }
        modelBatch.end()

        // Restore normal rendering state
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun updateBlockHighlight(ray: Ray, gameBlocks: Array<GameBlock>, raycastSystem: RaycastSystem, blockSystem: BlockSystem) {
        val buildMode = blockSystem.currentBuildMode
        val size = buildMode.size.toFloat()

        val hitBlock = raycastSystem.getBlockAtRay(ray, gameBlocks)

        if (hitBlock != null) {
            // REMOVAL HIGHLIGHT
            var highlightSize = Vector3(blockSize, blockSize, blockSize)

            if (buildMode.isWall) {
                val blockBounds = BoundingBox()
                hitBlock.getBoundingBox(blockSize, blockBounds)
                val intersection = Vector3()
                if (Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
                    val relativePos = intersection.cpy().sub(hitBlock.position)
                    val absX = kotlin.math.abs(relativePos.x)
                    val absY = kotlin.math.abs(relativePos.y)
                    val absZ = kotlin.math.abs(relativePos.z)
                    when {
                        absX > absY && absX > absZ -> highlightSize.set(blockSize, size * blockSize, size * blockSize) // YZ plane
                        absY > absX && absY > absZ -> highlightSize.set(size * blockSize, blockSize, size * blockSize) // XZ plane
                        else -> highlightSize.set(size * blockSize, size * blockSize, blockSize) // XY plane
                    }
                }
            } else { // Floor mode
                highlightSize.set(size * blockSize, blockSize, size * blockSize)
            }

            updateHighlightSize(highlightSize)
            showHighlight(hitBlock.position, removeColor)
        } else {
            // Show green highlight for block placement
            val intersection = Vector3()
            val groundPlane = Plane(Vector3.Y, 0f)

            if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
                val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2

                // Floors are flat, Walls are vertical.
                val highlightSize = if (buildMode.isWall) {
                    Vector3(size * blockSize, size * blockSize, blockSize)
                } else {
                    Vector3(size * blockSize, blockSize, size * blockSize)
                }

                val yOffset = highlightSize.y / 2f
                val placementPos = Vector3(gridX, yOffset, gridZ)

                updateHighlightSize(highlightSize)
                showHighlight(placementPos, placeColor)
            } else {
                hideHighlight()
            }
        }
    }

    private fun updateObjectHighlight(ray: Ray, gameObjects: Array<GameObject>, objectSystem: ObjectSystem, raycastSystem: RaycastSystem) {
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            when (objectSystem.currentSelectedObject) {
                ObjectType.LIGHT_SOURCE -> {
                    updateHighlightSize(Vector3(1f, 1f, 1f)) // Small size for light sources

                    // Check for existing light source
                    val existingLight = objectSystem.getLightSourceAt(intersection, 2f)
                    if (existingLight != null) {
                        showHighlight(existingLight.position, removeColor)
                    } else {
                        showHighlight(Vector3(intersection.x, intersection.y, intersection.z), toolColors[UIManager.Tool.OBJECT]!!)
                    }
                }
                else -> {
                    updateHighlightSize(Vector3(2f, 2f, 2f)) // Standard object size

                    val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
                    val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2
                    val placementPos = Vector3(gridX, 1f, gridZ) // Adjusted Y position

                    // Check for existing object
                    val existingObject = gameObjects.find { gameObject ->
                        gameObject.position.dst(placementPos) < 1f
                    }

                    if (existingObject != null) {
                        showHighlight(existingObject.position, removeColor)
                    } else {
                        showHighlight(placementPos, toolColors[UIManager.Tool.OBJECT]!!)
                    }
                }
            }
        } else {
            hideHighlight()
        }
    }

    private fun updateItemHighlight(ray: Ray, itemSystem: ItemSystem, raycastSystem: RaycastSystem) {
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        updateHighlightSize(Vector3(1.5f, 1.5f, 1.5f)) // Item size

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val placementPos = Vector3(intersection.x, intersection.y + 1f, intersection.z)

            // Check for existing item
            val existingItem = itemSystem.getItemAtPosition(placementPos, 1.5f)
            if (existingItem != null && !existingItem.isCollected) {
                showHighlight(existingItem.position, removeColor)
            } else {
                showHighlight(placementPos, toolColors[UIManager.Tool.ITEM]!!)
            }
        } else {
            hideHighlight()
        }
    }

    private fun updateCarHighlight(ray: Ray, gameCars: Array<GameCar>, raycastSystem: RaycastSystem, uiManager: UIManager) {
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        // Get car dimensions - use proper car size
        val carSize = Vector3(4f, 2f, 6f)
        updateHighlightSize(carSize)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2
            val placementPos = Vector3(gridX, carSize.y / 2, gridZ) // Position at car center height

            // Check for existing car
            val existingCar = gameCars.find { car ->
                car.position.dst(placementPos) < 3f // Increased detection range for cars
            }

            if (existingCar != null) {
                // Show red highlight at actual car position with car size
                showHighlight(existingCar.position, removeColor)
            } else {
                showHighlight(placementPos, toolColors[UIManager.Tool.CAR]!!)
            }
        } else {
            hideHighlight()
        }
    }

    private fun updateHouseHighlight(ray: Ray, gameHouses: Array<GameHouse>, raycastSystem: RaycastSystem, uiManager: UIManager) {
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        // Get house dimensions based on selected house type
        val houseSize = getHouseDimensions(uiManager)
        updateHighlightSize(houseSize)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val gridX = floor(intersection.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(intersection.z / blockSize) * blockSize + blockSize / 2
            val placementPos = Vector3(gridX, houseSize.y / 2, gridZ)

            // Check for existing house
            val existingHouse = gameHouses.find { house ->
                house.position.dst(placementPos) < 4f // Increased detection range for houses
            }

            if (existingHouse != null) {
                // Show red highlight at actual house position with house size
                val actualHouseSize = getHouseDimensionsForType(existingHouse.houseType)
                updateHighlightSize(actualHouseSize)
                showHighlight(existingHouse.position, removeColor)
            } else {
                showHighlight(placementPos, toolColors[UIManager.Tool.HOUSE]!!)
            }
        } else {
            hideHighlight()
        }
    }

    private fun updateBackgroundHighlight(ray: Ray, backgroundSystem: BackgroundSystem, raycastSystem: RaycastSystem, uiManager: UIManager) {
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            // Get background dimensions from selected background type
            val selectedBackground = backgroundSystem.currentSelectedBackground
            val backgroundSize = Vector3(selectedBackground.width, selectedBackground.height, 0.5f) // Thin depth for backgrounds
            updateHighlightSize(backgroundSize)

            // Check for existing background at intersection position
            val existingBackground = backgroundSystem.getBackgroundAtPosition(intersection, backgroundSize.x / 2)

            if (existingBackground != null) {
                // Show red highlight at actual background position with correct size
                val actualBackgroundSize = Vector3(
                    existingBackground.backgroundType.width,
                    existingBackground.backgroundType.height,
                    0.5f
                )
                updateHighlightSize(actualBackgroundSize)
                showHighlight(existingBackground.position, removeColor)
            } else {
                // Show placement highlight at intersection point (not grid-snapped for backgrounds)
                val placementPos = Vector3(intersection.x, intersection.y + backgroundSize.y / 2, intersection.z)
                showHighlight(placementPos, toolColors[UIManager.Tool.BACKGROUND]!!)
            }
        } else {
            hideHighlight()
        }
    }

    private fun updateParallaxHighlight(ray: Ray, uiManager: UIManager, parallaxSystem: ParallaxBackgroundSystem, raycastSystem: RaycastSystem) {
        // First, check if we're hovering over an existing image to remove it
        val imageToRemove = raycastSystem.getParallaxImageAtRay(ray, parallaxSystem)
        if (imageToRemove != null) {
            val imageSize = Vector3(imageToRemove.width, imageToRemove.height, 1f)
            updateHighlightSize(imageSize)

            // The position of a parallax image is its current, camera-adjusted position
            // We calculate the center for the highlight box
            val highlightPos = Vector3(
                imageToRemove.currentPosition.x,
                imageToRemove.currentPosition.y + imageSize.y / 2f,
                imageToRemove.currentPosition.z
            )
            showHighlight(highlightPos, removeColor)
            return
        }

        // If not removing, show a placement highlight
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val imageType = uiManager.getCurrentParallaxImageType()
            val layerIndex = uiManager.getCurrentParallaxLayer()
            val layer = parallaxSystem.getLayers()[layerIndex]

            val highlightSize = Vector3(imageType.width, imageType.height, 1f)
            updateHighlightSize(highlightSize)

            // Position the highlight where the image *would* be placed.
            // X comes from the cursor, while Y and Z (depth) come from the layer's properties.
            val placementPos = Vector3(
                intersection.x,
                layer.height + highlightSize.y / 2f,
                -layer.depth
            )
            showHighlight(placementPos, toolColors[UIManager.Tool.PARALLAX]!!)
        } else {
            hideHighlight()
        }
    }

    private fun updatePlayerHighlight(ray: Ray, gameBlocks: Array<GameBlock>, raycastSystem: RaycastSystem) {
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        updateHighlightSize(Vector3(blockSize, blockSize * 2, blockSize)) // Player is taller

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val gridX = floor(intersection.x / blockSize) * blockSize
            val gridZ = floor(intersection.z / blockSize) * blockSize
            val placementPos = Vector3(gridX + blockSize / 2, blockSize, gridZ + blockSize / 2)

            // Check if there's a block at this position (player can't be placed on blocks)
            val existingBlock = gameBlocks.find { gameBlock ->
                gameBlock.position.dst(placementPos) < blockSize
            }

            if (existingBlock == null) {
                showHighlight(placementPos, toolColors[UIManager.Tool.PLAYER]!!)
            } else {
                hideHighlight()
            }
        } else {
            hideHighlight()
        }
    }

    private fun updateInteriorHighlight(ray: Ray, gameInteriors: Array<GameInterior>, interiorSystem: InteriorSystem, raycastSystem: RaycastSystem) {
        interiorSystem.updatePreview(ray)

        if (interiorSystem.currentSelectedInterior == InteriorType.PLAYER_SPAWNPOINT) {
            hideHighlight()

            val interiorToRemove = raycastSystem.getInteriorAtRay(ray, gameInteriors)
            if (interiorToRemove != null && interiorToRemove.interiorType == InteriorType.PLAYER_SPAWNPOINT) {
                val size = Vector3(interiorToRemove.interiorType.width, interiorToRemove.interiorType.height, interiorToRemove.interiorType.depth)
                updateHighlightSize(size)
                showHighlight(interiorToRemove.position, removeColor)
            }
            return
        }

        // First, check if hovering over an existing interior to remove it
        val interiorToRemove = raycastSystem.getInteriorAtRay(ray, gameInteriors)
        if (interiorToRemove != null) {
            val size = Vector3(interiorToRemove.interiorType.width, interiorToRemove.interiorType.height, interiorToRemove.interiorType.depth)
            updateHighlightSize(size)
            showHighlight(interiorToRemove.position, removeColor)
            return
        }

        // If not removing, show placement highlight on the floor (Y=0)
        val intersection = Vector3()
        val floorPlane = Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, floorPlane, intersection)) {
            val selectedType = interiorSystem.currentSelectedInterior
            val highlightSize = Vector3(selectedType.width, selectedType.height, selectedType.depth)
            updateHighlightSize(highlightSize)

            // Center the highlight box at the placement position, raised by half its height
            val placementPos = Vector3(intersection.x, intersection.y + highlightSize.y / 2f, intersection.z)
            showHighlight(placementPos, toolColors[UIManager.Tool.INTERIOR]!!)
        } else {
            hideHighlight()
        }
    }

    private fun updateEnemyHighlight(ray: Ray, gameEnemies: Array<GameEnemy>, raycastSystem: RaycastSystem) {
        // Check if hovering over an existing enemy to remove it
        val enemyToRemove = raycastSystem.getEnemyAtRay(ray, gameEnemies)
        if (enemyToRemove != null) {
            updateHighlightSize(Vector3(enemyToRemove.enemyType.width, enemyToRemove.enemyType.height, enemyToRemove.enemyType.width))
            showHighlight(enemyToRemove.position, removeColor)
            return
        }

        // Show placement highlight on the ground
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val placementPos = Vector3(intersection.x, intersection.y + 2f, intersection.z)
            updateHighlightSize(Vector3(3f, 4f, 3f)) // Use a generic size for the placement preview
            showHighlight(placementPos, toolColors[UIManager.Tool.ENEMY]!!)
        } else {
            hideHighlight()
        }
    }

    private fun updateNPCHighlight(ray: Ray, gameNPCs: Array<GameNPC>, raycastSystem: RaycastSystem) {
        // Check if hovering over an existing NPC to remove it
        val npcToRemove = raycastSystem.getNPCAtRay(ray, gameNPCs)
        if (npcToRemove != null) {
            updateHighlightSize(Vector3(npcToRemove.npcType.width, npcToRemove.npcType.height, npcToRemove.npcType.width))
            showHighlight(npcToRemove.position, removeColor)
            return
        }

        // Show placement highlight on the ground
        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val placementPos = Vector3(intersection.x, intersection.y + 2f, intersection.z)
            updateHighlightSize(Vector3(3f, 4f, 3f)) // Generic preview size
            showHighlight(placementPos, toolColors[UIManager.Tool.NPC]!!)
        } else {
            hideHighlight()
        }
    }

    private fun updateParticleHighlight(ray: Ray, particleSystem: ParticleSystem) {
        val effectType = particleSystem.currentSelectedEffect
        val highlightSize = effectType.scale + effectType.scaleVariance
        updateHighlightSize(Vector3(highlightSize, highlightSize, highlightSize))

        val intersection = Vector3()
        val groundPlane = Plane(Vector3.Y, 0f)

        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            showHighlight(intersection, toolColors[UIManager.Tool.PARTICLE]!!)
        } else {
            hideHighlight()
        }
    }

    // Helper function to get house dimensions for UI manager
    private fun getHouseDimensions(uiManager: UIManager): Vector3 {
        return Vector3(8f, 6f, 8f)
    }

    // Helper function to get house dimensions based on house type
    private fun getHouseDimensionsForType(houseType: HouseType): Vector3 {
        return when (houseType) {
            HouseType.HOUSE_4 -> Vector3(8f * 6f, 6f * 6f, 8f * 6f) // Scaled version
            else -> Vector3(8f, 6f, 8f) // Default house dimensions
        }
    }

    private fun showHighlight(position: Vector3, color: Color) {
        isHighlightVisible = true
        highlightPosition.set(position)
        setHighlightColor(color)
        updateHighlightTransform()
    }

    private fun hideHighlight() {
        isHighlightVisible = false
    }

    private fun setHighlightColor(color: Color) {
        highlightMaterial?.set(ColorAttribute.createDiffuse(color))
    }

    private fun updateHighlightTransform() {
        // Reset scale first (important for background highlights)
        highlightInstance?.transform?.idt()
        highlightInstance?.transform?.setTranslation(highlightPosition)
    }

    fun render(modelBatch: ModelBatch, camera: com.badlogic.gdx.graphics.Camera, environment: com.badlogic.gdx.graphics.g3d.Environment) {
        if (isHighlightVisible && highlightInstance != null) {
            // Enable blending for transparency
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

            // Disable depth writing but keep depth testing
            Gdx.gl.glDepthMask(false)

            modelBatch.begin(camera)
            modelBatch.render(highlightInstance!!, environment)
            modelBatch.end()

            // Restore depth writing and disable blending
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
    }

    fun dispose() {
        highlightModel?.dispose()
    }
}
