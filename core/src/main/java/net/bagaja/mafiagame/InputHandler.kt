package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array
import net.bagaja.mafiagame.UIManager.Tool

class InputHandler(
    private val game: MafiaGame,
    private val uiManager: UIManager
) {
    lateinit var cameraManager: CameraManager
    lateinit var blockSystem: BlockSystem
    lateinit var objectSystem: ObjectSystem
    lateinit var itemSystem: ItemSystem
    lateinit var carSystem: CarSystem
    lateinit var houseSystem: HouseSystem
    lateinit var backgroundSystem: BackgroundSystem
    lateinit var parallaxSystem: ParallaxBackgroundSystem
    lateinit var interiorSystem: InteriorSystem
    lateinit var enemySystem: EnemySystem
    lateinit var npcSystem: NPCSystem
    lateinit var particleSystem: ParticleSystem
    lateinit var spawnerSystem: SpawnerSystem
    lateinit var teleporterSystem: TeleporterSystem
    lateinit var audioEmitterSystem: AudioEmitterSystem
    lateinit var sceneManager: SceneManager
    lateinit var roomTemplateManager: RoomTemplateManager
    lateinit var shaderEffectManager: ShaderEffectManager
    lateinit var carPathSystem: CarPathSystem
    lateinit var characterPathSystem: CharacterPathSystem

    private var isRightMousePressed = false
    private var isLeftMousePressed = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var isBlockSelectionMode = false
    private var isObjectSelectionMode = false
    private var isItemSelectionMode = false
    private var isCarSelectionMode = false
    private var isHouseSelectionMode = false
    private var isBackgroundSelectionMode = false
    private var isParallaxSelectionMode = false
    private var isInteriorSelectionMode = false
    private var isEnemySelectionMode = false
    private var isNPCSelectionMode = false
    private var isParticleSelectionMode = false
    private var isTimeSpeedUpPressed = false
    private var isPlacingCarPath = false
    private var isPlacingCharacterPath = false

    // Variables for continuous block placement/removal
    private var continuousActionTimer = 0f
    private val continuousActionDelay = 0.1f
    private var lastPlacementX = -1
    private var lastPlacementY = -1
    private var lastRemovalX = -1
    private var lastRemovalY = -1

    // Variables for continuous fine positioning
    private var continuousFineTimer = 0f
    private val continuousFineDelay = 0.05f // Faster for smooth movement
    private var leftPressed = false
    private var rightPressed = false
    private var upPressed = false
    private var downPressed = false
    private var pageUpPressed = false
    private var pageDownPressed = false
    val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)

    private fun handleBackgroundPreviewUpdate(screenX: Int, screenY: Int) {
        if (uiManager.selectedTool == Tool.BACKGROUND) {
            val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
            val intersection = Vector3()

            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                val adjustedPos = backgroundSystem.updatePreview(intersection)
                uiManager.updatePlacementInfo(backgroundSystem.getPlacementInfo(adjustedPos))
            }
        } else {
            backgroundSystem.hidePreview()
            uiManager.updatePlacementInfo("") // Clear placement info text
        }
    }

    fun initialize() {
        carPathSystem.raycastSystem = game.sceneManager.raycastSystem
        val inputMultiplexer = InputMultiplexer()

        inputMultiplexer.addProcessor(uiManager.getStage())
        inputMultiplexer.addProcessor(object : InputAdapter() {

            // Handle mouse movement for real-time previews
            override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
                handleBackgroundPreviewUpdate(screenX, screenY)
                return false // Don't consume the event, let other things use it if needed
            }

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                // Check if click is over UI
                /*
                val stageCoords = uiManager.getStage().screenToStageCoordinates(Vector2(screenX.toFloat(), screenY.toFloat()))
                val actorHit = uiManager.getStage().hit(stageCoords.x, stageCoords.y, true)

                // If we clicked on UI, let the stage handle it
                if (actorHit != null && uiManager.isUIVisible) {
                    return false
                }
                 */

                if (uiManager.isPlacingObjectiveArea) {
                    if (button == Input.Buttons.LEFT) {
                        val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                        val intersection = Vector3()
                        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                            val objective = uiManager.objectiveBeingPlaced ?: return true
                            val condition = objective.completionCondition

                            // Create a new objective with the updated center
                            val newCondition = condition.copy(areaCenter = intersection)
                            val updatedObjective = objective.copy(completionCondition = newCondition)

                            // Pass the entire updated objective back to the UIManager
                            uiManager.updateObjectiveAreaFromVisuals(updatedObjective)
                        }
                    }
                    return true
                }

                if (uiManager.isPauseMenuVisible()) {
                    return true
                }

                // Handle mouse input
                when (button) {
                    Input.Buttons.LEFT -> {
                        if (game.isEditorMode) {
                            // Check for special entry point placement mode
                            if (uiManager.isPlacingEntryPointMode) {
                                val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                                houseSystem.handleEntryPointPlaceAction(ray, uiManager.houseRequiringEntryPoint!!)
                                return true // Consume the click
                            }

                            isLeftMousePressed = true
                            val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                            when (uiManager.selectedTool) {
                                Tool.BLOCK -> blockSystem.handlePlaceAction(ray)
                                Tool.PLAYER -> {
                                    if (isPlacingCharacterPath) {
                                        characterPathSystem.handlePlaceAction(ray)
                                    } else {
                                        game.playerSystem.placePlayer(ray, sceneManager)
                                    }
                                }
                                Tool.OBJECT -> { objectSystem.handlePlaceAction(ray, objectSystem.currentSelectedObject) }
                                Tool.ITEM -> itemSystem.handlePlaceAction(ray)
                                Tool.CAR -> {
                                    if (isPlacingCarPath) {
                                        carPathSystem.handlePlaceAction(ray)
                                    } else {
                                        carSystem.handlePlaceAction(ray)
                                    }
                                }
                                Tool.HOUSE -> houseSystem.handlePlaceAction(ray)
                                Tool.BACKGROUND -> backgroundSystem.handlePlaceAction(ray)
                                Tool.PARALLAX -> parallaxSystem.handlePlaceAction(ray)
                                Tool.INTERIOR -> interiorSystem.handlePlaceAction(ray)
                                Tool.ENEMY -> enemySystem.handlePlaceAction(ray)
                                Tool.NPC -> npcSystem.handlePlaceAction(ray)
                                Tool.PARTICLE -> particleSystem.handlePlaceAction(ray)
                                Tool.TRIGGER -> {
                                    val missionId = game.triggerSystem.selectedMissionIdForEditing
                                    if (missionId == null) {
                                        uiManager.showTemporaryMessage("Select a mission to edit in the Mission Editor (F7).")
                                        return true
                                    }
                                    val mission = game.missionSystem.getMissionDefinition(missionId) ?: return true
                                    val triggerType = mission.startTrigger.type

                                    // 1. Check if the current trigger type actually needs an area.
                                    val isAreaBased = triggerType in listOf(
                                        TriggerType.ON_ENTER_AREA,
                                        TriggerType.ON_LEAVE_AREA,
                                        TriggerType.ON_STAY_IN_AREA_FOR_TIME
                                    )

                                    // 2. If it doesn't need an area, inform the user and do nothing.
                                    if (!isAreaBased) {
                                        uiManager.showTemporaryMessage("This trigger type does not need a location.")
                                        return true // Consume the click but don't place anything.
                                    }

                                    // 3. If it IS an area-based trigger, perform the placement logic.
                                    val intersection = Vector3()
                                    if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                                        // Update the mission's trigger data
                                        mission.startTrigger.areaCenter.set(intersection)
                                        if (game.sceneManager.currentScene == SceneType.HOUSE_INTERIOR) {
                                            val currentHouseId = game.sceneManager.getCurrentHouse()?.id
                                            if (currentHouseId != null) {
                                                mission.startTrigger.sceneId = currentHouseId
                                                uiManager.updatePlacementInfo("Set trigger for '${mission.title}' in this room.")
                                            }
                                        } else {
                                            mission.startTrigger.sceneId = "WORLD"
                                            uiManager.updatePlacementInfo("Set trigger for '${mission.title}' in the world.")
                                        }

                                        // Save the mission file with the new trigger position and sceneId
                                        game.missionSystem.saveMission(mission)
                                    }
                                    return true // Return true as the action is handled.
                                }
                                Tool.AUDIO_EMITTER -> audioEmitterSystem.handlePlaceAction(ray)
                            }
                            // Reset timer and track position for continuous placement
                            continuousActionTimer = 0f
                            lastPlacementX = screenX
                            lastPlacementY = screenY

                            // Hide preview right after placing
                            if (uiManager.selectedTool == Tool.BACKGROUND) {
                                backgroundSystem.hidePreview()
                            }
                            return true
                        }
                    }
                    Input.Buttons.RIGHT -> {
                        // Cancel a charged throw
                        if (!game.isEditorMode && game.playerSystem.isChargingThrow()) {
                            return true
                        }

                        if (!game.isEditorMode) {
                            val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())

                            if (uiManager.selectedTool == Tool.CAR) {
                                if (carPathSystem.handleRemoveAction(ray)) {
                                    return true // Consume the click if a node was removed
                                }
                            }

                            // Check if the player has a valid weapon equipped before trying to open the UI.
                            val weapon = game.playerSystem.equippedWeapon
                            val canInspect = weapon.actionType == WeaponActionType.SHOOTING ||
                                (weapon.actionType == WeaponActionType.MELEE && weapon != WeaponType.UNARMED)

                            if (canInspect) {
                                // Check for enemy first
                                val enemyToInspect = enemySystem.raycastSystem.getEnemyAtRay(ray, sceneManager.activeEnemies)
                                if (enemyToInspect != null) {
                                    uiManager.showEnemyInventory(enemyToInspect)
                                    return true // Consume the click so it doesn't trigger camera drag
                                }

                                // If no enemy, check for NPC
                                val npcToInspect = npcSystem.raycastSystem.getNPCAtRay(ray, sceneManager.activeNPCs)
                                if (npcToInspect != null) {
                                    uiManager.showNpcInventory(npcToInspect)
                                    return true // Consume the click
                                }
                            }
                        }

                        // PRIORITY 2: Handle removal actions in Editor Mode.
                        if (game.isEditorMode) {
                            // Try to remove a block. If successful, consume the event.
                            val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())

                            // 1. Raycast for an emitter just ONCE using its position property.
                            val hitEmitter = audioEmitterSystem.activeEmitters.find { emitter ->
                                // Create a temporary bounding box in world space for the check
                                val bounds = BoundingBox()
                                val halfSize = 1.5f / 2f // Based on the debug model size in AudioEmitterSystem
                                bounds.set(
                                    emitter.position.cpy().sub(halfSize),
                                    emitter.position.cpy().add(halfSize)
                                )
                                Intersector.intersectRayBounds(ray, bounds, null)
                            }

                            // 2. If we hit an emitter, decide what to do.
                            if (hitEmitter != null) {
                                if (game.isInspectModeEnabled) {
                                    // 2a. Inspect Mode is ON: Copy the ID.
                                    Gdx.app.clipboard.contents = hitEmitter.id
                                    uiManager.showTemporaryMessage("Copied Audio Emitter ID: ${hitEmitter.id}")
                                } else {
                                    // 2b. Inspect Mode is OFF: Open the editor UI.
                                    uiManager.showAudioEmitterUI(hitEmitter)
                                }
                                return true // Consume the click event in both cases.
                            }

                            // HIGHEST PRIORITY: Check if we are right-clicking a light source that belongs to another object.
                            val lightToRemove = game.raycastSystem.getLightSourceAtRay(ray, game.lightingManager)
                            if (lightToRemove?.parentObjectId != null) {
                                val parentId = lightToRemove.parentObjectId!! // We know it's not null here

                                // Try to find the parent object in the active lists.
                                val parentFire = game.fireSystem.activeFires.find { it.gameObject.id == parentId }
                                val parentObject = game.sceneManager.activeObjects.find { it.id == parentId }

                                if (parentFire != null) {
                                    // The parent is a fire. Use the FireSystem to remove it completely.
                                    game.sceneManager.activeObjects.removeValue(parentFire.gameObject, true)
                                    game.fireSystem.removeFire(parentFire, game.objectSystem, game.lightingManager)
                                    uiManager.showTemporaryMessage("Removed Fire (via its light source)")
                                    return true // Action is handled, consume the click.
                                } else if (parentObject != null) {
                                    // The parent is a generic object (like a lantern). Remove it.
                                    game.objectSystem.removeGameObjectWithLight(parentObject, game.lightingManager)
                                    game.sceneManager.activeObjects.removeValue(parentObject, true)
                                    uiManager.showTemporaryMessage("Removed ${parentObject.objectType.displayName} (via its light source)")
                                    return true // Action is handled, consume the click.
                                }
                            }

                            // If in mission mode, try to remove a preview object first.
                            if (uiManager.currentEditorMode == EditorMode.MISSION) {
                                val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                                if (handleMissionPreviewRemoval(ray)) {
                                    return true // A preview was removed, consume the click.
                                }
                            }

                            if (uiManager.selectedTool == Tool.PLAYER && isPlacingCharacterPath) {
                                characterPathSystem.cancelPlacement()
                                uiManager.updatePlacementInfo("Character Path placement cancelled.")
                                return true
                            }

                            // --- Special Action Canceling (Highest Priority) ---
                            if (uiManager.selectedTool == Tool.CAR && isPlacingCarPath) {
                                carPathSystem.cancelPlacement()
                                return true // Consume this click to prevent camera drag
                            }

                            // Handle cancelling teleporter linking first
                            if (teleporterSystem.isLinkingMode) {
                                teleporterSystem.cancelLinking()
                                return true // Consume the click
                            }
                            if (game.isInspectModeEnabled) {
                            val teleporterGameObjects = Array(teleporterSystem.activeTeleporters.map { it.gameObject }.toTypedArray())
                            val hitTeleporterObject = game.raycastSystem.getObjectAtRay(ray, teleporterGameObjects)

                            if (hitTeleporterObject != null) {
                                val teleporterToEdit = teleporterSystem.activeTeleporters.find { it.gameObject.id == hitTeleporterObject.id.toString() } // MODIFIED: ID is a String
                                if (teleporterToEdit != null) {
                                    // Open the naming/editing dialog in "edit mode"
                                    uiManager.showTeleporterNameDialog(
                                        title = "Edit Teleporter",
                                        teleporter = teleporterToEdit, // Pass the whole object
                                        initialText = teleporterToEdit.name
                                    ) { newName ->
                                        teleporterToEdit.name = newName
                                    }
                                    return true // Consume the click
                                }
                            }

                            // Check for Spawner
                            val spawner = game.raycastSystem.getSpawnerAtRay(ray, sceneManager.activeSpawners)
                            if (spawner != null) {
                                // Right-clicking a spawner now only opens its UI.
                                uiManager.showSpawnerUI(spawner)
                                return true
                            }

                            val enemy = enemySystem.raycastSystem.getEnemyAtRay(ray, sceneManager.activeEnemies)
                            if (enemy != null && uiManager.selectedTool != Tool.ENEMY) {
                                Gdx.app.clipboard.contents = enemy.id
                                uiManager.showTemporaryMessage("Copied Enemy ID: ${enemy.id}")
                                return true // Inspection successful, consume the click and stop everything else.
                            }

                            val npc = npcSystem.raycastSystem.getNPCAtRay(ray, sceneManager.activeNPCs)
                            if (npc != null && uiManager.selectedTool != Tool.NPC) {
                                Gdx.app.clipboard.contents = npc.id
                                uiManager.showTemporaryMessage("Copied NPC ID: ${npc.id}")
                                return true // Inspection successful
                            }

                            val car = carSystem.raycastSystem.getCarAtRay(ray, sceneManager.activeCars)
                            if (car != null && uiManager.selectedTool != Tool.CAR) {
                                Gdx.app.clipboard.contents = car.id
                                uiManager.showTemporaryMessage("Copied Car ID: ${car.id}")
                                return true
                            }

                            // Check for House
                            val house = houseSystem.raycastSystem.getHouseAtRay(ray, sceneManager.activeHouses)
                            if (house != null && uiManager.selectedTool != Tool.HOUSE) {
                                Gdx.app.clipboard.contents = house.id
                                uiManager.showTemporaryMessage("Copied House ID: ${house.id}")
                                return true
                            }

                            // Check for Item
                            val item = itemSystem.raycastSystem.getItemAtRay(ray, sceneManager.activeItems)
                            if (item != null && uiManager.selectedTool != Tool.ITEM) {
                                Gdx.app.clipboard.contents = item.id
                                uiManager.showTemporaryMessage("Copied Item ID: ${item.id}")
                                return true
                            }

                            // Check for Object
                            val obj = objectSystem.raycastSystem.getObjectAtRay(ray, sceneManager.activeObjects)
                            if (obj != null && uiManager.selectedTool != Tool.OBJECT) {
                                Gdx.app.clipboard.contents = obj.id
                                uiManager.showTemporaryMessage("Copied Object ID: ${obj.id}")
                                return true
                            }

                            // 1. Check for Car Path Nodes
                            val carPathNode = game.carPathSystem.findNodeAtRay(ray)
                            if (carPathNode != null) {
                                if (uiManager.selectedTool == Tool.CAR) {
                                    // If Car Tool is active, right-click REMOVES the node.
                                    if (game.carPathSystem.handleRemoveAction(ray)) {
                                        uiManager.updatePlacementInfo("Removed car path node.")
                                        return true // Action handled
                                    }
                                } else {
                                    // If any other tool is active, right-click COPIES the ID.
                                    Gdx.app.clipboard.contents = carPathNode.id
                                    uiManager.showTemporaryMessage("Copied Car Path Node ID: ${carPathNode.id}")
                                    return true // Action handled
                                }
                            }

                            // 2. Check for Character Path Nodes
                            val charPathNode = game.characterPathSystem.findNodeAtRay(ray)
                            if (charPathNode != null) {
                                if (uiManager.selectedTool == Tool.PLAYER) {
                                    // If Player Tool is active, right-click REMOVES the node.
                                    if (game.characterPathSystem.handleRemoveAction(ray)) {
                                        uiManager.updatePlacementInfo("Removed character path node.")
                                        return true // Action handled
                                    }
                                } else {
                                    // If any other tool is active, right-click COPIES the ID.
                                    Gdx.app.clipboard.contents = charPathNode.id
                                    uiManager.showTemporaryMessage("Copied Char Path Node ID: ${charPathNode.id}")
                                    return true // Action handled
                                }
                            }

                            val block = game.raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())
                            if (block != null && uiManager.selectedTool != Tool.BLOCK) {
                                val pos = block.position
                                // Format to 2 decimal places for cleanliness and consistency
                                val posString = String.format(java.util.Locale.US, "%.2f, %.2f, %.2f", pos.x, pos.y, pos.z)
                                Gdx.app.clipboard.contents = posString
                                uiManager.showTemporaryMessage("Copied Block Position: $posString")
                                return true // Consume the click
                            }
                            }
                            var removed = false
                            when (uiManager.selectedTool) {
                                Tool.BLOCK -> removed = blockSystem.handleRemoveAction(ray)
                                Tool.OBJECT -> removed = objectSystem.handleRemoveAction(ray)
                                Tool.ITEM -> removed = itemSystem.handleRemoveAction(ray)
                                Tool.CAR -> removed = carSystem.handleRemoveAction(ray)
                                Tool.PLAYER -> {
                                    if (isPlacingCharacterPath) {
                                        removed = characterPathSystem.handleRemoveAction(ray)
                                    }
                                }
                                Tool.HOUSE -> removed = houseSystem.handleRemoveAction(ray)
                                Tool.BACKGROUND -> removed = backgroundSystem.handleRemoveAction(ray)
                                Tool.PARALLAX -> removed = parallaxSystem.handleRemoveAction(ray)
                                Tool.INTERIOR -> removed = interiorSystem.handleRemoveAction(ray)
                                Tool.ENEMY -> removed = enemySystem.handleRemoveAction(ray)
                                Tool.NPC -> removed = npcSystem.handleRemoveAction(ray)
                                Tool.TRIGGER -> removed = game.triggerSystem.removeTriggerForSelectedMission()
                                Tool.PARTICLE, Tool.AUDIO_EMITTER -> { /* No removal action */ }
                            }

                            if (removed) {
                                isRightMousePressed = true
                                // Reset timer and track position for continuous removal
                                continuousActionTimer = 0f
                                lastRemovalX = screenX
                                lastRemovalY = screenY
                                return true
                            }
                            // No object removed, handle as camera drag
                        }
                        val ray = cameraManager.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                        val spawnerToInspect = game.raycastSystem.getSpawnerAtRay(ray, sceneManager.activeSpawners)

                        // Open Spawner UI if not in debug mode
                        if (spawnerToInspect != null && !objectSystem.debugMode) {
                            uiManager.showSpawnerUI(spawnerToInspect)
                            return true // Consume the click
                        }

                        isRightMousePressed = true
                        lastMouseX = screenX.toFloat()
                        lastMouseY = screenY.toFloat()
                        return true
                    }
                }
                return false
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                when (button) {
                    Input.Buttons.LEFT -> isLeftMousePressed = false
                    Input.Buttons.RIGHT -> isRightMousePressed = false
                }
                return true
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                // Also update the preview when dragging
                handleBackgroundPreviewUpdate(screenX, screenY)

                if (isRightMousePressed && !isBlockBeingRemoved()) {
                    // Only handle camera drag if we're not in block removal mode
                    val deltaX = screenX - lastMouseX
                    cameraManager.handleMouseDrag(deltaX)
                    lastMouseX = screenX.toFloat()
                    lastMouseY = screenY.toFloat()
                    return true
                }
                return false
            }

            override fun scrolled(amountX: Float, amountY: Float): Boolean {
                if (uiManager.isPlacingObjectiveArea) {
                    val objective = uiManager.objectiveBeingPlaced ?: return true
                    val condition = objective.completionCondition

                    var currentRadius = condition.areaRadius ?: 10f
                    val step = if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) 5.0f else 1.0f
                    val change = -amountY * step
                    currentRadius = (currentRadius + change).coerceAtLeast(1.0f)

                    // Create a new objective with the updated radius
                    val newCondition = condition.copy(areaRadius = currentRadius)
                    val updatedObjective = objective.copy(completionCondition = newCondition)

                    // Pass the entire updated objective back to the UIManager
                    uiManager.updateObjectiveAreaFromVisuals(updatedObjective)
                    return true
                }

                if (uiManager.selectedTool == Tool.TRIGGER && game.lastPlacedInstance is MissionTrigger) {
                    val trigger = game.lastPlacedInstance as MissionTrigger
                    val step = if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) 5.0f else 1.0f // Hold shift for bigger steps
                    val change = -amountY * step // Invert scroll direction

                    trigger.areaRadius = (trigger.areaRadius + change).coerceAtLeast(1.0f) // Don't allow radius < 1

                    // We need to tell the UIManager to update the text field
                    uiManager.updateTriggerRadiusField(trigger.areaRadius)

                    println("Trigger radius changed to: ${trigger.areaRadius}")
                    return true // Consume the scroll event
                }
                // Check if block selection mode is active
                if (isBlockSelectionMode) {
                    // Use mouse scroll to change blocks
                    if (amountY > 0) blockSystem.nextBlock() else blockSystem.previousBlock()
                    uiManager.updateBlockSelection()
                    return true
                } else if (isObjectSelectionMode) {
                    // Use mouse scroll to change objects
                    if (amountY > 0) objectSystem.nextObject() else objectSystem.previousObject()
                    uiManager.updateObjectSelection()
                    return true
                } else if (isItemSelectionMode) {
                    // Use mouse scroll to change items
                    if (amountY > 0) itemSystem.nextItem() else itemSystem.previousItem()
                    uiManager.updateItemSelection()
                    return true
                } else if (isCarSelectionMode) {
                    // Use mouse scroll to change cars
                    if (amountY > 0) carSystem.nextCar() else carSystem.previousCar()
                    uiManager.updateCarSelection()
                    return true
                } else if (isHouseSelectionMode) {
                    // Use mouse scroll to change houses
                    if (amountY > 0) houseSystem.nextHouse() else houseSystem.previousHouse()
                    uiManager.updateHouseSelection()
                    return true
                } else if (isBackgroundSelectionMode) {
                    if (amountY > 0) backgroundSystem.nextBackground() else backgroundSystem.previousBackground()
                    uiManager.updateBackgroundSelection()
                    return true
                } else if (isParallaxSelectionMode) {
                    if (amountY > 0) uiManager.nextParallaxImage() else uiManager.previousParallaxImage()
                    return true
                } else if (isInteriorSelectionMode) {
                    if (amountY > 0) interiorSystem.nextInterior() else interiorSystem.previousInterior()
                    uiManager.updateInteriorSelection()
                    return true
                } else if (isEnemySelectionMode) {
                    when {
                        // Ctrl + Wheel: Change "Out of Ammo" tactic
                        Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT) -> {
                            if (amountY > 0) uiManager.nextEmptyAmmoTactic() else uiManager.prevEmptyAmmoTactic()
                        }
                        // Shift + Wheel: Change "Primary" tactic
                        Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT) -> {
                            if (amountY > 0) uiManager.nextPrimaryTactic() else uiManager.prevPrimaryTactic()
                        }
                        // Scroll through enemy types
                        else -> {
                            if (amountY > 0) enemySystem.nextEnemyType() else enemySystem.previousEnemyType()
                        }
                    }
                    uiManager.updateEnemySelection() // Update the UI to reflect the change
                    return true
                } else if (isNPCSelectionMode) {
                    if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                        if (amountY > 0) npcSystem.nextBehavior() else npcSystem.previousBehavior()
                    } else {
                        if (amountY > 0) npcSystem.nextNPCType() else npcSystem.previousNPCType()
                    }
                    uiManager.updateNPCSelection()
                    return true
                } else if (isParticleSelectionMode) { // ADD THIS BLOCK
                    if (amountY > 0) particleSystem.nextEffect() else particleSystem.previousEffect()
                    uiManager.updateParticleSelection()
                    return true
                } else {
                    // Normal camera zoom
                    cameraManager.handleMouseScroll(amountY)
                    return true
                }
            }

            override fun keyDown(keycode: Int): Boolean {
                // Handle respawn input, which should work even if other UI is active
                if (keycode == Input.Keys.R && game.playerSystem.isDead()) {
                    game.playerSystem.respawn()
                    return true // Consume the input
                }

                // If the user is typing in a UI text field, DO NOT process any game keybinds.
                if (uiManager.getStage().keyboardFocus != null) {
                    return false
                }

                if (uiManager.isDialogActive()) {
                    when (keycode) {
                        Input.Keys.E -> {
                            uiManager.dialogSystem.handleInput()
                            return true // Consume the input
                        }
                        Input.Keys.ESCAPE -> {
                            uiManager.dialogSystem.skipAll()
                            return true // Consume the input
                        }
                    }
                    // Don't process other keys if dialog is active
                    return true
                }

                if (uiManager.isPlacingObjectiveArea) {
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.ESCAPE) {
                        uiManager.exitObjectiveAreaPlacementMode()
                        return true
                    }
                }

                // Camera Flip Hotkey
                if (keycode == Input.Keys.TAB) {
                    // Only flip if we are in player camera mode
                    if (!cameraManager.isFreeCameraMode) {
                        cameraManager.flipCamera()
                        return true // Consume the key press
                    }
                }

                if (keycode == Input.Keys.ESCAPE) {
                    // Check for cancelling entry point placement
                    if (uiManager.isPlacingEntryPointMode) {
                        uiManager.exitEntryPointPlacementMode()
                        return true
                    }

                    if (blockSystem.isAreaFillModeActive) {
                        blockSystem.cancelAreaFill()
                        return true
                    }
                    if (uiManager.selectedTool == Tool.PLAYER && isPlacingCharacterPath) {
                        characterPathSystem.cancelPlacement()
                        uiManager.updatePlacementInfo("Character Path placement cancelled.")
                        return true
                    }

                    if (uiManager.selectedTool == Tool.CAR && isPlacingCarPath) {
                        carPathSystem.cancelPlacement()
                        // Give user feedback that the action was cancelled
                        uiManager.updatePlacementInfo("Line placement cancelled.")
                        return true // Consume the key press to prevent opening the pause menu
                    }

                    // First, handle cancelling any ongoing actions
                    if (game.teleporterSystem.isLinkingMode) {
                        game.teleporterSystem.cancelLinking()
                        return true
                    }
                    // If no actions to cancel, toggle the pause menu.
                    uiManager.togglePauseMenu()
                    return true
                }

                if (keycode == Input.Keys.F12) {
                    BillboardShader.DEBUG_SHADER_INFO = !BillboardShader.DEBUG_SHADER_INFO
                    val status = if (BillboardShader.DEBUG_SHADER_INFO) "ON" else "OFF"
                    uiManager.updatePlacementInfo("Shader Debug Info: $status")
                    return true
                }

                if (uiManager.isPauseMenuVisible()) {
                    return true
                }

                if (keycode == Input.Keys.F11) {
                    uiManager.toggleFpsLabel()
                    return true
                }

                if (keycode == Input.Keys.Z) {
                    if (!game.isEditorMode) {
                        game.targetingIndicatorSystem.toggle()
                        val status = if (game.targetingIndicatorSystem.isEnabled()) "ON" else "OFF"
                        // Provide feedback to the player via the UI
                        uiManager.updatePlacementInfo("Targeting Indicator: $status")
                        return true // Consume the key press
                    }
                }

                if (keycode == Input.Keys.F8) {
                    game.toggleEditorMode()
                    val modeStatus = if (game.isEditorMode) "EDITOR" else "GAME"
                    uiManager.updatePlacementInfo("Mode switched to: $modeStatus")
                    return true
                }

                if (!game.isEditorMode) {
                    when (keycode) {
//                        Input.Keys.Q -> {
//                            // Drop the current weapon
//                            game.playerSystem.dropEquippedWeapon(sceneManager, itemSystem)
//                            return true
//                        }
                        Input.Keys.L -> {
                            // Check if the player is currently driving a car
                            if (game.playerSystem.isDriving) {
                                val car = game.playerSystem.drivingCar
                                if (car != null && !car.isDestroyed) {
                                    car.areHeadlightsOn = !car.areHeadlightsOn
                                    println("Player toggled headlights ${if (car.areHeadlightsOn) "ON" else "OFF"}.")
                                    return true // Consume the key press
                                }
                            }
                        }
                    }
                }

                // EDITOR MODE CHECK
                if (game.isEditorMode) {
                    if (keycode == Input.Keys.NUM_8) {
                        uiManager.selectedTool = Tool.AUDIO_EMITTER
                    }
                    if (keycode == Input.Keys.NUM_3) { // Increase Rain
                        val currentIntensity = game.weatherSystem.getRainIntensity()
                        val newIntensity = (currentIntensity + 0.1f).coerceIn(0f, 1f)
                        game.weatherSystem.setMissionWeather(intensity = newIntensity)
                        uiManager.showTemporaryMessage("Rain Intensity Set: %.1f".format(newIntensity))
                        return true
                    }
                    if (keycode == Input.Keys.NUM_2) { // Decrease Rain
                        val currentIntensity = game.weatherSystem.getRainIntensity()
                        val newIntensity = (currentIntensity - 0.1f).coerceIn(0f, 1f)
                        game.weatherSystem.setMissionWeather(intensity = newIntensity)
                        uiManager.showTemporaryMessage("Rain Intensity Set: %.1f".format(newIntensity))
                        return true
                    }
                    if (keycode == Input.Keys.NUM_1) { // Clear weather override and return to random weather
                        game.weatherSystem.clearMissionOverride()
                        uiManager.showTemporaryMessage("Weather control returned to random system.")
                        return true
                    }

                    if (keycode == Input.Keys.F7) {
                        uiManager.toggleMissionEditor()
                        return true
                    }
                    if (keycode == Input.Keys.F9) {
                        uiManager.toggleDialogueEditor()
                        return true
                    }

                    if (keycode == Input.Keys.NUM_4) {
                        game.isInspectModeEnabled = !game.isInspectModeEnabled
                        val status = if (game.isInspectModeEnabled) "ON" else "OFF"
                        uiManager.updatePlacementInfo("Inspect Mode (Right-Click Copy): $status")
                        return true
                    }

                    if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
                        if (keycode == Input.Keys.S) {
                            // Save Template Hotkey
                            uiManager.showSaveRoomDialog(sceneManager)
                            return true // Consume the input
                        } else if (keycode == Input.Keys.L) {
                            // Context 1: Player tool is active and we are in character path mode
                            if (uiManager.selectedTool == Tool.PLAYER && isPlacingCharacterPath) {
                                characterPathSystem.isPlacingMissionOnly = !characterPathSystem.isPlacingMissionOnly
                                val status = if (characterPathSystem.isPlacingMissionOnly) "Mission Only" else "World Path"
                                uiManager.updatePlacementInfo("Character Path Scope: $status")
                            }
                            // Context 2: Car tool is active
                            else if (uiManager.selectedTool == Tool.CAR) {
                                // CONTEXT 1: Car tool is active, so we toggle path placement.
                                isPlacingCarPath = !isPlacingCarPath
                                val mode = if (isPlacingCarPath) "Car Path" else "Car Placement"
                                uiManager.updatePlacementInfo("Car Tool Mode: $mode")
                                if (!isPlacingCarPath) carPathSystem.cancelPlacement()
                            }
                            // Context 3: Default action (load room template)
                            else {
                                val firstTemplate = roomTemplateManager.getAllTemplates().firstOrNull()
                                if (firstTemplate != null) {
                                    sceneManager.loadTemplateIntoCurrentInterior(firstTemplate.id)
                                    uiManager.updatePlacementInfo("Loaded template: ${firstTemplate.name}")
                                } else {
                                    uiManager.updatePlacementInfo("No saved templates to load!")
                                }
                            }
                            return true // Consume the input, regardless of which action was taken
                        }
                    }

                    if (isHouseSelectionMode) {
                        when (keycode) {
                            Input.Keys.UP -> {
                                uiManager.navigateHouseRooms(-1)
                                return true
                            }
                            Input.Keys.DOWN -> {
                                uiManager.navigateHouseRooms(1)
                                return true
                            }
                            Input.Keys.ENTER -> {
                                uiManager.selectHouseRoom()
                                return true
                            }
                        }
                    }

                    when (keycode) {
                        Input.Keys.F1 -> {
                            uiManager.toggleVisibility()
                            return true
                        }
                        Input.Keys.F2 -> {
                            shaderEffectManager.nextEffect()
                            uiManager.updatePlacementInfo("Shader Effect: ${shaderEffectManager.getCurrentEffect().displayName}")
                            return true
                        }
                        Input.Keys.F3 -> {
                            shaderEffectManager.previousEffect()
                            uiManager.updatePlacementInfo("Shader Effect: ${shaderEffectManager.getCurrentEffect().displayName}")
                            return true
                        }
                        Input.Keys.F4 -> {
                            uiManager.toggleShaderEffectUI()
                            return true
                        }
                        Input.Keys.F5 -> {
                            val isBright = game.lightingManager.toggleBuildModeBrightness()
                            val status = if (isBright) "ON" else "OFF"
                            uiManager.updatePlacementInfo("Build Mode Brightness: $status")
                            return true
                        }
                        Input.Keys.F6 -> {
                            // Only allow this if the block tool is active
                            if (uiManager.selectedTool == Tool.BLOCK) {
                                val status = blockSystem.toggleAreaFillMode()
                                uiManager.updatePlacementInfo(status)
                            }
                            return true
                        }
                        Input.Keys.K -> {
                            uiManager.toggleSkyCustomizationUI()
                            return true
                        }
                        Input.Keys.C -> {
                            cameraManager.toggleFreeCameraMode()
                            return true
                        }
                        Input.Keys.G -> {
                            if (game.isEditorMode) {
                                if (uiManager.selectedTool == Tool.CAR) {
                                    carPathSystem.toggleVisibility()
                                    val status = if (carPathSystem.isVisible) "ON" else "OFF"
                                    uiManager.updatePlacementInfo("Car Path Visibility: $status")
                                    return true
                                }
                                if (uiManager.selectedTool == Tool.PLAYER) {
                                    characterPathSystem.isVisible = !characterPathSystem.isVisible
                                    val status = if (characterPathSystem.isVisible) "ON" else "OFF"
                                    uiManager.updatePlacementInfo("Character Path Visibility: $status")
                                    return true
                                }
                                if (uiManager.selectedTool == Tool.AUDIO_EMITTER) {
                                    audioEmitterSystem.toggleVisibility()
                                    return true
                                }

                                val shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
                                if (shiftPressed) {
                                    if (uiManager.selectedTool == Tool.OBJECT) {
                                        game.lightingManager.toggleLightAreaVisibility()
                                        val status = if (game.lightingManager.isLightAreaVisible) "ON" else "OFF"
                                        uiManager.updatePlacementInfo("Light Area Visibility: $status")
                                        return true // Consume the input so the original 'G' action doesn't run
                                    }
                                }
                                if (uiManager.selectedTool == Tool.BLOCK) {
                                    // If the block tool is active, toggle the collision wireframes
                                    game.toggleBlockCollisionOutlines()
                                } else {
                                    // Otherwise, perform the original debug action for objects and invisible blocks
                                    objectSystem.toggleDebugMode()
                                    game.toggleInvisibleBlockOutlines()
                                }
                                return true
                            }
                            return false
                        }

                        // Tool-specific hotkeys
                        Input.Keys.V -> {
                            // Only cycle area when the block tool is active
                            if (uiManager.selectedTool == Tool.BLOCK) {
                                if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                                    // SHIFT+V cycles camera visibility
                                    blockSystem.nextCameraVisibility()
                                } else {
                                    // V alone cycles build area
                                    blockSystem.nextBuildMode()
                                }
                                uiManager.updateBlockSelection() // Update UI to show the new mode
                                return true
                            }
                            return false // Not in block mode, let other systems handle 'V' if needed
                        }
                        Input.Keys.T -> {
                            if (isBlockSelectionMode) {  // Only cycle shapes when in block mode
                                if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
                                    blockSystem.previousShape()
                                } else {
                                    blockSystem.nextShape()
                                }
                                uiManager.updateBlockSelection() // Update UI to show the new shape
                                return true
                            }
                            return false
                        }
                        Input.Keys.R -> {
                            // Also has multiple contexts now
                            if (uiManager.selectedTool == Tool.PLAYER && isPlacingCharacterPath) {
                                characterPathSystem.isPlacingOneWay = !characterPathSystem.isPlacingOneWay
                                val status = if (characterPathSystem.isPlacingOneWay) "One-Way" else "Bi-Directional"
                                uiManager.updatePlacementInfo("Character Path Type: $status")
                                return true
                            }
                            if (uiManager.selectedTool == Tool.CAR && carPathSystem.isInPlacementMode) {
                                val status = carPathSystem.toggleDirectionFlip()
                                uiManager.updatePlacementInfo(status)
                                return true
                            }
                            if (isBlockSelectionMode) {
                                blockSystem.toggleRotationMode()
                                uiManager.updateBlockSelection()
                                return true
                            }
                            if (isInteriorSelectionMode) {
                                interiorSystem.rotateSelection()
                                uiManager.updateInteriorSelection()
                                return true
                            }
                        }
                        Input.Keys.Q, Input.Keys.E -> {
                            val reverse = (keycode == Input.Keys.E)
                            when {
                                uiManager.selectedTool == Tool.HOUSE -> {
                                    houseSystem.rotateSelection()
                                    uiManager.updatePlacementInfo("House Rotation: ${houseSystem.currentRotation}°")
                                }
                                uiManager.selectedTool == Tool.NPC -> {
                                    npcSystem.toggleRotation()
                                    val direction = if (npcSystem.currentRotation == 0f) "Right" else "Left"
                                    uiManager.updatePlacementInfo("NPC will face: $direction")
                                }
                                uiManager.selectedTool == Tool.BLOCK || isBlockSelectionMode -> {
                                    if (reverse) blockSystem.rotateCurrentBlockReverse() else blockSystem.rotateCurrentBlock()
                                    uiManager.updateBlockSelection()
                                }
                            }
                            return true
                        }

                        Input.Keys.B -> { if (!isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode) { isBlockSelectionMode = true; uiManager.showBlockSelection(); }; return true }
                        Input.Keys.O -> { if (!isObjectSelectionMode && !isBlockSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode) { isObjectSelectionMode = true; uiManager.showObjectSelection(); }; return true }
                        Input.Keys.I -> { if (!isItemSelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode) { isItemSelectionMode = true; uiManager.showItemSelection(); }; return true }
                        Input.Keys.M -> { if (!isCarSelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode) { isCarSelectionMode = true; uiManager.showCarSelection(); }; return true }
                        Input.Keys.H -> { if (!isHouseSelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isBackgroundSelectionMode) { isHouseSelectionMode = true; uiManager.showHouseSelection(); }; return true }
                        Input.Keys.N -> {
                            when {
                                uiManager.selectedTool == Tool.PLAYER && isPlacingCharacterPath -> {
                                    characterPathSystem.startNewPath()
                                    uiManager.updatePlacementInfo("Started new character path segment.")
                                }
                                uiManager.selectedTool == Tool.CAR && isPlacingCarPath -> {
                                    carPathSystem.startNewPath()
                                    uiManager.updatePlacementInfo("Started new car path segment.")
                                }
                                !isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode -> {
                                    isBackgroundSelectionMode = true
                                    uiManager.showBackgroundSelection()
                                }
                            }
                            return true
                        }
                        Input.Keys.J -> { if (!isInteriorSelectionMode && !isItemSelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode) { isInteriorSelectionMode = true; uiManager.showInteriorSelection(); }; return true }
                        Input.Keys.Y -> { if (!isEnemySelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode && !isInteriorSelectionMode && !isNPCSelectionMode) { isEnemySelectionMode = true; uiManager.showEnemySelection(); }; return true }
                        Input.Keys.U -> { if (!isNPCSelectionMode && !isEnemySelectionMode && !isBlockSelectionMode&& !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode && !isInteriorSelectionMode) { isNPCSelectionMode = true; uiManager.showNPCSelection(); }; return true }
                        Input.Keys.X -> { if (!isParticleSelectionMode && !isNPCSelectionMode && !isEnemySelectionMode && !isBlockSelectionMode && !isObjectSelectionMode && !isItemSelectionMode && !isCarSelectionMode && !isHouseSelectionMode && !isBackgroundSelectionMode && !isInteriorSelectionMode) { isParticleSelectionMode = true; uiManager.showParticleSelection(); }; return true }

                        // Other special keys
                        Input.Keys.L -> {
                            if (uiManager.selectedTool == Tool.PLAYER) {
                                isPlacingCharacterPath = !isPlacingCharacterPath
                                isPlacingCarPath = false
                                val mode = if (isPlacingCharacterPath) "Path Placement" else "Player Spawn"
                                uiManager.updatePlacementInfo("Player Tool Mode: $mode")
                                if (!isPlacingCharacterPath) characterPathSystem.cancelPlacement()
                                return true
                            }
                            if (uiManager.selectedTool == Tool.CAR && carPathSystem.isInPlacementMode) {
                                val status = carPathSystem.cycleDirectionality()
                                uiManager.updatePlacementInfo(status)
                                return true
                            }
                            if (isHouseSelectionMode) { houseSystem.toggleLockState(); uiManager.updateHouseSelection(); return true }
                            if (isCarSelectionMode) { carSystem.toggleLockState(); uiManager.updateCarSelection(); return true }
                            if (isParallaxSelectionMode) { uiManager.nextParallaxLayer(); return true }
                            uiManager.toggleLightSourceUI()
                            return true
                        }
                        // Key to cycle background placement modes
                        Input.Keys.P -> {
                            // If parallax tool is active, this key now shows the parallax selection UI
                            if (uiManager.selectedTool == Tool.PARALLAX) { if (!isParallaxSelectionMode) { isParallaxSelectionMode = true; uiManager.showParallaxSelection(); }; return true }
                            if (uiManager.selectedTool == Tool.BACKGROUND) { backgroundSystem.cyclePlacementMode(); handleBackgroundPreviewUpdate(Gdx.input.x, Gdx.input.y); }
                            return true
                        }
                        Input.Keys.F -> { getCurrentPositionableSystem()?.toggleFinePosMode(); return true }

                        // Numpad tool selection
                        Input.Keys.NUMPAD_1 -> uiManager.selectedTool = Tool.BLOCK
                        Input.Keys.NUMPAD_2 -> uiManager.selectedTool = Tool.PLAYER
                        Input.Keys.NUMPAD_3 -> uiManager.selectedTool = Tool.OBJECT
                        Input.Keys.NUMPAD_4 -> uiManager.selectedTool = Tool.ITEM
                        Input.Keys.NUMPAD_5 -> uiManager.selectedTool = Tool.CAR
                        Input.Keys.NUMPAD_6 -> uiManager.selectedTool = Tool.HOUSE
                        Input.Keys.NUMPAD_7 -> uiManager.selectedTool = Tool.BACKGROUND
                        Input.Keys.NUMPAD_8 -> uiManager.selectedTool = Tool.PARALLAX
                        Input.Keys.NUMPAD_9 -> uiManager.selectedTool = Tool.INTERIOR
                        Input.Keys.NUMPAD_0 -> uiManager.selectedTool = Tool.ENEMY
                        Input.Keys.NUM_7 -> uiManager.selectedTool = Tool.NPC
                        Input.Keys.NUM_6 -> uiManager.selectedTool = Tool.PARTICLE
                        Input.Keys.NUM_5 -> uiManager.selectedTool = UIManager.Tool.TRIGGER
                        // Fine positioning controls
                        Input.Keys.LEFT -> { if (getCurrentPositionableSystem()?.finePosMode == true) { leftPressed = true; continuousFineTimer = 0f; return true } }
                        Input.Keys.RIGHT -> { if (getCurrentPositionableSystem()?.finePosMode == true) { rightPressed = true; continuousFineTimer = 0f; return true } }
                        Input.Keys.UP -> { if (getCurrentPositionableSystem()?.finePosMode == true) { upPressed = true; continuousFineTimer = 0f; return true } }
                        Input.Keys.DOWN -> { if (getCurrentPositionableSystem()?.finePosMode == true) { downPressed = true; continuousFineTimer = 0f; return true } }
                        Input.Keys.NUM_0 -> { if (getCurrentPositionableSystem()?.finePosMode == true) { pageUpPressed = true; continuousFineTimer = 0f; return true } }
                        Input.Keys.NUM_9 -> { if (getCurrentPositionableSystem()?.finePosMode == true) { pageDownPressed = true; continuousFineTimer = 0f; return true } }

                        // Gameplay/Debug controls
                        Input.Keys.COMMA -> { isTimeSpeedUpPressed = true; return true }
                    }

                    // Update UI after tool selection
                    if (keycode in Input.Keys.NUMPAD_0..Input.Keys.NUMPAD_9 || keycode == Input.Keys.NUM_6 || keycode == Input.Keys.NUM_7 || keycode == Input.Keys.NUM_5) {
                        // When switching tools, always reset to car placement mode
                        if (uiManager.selectedTool != Tool.CAR && isPlacingCarPath) {
                            isPlacingCarPath = false
                            carPathSystem.cancelPlacement() // Cancel any pending line
                        }
                        // ALSO cancel character path placement when switching away from Player tool
                        if (uiManager.selectedTool != Tool.PLAYER && isPlacingCharacterPath) {
                            isPlacingCharacterPath = false
                            characterPathSystem.cancelPlacement()
                        }
                        uiManager.updateToolDisplay()
                        // Update preview in case we switched to/from the background tool
                        handleBackgroundPreviewUpdate(Gdx.input.x, Gdx.input.y)
                    }
                }
                return false
            }

            override fun keyUp(keycode: Int): Boolean {
                when (keycode) {
                    Input.Keys.B -> { isBlockSelectionMode = false; uiManager.hideBlockSelection(); return true }
                    Input.Keys.O -> { isObjectSelectionMode = false; uiManager.hideObjectSelection(); return true }
                    Input.Keys.I -> { isItemSelectionMode = false; uiManager.hideItemSelection(); return true }
                    Input.Keys.M -> { isCarSelectionMode = false; uiManager.hideCarSelection(); return true }
                    Input.Keys.H -> { isHouseSelectionMode = false; uiManager.hideHouseSelection(); return true }
                    Input.Keys.N -> { isBackgroundSelectionMode = false; uiManager.hideBackgroundSelection(); return true }
                    Input.Keys.P -> {
                        if (isParallaxSelectionMode) {
                            isParallaxSelectionMode = false
                            uiManager.hideParallaxSelection()
                            return true
                        }
                    }
                    Input.Keys.J -> { isInteriorSelectionMode = false; uiManager.hideInteriorSelection(); return true }
                    Input.Keys.Y -> { isEnemySelectionMode = false; uiManager.hideEnemySelection(); return true }
                    Input.Keys.X -> { isParticleSelectionMode = false; uiManager.hideParticleSelection(); return true }
                    Input.Keys.U -> { isNPCSelectionMode = false; uiManager.hideNPCSelection(); return true }
                    // Release fine positioning keys
                    Input.Keys.LEFT -> { leftPressed = false; return true }
                    Input.Keys.RIGHT -> { rightPressed = false; return true }
                    Input.Keys.UP -> { upPressed = false; return true }
                    Input.Keys.DOWN -> { downPressed = false; return true }
                    Input.Keys.NUM_0 -> { pageUpPressed = false; return true }
                    Input.Keys.NUM_9 -> { pageDownPressed = false; return true }
                    Input.Keys.COMMA -> { isTimeSpeedUpPressed = false;return true }
                }
                return false
            }
        })
        Gdx.input.inputProcessor = inputMultiplexer
    }

    fun update(deltaTime: Float) {
        if (uiManager.getStage().keyboardFocus != null) {
            return
        }

        if (game.isEditorMode) {
            continuousActionTimer += deltaTime
            continuousFineTimer += deltaTime

            // Handle continuous block placement
            if (isLeftMousePressed && continuousActionTimer >= continuousActionDelay) {
                val currentMouseX = Gdx.input.x
                val currentMouseY = Gdx.input.y

                // Only place if mouse has moved or enough time has passed
                if (currentMouseX != lastPlacementX || currentMouseY != lastPlacementY) {
                    val ray = cameraManager.camera.getPickRay(currentMouseX.toFloat(), currentMouseY.toFloat())
                    when (uiManager.selectedTool) {
                        Tool.BLOCK -> blockSystem.handlePlaceAction(ray)
                        Tool.PLAYER -> game.playerSystem.placePlayer(ray, sceneManager)
                        Tool.OBJECT -> objectSystem.handlePlaceAction(ray, objectSystem.currentSelectedObject)
                        Tool.ITEM -> itemSystem.handlePlaceAction(ray)
                        Tool.CAR -> {
                            if (isPlacingCarPath) {
                                carPathSystem.handlePlaceAction(ray)
                            } else {
                                carSystem.handlePlaceAction(ray)
                            }
                        }
                        Tool.HOUSE -> houseSystem.handlePlaceAction(ray)
                        Tool.BACKGROUND -> backgroundSystem.handlePlaceAction(ray)
                        Tool.PARALLAX -> parallaxSystem.handlePlaceAction(ray)
                        Tool.INTERIOR -> interiorSystem.handlePlaceAction(ray)
                        Tool.ENEMY -> enemySystem.handlePlaceAction(ray)
                        Tool.NPC -> npcSystem.handlePlaceAction(ray)
                        Tool.PARTICLE -> particleSystem.handlePlaceAction(ray)
                        Tool.TRIGGER -> {
                            val intersection = Vector3()
                            if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
                                game.triggerSystem.selectedMissionIdForEditing?.let { missionId ->
                                    val mission = game.missionSystem.getMissionDefinition(missionId)
                                    if (mission != null) {
                                        mission.startTrigger.areaCenter.set(intersection)
                                        game.missionSystem.saveMission(mission)
                                        uiManager.updatePlacementInfo("Set trigger for '${mission.title}'")
                                    }
                                }
                            }
                        }
                        Tool.AUDIO_EMITTER -> audioEmitterSystem.handlePlaceAction(ray)
                    }
                    lastPlacementX = currentMouseX
                    lastPlacementY = currentMouseY
                    continuousActionTimer = 0f
                }
            }

            // Handle continuous block removal
            if (isRightMousePressed && isBlockBeingRemoved() && continuousActionTimer >= continuousActionDelay) {
                val currentMouseX = Gdx.input.x
                val currentMouseY = Gdx.input.y

                // Only remove if mouse has moved or enough time has passed
                if (currentMouseX != lastRemovalX || currentMouseY != lastRemovalY) {
                    val ray = cameraManager.camera.getPickRay(currentMouseX.toFloat(), currentMouseY.toFloat())
                    var removed = false
                    when (uiManager.selectedTool) {
                        Tool.BLOCK -> removed = blockSystem.handleRemoveAction(ray)
                        Tool.OBJECT -> removed = objectSystem.handleRemoveAction(ray)
                        Tool.ITEM -> removed = itemSystem.handleRemoveAction(ray)
                        Tool.CAR -> removed = carSystem.handleRemoveAction(ray)
                        Tool.HOUSE -> removed = houseSystem.handleRemoveAction(ray)
                        Tool.BACKGROUND -> removed = backgroundSystem.handleRemoveAction(ray)
                        Tool.PARALLAX -> removed = parallaxSystem.handleRemoveAction(ray)
                        Tool.INTERIOR -> removed = interiorSystem.handleRemoveAction(ray)
                        Tool.ENEMY -> removed = enemySystem.handleRemoveAction(ray)
                        Tool.NPC -> removed = npcSystem.handleRemoveAction(ray)
                        Tool.TRIGGER -> removed = game.triggerSystem.removeTriggerForSelectedMission()
                        Tool.PLAYER, UIManager.Tool.PARTICLE, UIManager.Tool.AUDIO_EMITTER -> { /* No continuous removal action */ }
                    }

                    if (removed) {
                        lastRemovalX = currentMouseX
                        lastRemovalY = currentMouseY
                        continuousActionTimer = 0f
                    }
                }
            }

            // Handle continuous fine positioning
            if (continuousFineTimer >= continuousFineDelay) {
                var deltaX = 0f
                var deltaY = 0f
                var deltaZ = 0f

                if (leftPressed) deltaX -= getCurrentFineStep()
                if (rightPressed) deltaX += getCurrentFineStep()
                if (downPressed) deltaZ += getCurrentFineStep()
                if (upPressed) deltaZ -= getCurrentFineStep()
                if (pageDownPressed) deltaY -= getCurrentFineStep()
                if (pageUpPressed) deltaY += getCurrentFineStep()

                // Only call if there's actual movement
                if (deltaX != 0f || deltaY != 0f || deltaZ != 0f) {
                    game.handleFinePosMove(deltaX, deltaY, deltaZ)
                    continuousFineTimer = 0f
                }
            }
        }
    }

    // Helper method to determine if we're in block removal mode vs camera drag mode
    private fun isBlockBeingRemoved(): Boolean {
        // We're in block removal mode if the last removal position is set
        return lastRemovalX != -1 && lastRemovalY != -1
    }

    private fun getCurrentFineStep(): Float {
        return getCurrentPositionableSystem()?.fineStep ?: 0.25f
    }

    private fun getCurrentPositionableSystem(): IFinePositionable? {
        return when (uiManager.selectedTool) {
            Tool.OBJECT -> {
                // Check if the selected object is a teleporter
                if (objectSystem.currentSelectedObject == ObjectType.TELEPORTER) {
                    teleporterSystem // If so, return the TeleporterSystem
                } else {
                    objectSystem // Otherwise, return the normal
                }
            }
            Tool.CAR -> carSystem
            Tool.HOUSE -> houseSystem
            Tool.ITEM -> itemSystem
            Tool.BACKGROUND -> backgroundSystem
            Tool.INTERIOR -> interiorSystem
            Tool.ENEMY -> enemySystem
            Tool.NPC -> npcSystem
            else -> null
        }
    }

    private fun handleMissionPreviewRemoval(ray: Ray): Boolean {
        val mission = uiManager.selectedMissionForEditing ?: return false

        // Check for each type of preview object
        val enemyToRemove = game.sceneManager.raycastSystem.getPreviewEnemyAtRay(ray, game.sceneManager.activeMissionPreviewEnemies)
        if (enemyToRemove != null) {
            mission.eventsOnStart.removeAll { it.targetId == enemyToRemove.id }
            game.sceneManager.activeMissionPreviewEnemies.removeValue(enemyToRemove, true)
            game.missionSystem.saveMission(mission)
            uiManager.updatePlacementInfo("Removed SPAWN_ENEMY from '${mission.title}'")
            return true
        }

        val npcToRemove = game.sceneManager.raycastSystem.getPreviewNpcAtRay(ray, game.sceneManager.activeMissionPreviewNPCs)
        if (npcToRemove != null) {
            mission.eventsOnStart.removeAll { it.targetId == npcToRemove.id }
            game.sceneManager.activeMissionPreviewNPCs.removeValue(npcToRemove, true)
            game.missionSystem.saveMission(mission)
            uiManager.updatePlacementInfo("Removed SPAWN_NPC from '${mission.title}'")
            return true
        }

        val carToRemove = game.sceneManager.raycastSystem.getPreviewCarAtRay(ray, game.sceneManager.activeMissionPreviewCars)
        if (carToRemove != null) {
            mission.eventsOnStart.removeAll { it.targetId == carToRemove.id }
            game.sceneManager.activeMissionPreviewCars.removeValue(carToRemove, true)

            // Also remove any associated driver from the preview
            carToRemove.seats.forEach { seat ->
                when (val occupant = seat.occupant) {
                    is GameEnemy -> game.sceneManager.activeMissionPreviewEnemies.removeValue(occupant, true)
                    is GameNPC -> game.sceneManager.activeMissionPreviewNPCs.removeValue(occupant, true)
                }
            }

            game.missionSystem.saveMission(mission)
            uiManager.updatePlacementInfo("Removed SPAWN_CAR from '${mission.title}'")
            return true
        }

        val itemToRemove = game.sceneManager.raycastSystem.getPreviewItemAtRay(ray, game.sceneManager.activeMissionPreviewItems)
        if (itemToRemove != null) {
            // Items don't have IDs, so we remove based on position and type
            val removed = mission.eventsOnStart.removeAll {
                it.itemType == itemToRemove.itemType && it.spawnPosition?.epsilonEquals(itemToRemove.position, 0.1f) == true
            }
            if (removed) {
                game.sceneManager.activeMissionPreviewItems.removeValue(itemToRemove, true)
                game.missionSystem.saveMission(mission)
                uiManager.updatePlacementInfo("Removed SPAWN_ITEM from '${mission.title}'")
                return true
            }
        }

        return false
    }

    fun isTimeSpeedUpActive(): Boolean = isTimeSpeedUpPressed
}
