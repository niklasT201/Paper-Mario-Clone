package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array
import kotlin.random.Random
import java.util.*
import kotlin.math.floor
import kotlin.math.sin

// Item system class to manage 2D rotating item pickups
class ItemSystem: IFinePositionable {
    companion object {
        const val UNIFIED_ITEM_HEIGHT = 2.0f // All items will now have this visual height in the game world.
        const val ITEM_SURFACE_OFFSET = 0.35f
    }
    private val itemModels = mutableMapOf<ItemType, Model>()
    private val itemTextures = mutableMapOf<ItemType, Texture>()
    private lateinit var itemModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider
    private val renderableInstances = Array<ModelInstance>()
    private val FALL_SPEED = 25f
    private var blockSize: Float = 4f // Will be set by initialize

    var currentSelectedItem = ItemType.MONEY_STACK
        private set
    var currentSelectedItemIndex = 0
        private set

    override var finePosMode = false
    override val fineStep = 0.25f

    lateinit var sceneManager: SceneManager
    lateinit var raycastSystem: RaycastSystem
    private val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()

    fun initialize(blockSize: Float) {
        this.blockSize = blockSize
        this.raycastSystem = RaycastSystem(blockSize)
        val modelBuilder = ModelBuilder()

        // Initialize shader and batch for items
        billboardShaderProvider = BillboardShaderProvider()
        itemModelBatch = ModelBatch(billboardShaderProvider)
        billboardShaderProvider.setBillboardLightingStrength(0.7f)

        // Load textures and create models for each item type
        for (itemType in ItemType.entries) {
            try {
                // Load texture
                val texture = Texture(Gdx.files.internal(itemType.texturePath))
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                itemTextures[itemType] = texture

                // Create material with texture and transparency
                val material = Material(
                    TextureAttribute.createDiffuse(texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE) // Disable backface culling for 2D sprites
                )

                // Create billboard model (always faces camera)
                val model = createBillboardModel(modelBuilder, material, itemType)
                itemModels[itemType] = model

                println("Loaded item type: ${itemType.displayName}")
            } catch (e: Exception) {
                println("Failed to load item ${itemType.displayName}: ${e.message}")
            }
        }
    }

    private fun createBillboardModel(modelBuilder: ModelBuilder, material: Material, itemType: ItemType): Model {
        val width = itemType.width
        val height = UNIFIED_ITEM_HEIGHT
        val halfWidth = width / 2f
        val halfHeight = height / 2f

        // Create a single quad that will be rotated to face the camera
        modelBuilder.begin()

        val part = modelBuilder.part(
            "billboard",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or
                VertexAttributes.Usage.Normal or
                VertexAttributes.Usage.TextureCoordinates).toLong(),
            material
        )

        // Vertices for the quad (Y-up orientation)
        part.vertex(-halfWidth, 0f, 0f, 0f, 0f, 1f, 0f, 1f) // Bottom left
        part.vertex(halfWidth, 0f, 0f, 0f, 0f, 1f, 1f, 1f)  // Bottom right
        part.vertex(halfWidth, height, 0f, 0f, 0f, 1f, 1f, 0f) // Top right
        part.vertex(-halfWidth, height, 0f, 0f, 0f, 1f, 0f, 0f) // Top left

        // Triangles for the quad
        part.triangle(0, 1, 2) // First triangle
        part.triangle(2, 3, 0) // Second triangle

        return modelBuilder.end()
    }

    fun nextItem() {
        currentSelectedItemIndex = (currentSelectedItemIndex + 1) % ItemType.entries.size
        currentSelectedItem = ItemType.entries.toTypedArray()[currentSelectedItemIndex]
        println("Selected item: ${currentSelectedItem.displayName}")
    }

    fun previousItem() {
        currentSelectedItemIndex = if (currentSelectedItemIndex > 0) {
            currentSelectedItemIndex - 1
        } else {
            ItemType.entries.size - 1
        }
        currentSelectedItem = ItemType.entries.toTypedArray()[currentSelectedItemIndex]
        println("Selected item: ${currentSelectedItem.displayName}")
    }

    private fun createItemInstance(itemType: ItemType): ModelInstance? {
        val model = itemModels[itemType]
        return model?.let {
            val instance = ModelInstance(it)
            instance.userData = "item"
            instance
        }
    }

    fun createItem(position: Vector3, itemType: ItemType, pickupDelay: Float = 0.5f): GameItem? {
        val modelInstance = createItemInstance(itemType)
        if (modelInstance != null) {
            val newGameItem = GameItem(
                modelInstance,
                itemType,
                position.cpy(),
                pickupDelay = pickupDelay
            )

            itemType.correspondingWeapon?.let { weaponType ->
                if (weaponType.soundVariations > 0 && weaponType.soundId != null) {
                    val randomVariation = Random.nextInt(1, weaponType.soundVariations + 1)
                    newGameItem.soundVariationId = "${weaponType.soundId}_V$randomVariation"
                    println("Created ${itemType.displayName} with sound variation: ${newGameItem.soundVariationId}")
                }
            }

            return newGameItem
        }
        return null
    }

    fun handlePlaceAction(ray: Ray) {
        // Check the current editor mode
        if (sceneManager.game.uiManager.currentEditorMode == EditorMode.MISSION) {
            handleMissionPlacement(ray)
            return
        }

        // World Editing logic
        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())
        if (hitBlock != null) {
            // We hit a block directly - place item on top of it
            placeItemOnBlock(ray, hitBlock)
        } else {
            // No block hit, use the original ground plane method
            placeItemOnGround(ray)
        }
    }

    private fun handleMissionPlacement(ray: Ray) {
        val mission = sceneManager.game.uiManager.selectedMissionForEditing
        if (mission == null) {
            sceneManager.game.uiManager.updatePlacementInfo("ERROR: No mission selected for editing!")
            return
        }

        var itemPosition: Vector3? = null
        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())
        if (hitBlock != null) {
            val blockBounds = BoundingBox()
            hitBlock.getBoundingBox(blockSize, blockBounds)
            val intersection = Vector3()
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
                val relativePos = Vector3(intersection).sub(hitBlock.position)
                val absY = kotlin.math.abs(relativePos.y)
                val absX = kotlin.math.abs(relativePos.x)
                val absZ = kotlin.math.abs(relativePos.z)
                itemPosition = when {
                    absY >= absX && absY >= absZ && relativePos.y > 0 -> Vector3(hitBlock.position.x, hitBlock.position.y + blockSize / 2 + ITEM_SURFACE_OFFSET, hitBlock.position.z)
                    else -> Vector3(intersection.x, hitBlock.position.y + blockSize / 2 + ITEM_SURFACE_OFFSET, intersection.z)
                }
            }
        } else {
            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
                val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
                val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2
                val properY = calculateGroundYAt(gridX, gridZ, ITEM_SURFACE_OFFSET)
                itemPosition = Vector3(gridX, properY, gridZ)
            }
        }

        if (itemPosition != null) {
            val itemType = currentSelectedItem
            val eventType = if (itemType == ItemType.MONEY_STACK) GameEventType.SPAWN_MONEY_STACK else GameEventType.SPAWN_ITEM
            val currentSceneId = sceneManager.getCurrentSceneId()

            // 1. Create the GameEvent
            val event = GameEvent(
                type = eventType,
                spawnPosition = itemPosition,
                sceneId = currentSceneId,
                itemType = itemType,
                itemValue = itemType.value,
                targetId = if (itemType.correspondingWeapon != null) "item_${UUID.randomUUID()}" else null // Give specific items an ID
            )

            // 2. Add and save
            mission.eventsOnStart.add(event)
            sceneManager.game.missionSystem.saveMission(mission)

            // 3. Create preview item
            val previewItem = createItem(itemPosition, itemType, pickupDelay = 9999f) // Long delay so it can't be picked up
            if (previewItem != null) {
                previewItem.modelInstance.transform.setToTranslation(itemPosition)

                previewItem.missionId = mission.id
                if (event.targetId != null) previewItem.id = event.targetId
                sceneManager.activeMissionPreviewItems.add(previewItem)
                sceneManager.game.lastPlacedInstance = previewItem
                sceneManager.game.uiManager.updatePlacementInfo("Added $eventType to '${mission.title}'")
                sceneManager.game.uiManager.missionEditorUI.refreshEventWidgets()

                println("Preview item created at position: $itemPosition")
                println("Preview item modelInstance transform: ${previewItem.modelInstance.transform}")
            } else {
                println("Failed to create preview item!")
            }
        }
    }

    fun handleRemoveAction(ray: Ray): Boolean {
        val itemToRemove = raycastSystem.getItemAtRay(ray, sceneManager.activeItems)
        if (itemToRemove != null) {
            sceneManager.activeItems.removeValue(itemToRemove, true)
            println("Removed ${itemToRemove.itemType.displayName}")
            return true
        }
        return false
    }

    private fun placeItemOnBlock(ray: Ray, hitBlock: GameBlock) {
        // Calculate intersection point with the hit block
        val blockBounds = BoundingBox()
        blockBounds.set(
            Vector3(
                hitBlock.position.x - blockSize / 2,
                hitBlock.position.y - blockSize / 2,
                hitBlock.position.z - blockSize / 2
            ),
            Vector3(
                hitBlock.position.x + blockSize / 2,
                hitBlock.position.y + blockSize / 2,
                hitBlock.position.z + blockSize / 2
            )
        )

        val intersection = Vector3()
        if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
            // Determine which face was hit
            val relativePos = Vector3(intersection).sub(hitBlock.position)

            // Find the dominant axis (which face was hit)
            val absX = kotlin.math.abs(relativePos.x)
            val absY = kotlin.math.abs(relativePos.y)
            val absZ = kotlin.math.abs(relativePos.z)

            val itemPosition = when {
                // Hit top face - place item on top
                absY >= absX && absY >= absZ && relativePos.y > 0 -> {
                    Vector3(
                        hitBlock.position.x,
                        hitBlock.position.y + blockSize / 2 + ITEM_SURFACE_OFFSET, // 1f above the block surface
                        hitBlock.position.z
                    )
                }
                // Hit side faces - place item at the side but on the same level as block top
                else -> {
                    val blockTop = hitBlock.position.y + blockSize / 2
                    Vector3(intersection.x, blockTop + ITEM_SURFACE_OFFSET, intersection.z)
                }
            }
            addItemToScene(itemPosition)
        }
    }

    private fun placeItemOnGround(ray: Ray) {
        if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
            // Items can be placed more freely, but still need to be on top of blocks
            val gridX = floor(tempVec3.x / blockSize) * blockSize + blockSize / 2
            val gridZ = floor(tempVec3.z / blockSize) * blockSize + blockSize / 2

            var properY = calculateGroundYAt(gridX, gridZ, ITEM_SURFACE_OFFSET)
            if (properY < -500f) { // Check for the fallback value
                // If no ground, place it on y=0 plus its offset
                properY = 0f + ITEM_SURFACE_OFFSET
            }

            val itemPosition = Vector3(gridX, properY, gridZ)
            addItemToScene(itemPosition)
        }
    }

    private fun addItemToScene(position: Vector3) {
        val newItem = createItem(position, currentSelectedItem)
        if (newItem != null) {
            sceneManager.activeItems.add(newItem)
            sceneManager.game.lastPlacedInstance = newItem
            println("${newItem.itemType.displayName} placed in scene at: $position")
        }
    }

    private fun calculateGroundYAt(x: Float, z: Float, objectHeight: Float = 0f): Float {
        var highestBlockY = 0f // Ground level

        for (gameBlock in sceneManager.activeChunkManager.getBlocksInColumn(x, z)) {
            if (!gameBlock.blockType.hasCollision) continue

            val blockCenterX = gameBlock.position.x
            val blockCenterZ = gameBlock.position.z

            // This tolerance check is the critical part that makes it work correctly
            val tolerance = blockSize / 4f
            if (kotlin.math.abs(blockCenterX - x) < tolerance &&
                kotlin.math.abs(blockCenterZ - z) < tolerance) {

                val blockHeight = blockSize * gameBlock.blockType.height
                val blockTop = gameBlock.position.y + blockHeight / 2f

                if (blockTop > highestBlockY) {
                    highestBlockY = blockTop
                }
            }
        }
        return highestBlockY + objectHeight
    }

    fun update(deltaTime: Float, camera: Camera, playerSystem: PlayerSystem, sceneManager: SceneManager) {
        val itemsToRemove = Array<GameItem>()
        val modifiers = sceneManager.game.missionSystem.activeModifiers

        for (item in sceneManager.activeItems) {
            // Check if the "no item drops" modifier is active
            if (modifiers?.disableNoItemDrops == true) {
                itemsToRemove.add(item) // If so, mark this item for immediate removal
                continue // Skip all other logic for this item
            }

            if (item.isCollected) continue

            if (item.pickupDelay > 0f) {
                item.pickupDelay -= deltaTime
            }

            if (!finePosMode) {
                // 1. Apply Gravity and Find Support for the item
                val itemX = item.position.x
                val itemZ = item.position.z
                val itemCurrentY = item.position.y + ITEM_SURFACE_OFFSET // The item's origin is at its base

                val supportY = sceneManager.findHighestSupportYForItem(itemX, itemZ, itemCurrentY, this.blockSize)

                // Apply Gravity
                val fallY = item.position.y - FALL_SPEED * deltaTime
                val nextY = kotlin.math.max(supportY, fallY) // The item is either on the ground or falling.

                // 2. Update item position if it moved vertically
                item.position.y = nextY
            }

            // Update item animation (rotation and bobbing)
            item.update(deltaTime, camera.position, ITEM_SURFACE_OFFSET)

            // Only check for collision if the pickup delay has expired.
            if (item.pickupDelay <= 0f && item.checkCollision(playerSystem.getPosition(), 2f)) {
                val modifiers = sceneManager.game.missionSystem.activeModifiers
                val isWeapon = item.itemType.correspondingWeapon != null

                // If this item is a weapon AND the mission modifier is active, skip the pickup
                if ((isWeapon && modifiers?.disableWeaponPickups == true) ||
                    (!isWeapon && modifiers?.disableItemPickups == true)) {
                    println("Mission prevents item pickup. Ignoring ${item.itemType.displayName}.")
                    continue // Skip to the next item
                }

                sceneManager.game.soundManager.playSound(
                    effect = SoundManager.Effect.ITEM_PICKUP,
                    position = item.position, // Play the sound where the item was
                    reverbProfile = null // No need for reverb on a UI-like sound
                )

                if (item.itemType == ItemType.MONEY_STACK) {
                    playerSystem.addMoney(item.value)
                    // The item will be marked for removal below, but don't equip anything
                } else {
                    // Check if the collected item corresponds to a weapon
                    item.itemType.correspondingWeapon?.let { weaponToEquip ->
                        // If it does, tell the player system to equip it
                        playerSystem.addWeaponToInventory(weaponToEquip, item.ammo, item.soundVariationId)
                    }
                }

                // Report the specific item ID to the mission system upon collection
                sceneManager.game.missionSystem.reportItemCollected(item.id)

                item.collect()
                itemsToRemove.add(item)
            }
        }

        // Remove collected items
        if (itemsToRemove.size > 0) {
            println("Removing ${itemsToRemove.size} items due to mission rules or collection.")
            for (item in itemsToRemove) {
                sceneManager.activeItems.removeValue(item, true)
            }
        }
    }

    fun render(camera: Camera, environment: Environment, items: Array<GameItem>) {
        if (items.isEmpty) return

        billboardShaderProvider.setEnvironment(environment)
        itemModelBatch.begin(camera)
        renderableInstances.clear()
        for (item in items) {
            if (!item.isCollected) {
                renderableInstances.add(item.modelInstance)
            }
        }
        if (renderableInstances.size > 0) {
            itemModelBatch.render(renderableInstances, environment)
        }
        itemModelBatch.end()
    }

    // Modify the original render function to call the new one
    fun render(camera: Camera, environment: Environment) {
        render(camera, environment, sceneManager.activeItems)
    }

    fun getItemAtPosition(position: Vector3, radius: Float = 2f): GameItem? {
        for (item in sceneManager.activeItems) {
            if (!item.isCollected && item.position.dst(position) <= radius) {
                return item
            }
        }
        return null
    }

    fun getTextureForItem(itemType: ItemType): Texture? {
        return itemTextures[itemType]
    }

    fun dispose() {
        itemModels.values.forEach { it.dispose() }
        itemTextures.values.forEach { it.dispose() }
        itemModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}

// Game item class to store item pickup data
data class GameItem(
    val modelInstance: ModelInstance,
    val itemType: ItemType,
    val position: Vector3,
    var rotationSpeed: Float = 90f,
    private var totalTime: Float = 0f,
    private var bobOffset: Float = 0f,
    var isCollected: Boolean = false,
    var ammo: Int = itemType.ammoAmount,
    var value: Int = itemType.value, // This is the changed line
    var pickupDelay: Float = 0f,
    var id: String = UUID.randomUUID().toString(),
    var missionId: String? = null,
    var soundVariationId: String? = null
) {
    init {
        // Random bobbing offset so items don't all bob in sync
        bobOffset = (Math.random() * Math.PI * 2).toFloat()
        // Randomize rotation speed slightly
        rotationSpeed += ((Math.random() - 0.5) * 30).toFloat() // ±15 degrees variation
    }

    // Get bounding box for collision detection
    fun getBoundingBox(): BoundingBox {
        val bounds = BoundingBox()
        val halfWidth = itemType.width / 2f

        // Create a smaller collision box for easier pickup
        val collisionScale = 0.8f
        bounds.set(
            Vector3(
                position.x - halfWidth * collisionScale,
                position.y,
                position.z - halfWidth * collisionScale
            ),
            Vector3(
                position.x + halfWidth * collisionScale,
                position.y + ItemSystem.UNIFIED_ITEM_HEIGHT * collisionScale,
                position.z + halfWidth * collisionScale
            )
        )
        return bounds
    }

    // Update item animation (rotation and bobbing)
    fun update(deltaTime: Float, cameraPosition: Vector3, surfaceOffset: Float = 0f) {
        if (isCollected) return

        totalTime += deltaTime

        // Rotate around Y-axis
        val currentRotation = totalTime * rotationSpeed

        // Bobbing animation (slight up-down movement)
        val bobHeight = sin(totalTime * 3f + bobOffset) * 0.3f // 0.3 units max bob

        // Calculate billboard rotation to face camera
        val direction = Vector3(cameraPosition).sub(position)
        direction.y = 0f // Keep it horizontal
        direction.nor()

        // Calculate angle to rotate towards camera
        val angle = Math.atan2(direction.x.toDouble(), direction.z.toDouble()) * 180.0 / Math.PI

        // Apply transformations: position + surfaceOffset + bobbing + rotation towards camera + spinning
        modelInstance.transform.setToTranslation(position.x, position.y + surfaceOffset + bobHeight, position.z)
        modelInstance.transform.rotate(Vector3.Y, angle.toFloat() + currentRotation)
    }

    // Check collision with player
    fun checkCollision(playerPosition: Vector3, playerRadius: Float = 1f): Boolean {
        if (isCollected) return false

        val distance = Vector3(playerPosition).sub(position).len()
        return distance < (playerRadius + itemType.width / 2f)
    }

    // Collect the item
    fun collect(): String {
        isCollected = true
        println("Collected ${itemType.displayName}!")
        return itemType.displayName
    }
}

// Item type definitions - updated with new weapon folder structure and additional weapons
enum class ItemType(
    val displayName: String,
    val texturePath: String,
    val width: Float,
    val value: Int = 1,
    val correspondingWeapon: WeaponType? = null,
    val ammoAmount: Int = 0
) {
    // Non-weapon items
    MONEY_STACK("Money Stack", "textures/objects/items/money_stack.png", 2f, 10),

    // Melee weapon
    BASEBALL_BAT("Baseball Bat", "textures/objects/items/weapons/baseball_bat.png", 2f, 15, WeaponType.BASEBALL_BAT, 0),

    // Weapons
    REVOLVER("Revolver", "textures/objects/items/weapons/revolver.png", 2f, 15, WeaponType.REVOLVER, 12),
    REVOLVER_LIGHT("Light Revolver", "textures/objects/items/weapons/revolver_light.png", 2f, 12, WeaponType.LIGHT_REVOLVER, 12),
    SMALLER_REVOLVER("Small Revolver", "textures/objects/items/weapons/smaller_revolver.png", 1.8f, 12, WeaponType.SMALLER_REVOLVER, 10),

    SHOTGUN("Shotgun", "textures/objects/items/weapons/shotgun.png", 3f, 25, WeaponType.SHOTGUN, 8),
    SHOTGUN_LIGHT("Light Shotgun", "textures/objects/items/weapons/shotgun_light.png", 3f, 22, WeaponType.LIGHT_SHOTGUN, 10),
    SMALL_SHOTGUN("Small Shotgun", "textures/objects/items/weapons/small_shotgun.png", 2.5f, 20, WeaponType.SMALL_SHOTGUN, 4),

    MACHINE_GUN("Machine Gun", "textures/objects/items/weapons/machine_gun.png", 3.5f, 40, WeaponType.MACHINE_GUN, 100),

    TOMMY_GUN("Tommy Gun", "textures/objects/items/weapons/tommy_gun.png", 3.5f, 35, WeaponType.TOMMY_GUN, 50),
    TOMMY_GUN_LIGHT("Light Tommy Gun", "textures/objects/items/weapons/tommy_gun_light.png", 3.5f, 32, WeaponType.LIGHT_TOMMY_GUN, 40),

    KNIFE("Knife", "textures/objects/items/weapons/knife.png", 1.5f, 8, WeaponType.KNIFE, 0),

    // Explosives
    DYNAMITE("Dynamite", "textures/objects/items/weapons/dynamite.png", 2f, 10, WeaponType.DYNAMITE, 5),
    MOLOTOV("Molotov", "textures/objects/items/weapons/molotov.png", 1.5f, 10, WeaponType.MOLOTOV, 5);
}
