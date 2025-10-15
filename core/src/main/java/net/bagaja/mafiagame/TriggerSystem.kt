package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import kotlin.math.sin

/**
 * Manages the logic and visualization of mission triggers and objectives.
 * This system is responsible for starting missions based on player actions
 * and rendering visual indicators for available missions and active objectives.
 */
class TriggerSystem(private val game: MafiaGame) : Disposable {

    // --- Rendering Components ---
    private lateinit var modelBatch: ModelBatch
    private lateinit var billboardModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider

    // --- Models & Instances ---
    private var highlightCircleModel: Model? = null
    private var dialogIconModel: Model? = null
    private var highlightCircleInstance: ModelInstance? = null
    private var dialogIconInstance: ModelInstance? = null
    private var itemHighlightCircleModel: Model? = null
    private var itemHighlightCircleInstance: ModelInstance? = null
    private var questionIconModel: Model? = null
    private var exclamationIconModel: Model? = null

    // --- Textures ---
    private var highlightCircleTexture: Texture? = null
    private var dialogIconTexture: Texture? = null
    private var questionIconTexture: Texture? = null
    private var exclamationIconTexture: Texture? = null

    // --- State ---
    private val allMissions = mutableMapOf<String, MissionDefinition>()
    var selectedMissionIdForEditing: String? = null
    var isEditorVisible = false

    // --- Animation State ---
    private var bobbingTimer = 0f

    // --- Configuration ---
    companion object {
        const val VISUAL_RADIUS = 2.5f
        private const val ITEM_HIGHLIGHT_RADIUS = 1.25f
        private const val VISUAL_ACTIVATION_DISTANCE = 60f
        private const val GROUND_OFFSET = 0.08f

        private const val NPC_ICON_Y_OFFSET = 0.75f
        private const val DIALOG_ICON_WIDTH = 1.5f
        private const val DIALOG_ICON_HEIGHT = 1.5f

        private const val BOBBING_SPEED = 4f
        private const val BOBBING_HEIGHT = 0.15f
    }

    fun initialize() {
        refreshTriggers() // Load all missions from the mission system

        modelBatch = ModelBatch()
        billboardShaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.9f)
            setMinLightLevel(0.4f)
        }
        billboardModelBatch = ModelBatch(billboardShaderProvider) // For camera-facing billboards

        val modelBuilder = ModelBuilder()

        // --- Load Highlight Circle Texture and Create Model ---
        try {
            highlightCircleTexture = Texture(Gdx.files.internal("gui/highlight_circle_trans.png")).apply {
                setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            }
            val circleMaterial = Material(
                TextureAttribute.createDiffuse(highlightCircleTexture),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                IntAttribute.createCullFace(GL20.GL_NONE)
            )

            // --- Create LARGE, SCALABLE Area Highlight Model ---
            val areaSize = VISUAL_RADIUS * 2
            highlightCircleModel = modelBuilder.createRect(-areaSize / 2f, 0f, areaSize / 2f, -areaSize / 2f, 0f, -areaSize / 2f, areaSize / 2f, 0f, -areaSize / 2f, areaSize / 2f, 0f, areaSize / 2f, 0f, 1f, 0f, circleMaterial, (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong())
            highlightCircleInstance = ModelInstance(highlightCircleModel).apply { userData = "effect" }

            val itemSize = ITEM_HIGHLIGHT_RADIUS * 2
            itemHighlightCircleModel = modelBuilder.createRect(-itemSize / 2f, 0f, itemSize / 2f, -itemSize / 2f, 0f, -itemSize / 2f, itemSize / 2f, 0f, -itemSize / 2f, itemSize / 2f, 0f, itemSize / 2f, 0f, 1f, 0f, circleMaterial, (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong())
            itemHighlightCircleInstance = ModelInstance(itemHighlightCircleModel).apply { userData = "effect" }

        } catch (e: Exception) {
            println("ERROR: Could not load 'gui/highlight_circle_trans.png'. Highlight circles will be invisible.")
        }

        // --- Load Dialog Icon Texture and Create Model ---
        try {
            dialogIconTexture = Texture(Gdx.files.internal("gui/dialog_box.png")).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
            val iconMaterial = Material(TextureAttribute.createDiffuse(dialogIconTexture), BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA), IntAttribute.createCullFace(GL20.GL_NONE))
            dialogIconModel = modelBuilder.createRect(-DIALOG_ICON_WIDTH / 2f, -DIALOG_ICON_HEIGHT / 2f, 0f, DIALOG_ICON_WIDTH / 2f, -DIALOG_ICON_HEIGHT / 2f, 0f, DIALOG_ICON_WIDTH / 2f, DIALOG_ICON_HEIGHT / 2f, 0f, -DIALOG_ICON_WIDTH / 2f, DIALOG_ICON_HEIGHT / 2f, 0f, 0f, 0f, 1f, iconMaterial, (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong())
            dialogIconInstance = ModelInstance(dialogIconModel).apply { userData = "character" }
        } catch (e: Exception) {
            println("ERROR: Could not load 'gui/dialog_box.png'. Dialog icons will be invisible.")
        }

        try {
            questionIconTexture = Texture(Gdx.files.internal("gui/question_mark.png")).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
            val qMaterial = Material(TextureAttribute.createDiffuse(questionIconTexture), BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA), IntAttribute.createCullFace(GL20.GL_NONE))
            questionIconModel = modelBuilder.createRect(-DIALOG_ICON_WIDTH / 2f, -DIALOG_ICON_HEIGHT / 2f, 0f, DIALOG_ICON_WIDTH / 2f, -DIALOG_ICON_HEIGHT / 2f, 0f, DIALOG_ICON_WIDTH / 2f, DIALOG_ICON_HEIGHT / 2f, 0f, -DIALOG_ICON_WIDTH / 2f, DIALOG_ICON_HEIGHT / 2f, 0f, 0f, 0f, 1f, qMaterial, (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong())
            // No instance is created here anymore
        } catch (e: Exception) {
            println("ERROR: Could not load 'gui/question_mark.png'.")
        }

        try {
            exclamationIconTexture = Texture(Gdx.files.internal("gui/exclamation_mark.png")).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
            val eMaterial = Material(TextureAttribute.createDiffuse(exclamationIconTexture), BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA), IntAttribute.createCullFace(GL20.GL_NONE))
            exclamationIconModel = modelBuilder.createRect(-DIALOG_ICON_WIDTH / 2f, -DIALOG_ICON_HEIGHT / 2f, 0f, DIALOG_ICON_WIDTH / 2f, -DIALOG_ICON_HEIGHT / 2f, 0f, DIALOG_ICON_WIDTH / 2f, DIALOG_ICON_HEIGHT / 2f, 0f, -DIALOG_ICON_WIDTH / 2f, DIALOG_ICON_HEIGHT / 2f, 0f, 0f, 0f, 1f, eMaterial, (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong())
            // No instance is created here anymore
        } catch (e: Exception) {
            println("ERROR: Could not load 'gui/exclamation_mark.png'.")
        }
    }

    fun refreshTriggers() {
        println("Refreshing mission triggers...")
        allMissions.clear()
        allMissions.putAll(game.missionSystem.getAllMissionDefinitions())
        println("Triggers refreshed. Total missions tracked: ${allMissions.size}")
    }

    fun update() {
        // Update the animation timer every frame.
        bobbingTimer += Gdx.graphics.deltaTime

        val playerPos = game.playerSystem.getPosition()
        val currentSceneId = game.sceneManager.getCurrentSceneId()

        for ((missionId, missionDef) in allMissions) {
            if (game.missionSystem.isMissionActive(missionId) || game.missionSystem.isMissionCompleted(missionId)) continue
            val prerequisitesMet = missionDef.prerequisites.all { game.missionSystem.isMissionCompleted(it) }
            if (!prerequisitesMet) continue

            val trigger = missionDef.startTrigger
            if (trigger.sceneId != currentSceneId) continue

            if (trigger.type == TriggerType.ON_ENTER_AREA && playerPos.dst(trigger.areaCenter) < trigger.areaRadius) {
                game.missionSystem.startMission(missionId)
                break // Start only one mission per frame
            }
        }
    }

    fun render(camera: Camera, environment: Environment) {
        val renderables = Array<ModelInstance>()
        val billboards = Array<ModelInstance>()

        // Calculate the bobbing offset once per frame.
        val bobOffset = sin(bobbingTimer * BOBBING_SPEED) * BOBBING_HEIGHT

        // Populate the lists with visuals that need to be drawn this frame
        renderStartTriggers(renderables, billboards, bobOffset)
        renderStandaloneDialogTriggers(billboards, bobOffset)
        renderActiveObjectiveMarkers(renderables, billboards, bobOffset)

        // --- Render Ground-Based Visuals (Highlight Circles) ---
        if (renderables.size > 0) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            Gdx.gl.glDepthMask(false) // Prevent visual artifacts with transparent objects

            modelBatch.begin(camera)
            modelBatch.render(renderables, environment)
            modelBatch.end()

            Gdx.gl.glDepthMask(true)
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }

        // --- Render Billboard Visuals (Dialog Icons) ---
        if (billboards.size > 0) {
            billboardShaderProvider.setEnvironment(environment)
            billboardModelBatch.begin(camera)
            billboardModelBatch.render(billboards, environment)
            billboardModelBatch.end()
        }
    }

    private fun renderStandaloneDialogTriggers(billboards: Array<ModelInstance>, bobOffset: Float) {
        val playerPos = game.playerSystem.getPosition()
        if (game.missionSystem.activeMission != null) return

        // --- Check NPCs ---
        for (npc in game.sceneManager.activeNPCs) {
            val dialog = npc.standaloneDialog ?: continue
            if (playerPos.dst(npc.position) > VISUAL_ACTIVATION_DISTANCE) continue
            if (findMissionForNpc(npc.id) != null) continue

            val modelToUse = if (dialog.isInteractive()) exclamationIconModel else questionIconModel
            modelToUse?.let { model ->
                val iconInstance = ModelInstance(model).apply { userData = "character" }
                val iconPos = npc.position.cpy().add(0f, (npc.npcType.height / 2f) + NPC_ICON_Y_OFFSET + bobOffset, 0f)
                iconInstance.transform.setToTranslation(iconPos)
                billboards.add(iconInstance)
            }
        }

        // --- Check Enemies ---
        for (enemy in game.sceneManager.activeEnemies) {
            val dialog = enemy.standaloneDialog ?: continue
            if (enemy.currentState != AIState.IDLE) continue
            if (playerPos.dst(enemy.position) > VISUAL_ACTIVATION_DISTANCE) continue

            val modelToUse = if (dialog.isInteractive()) exclamationIconModel else questionIconModel
            modelToUse?.let { model ->
                val iconInstance = ModelInstance(model).apply { userData = "character" }
                val iconPos = enemy.position.cpy().add(0f, (enemy.enemyType.height / 2f) + NPC_ICON_Y_OFFSET + bobOffset, 0f)
                iconInstance.transform.setToTranslation(iconPos)
                billboards.add(iconInstance)
            }
        }
    }

    private fun renderStartTriggers(renderables: Array<ModelInstance>, billboards: Array<ModelInstance>, bobOffset: Float) {
        val playerPos = game.playerSystem.getPosition()
        val currentSceneId = game.sceneManager.getCurrentSceneId()

        for ((missionId, missionDef) in allMissions) {
            val trigger = missionDef.startTrigger

            // --- Visibility Checks ---
            if (!trigger.showVisuals || game.missionSystem.activeMission != null || trigger.sceneId != currentSceneId || game.missionSystem.isMissionCompleted(missionId)) continue
            val prerequisitesMet = missionDef.prerequisites.all { game.missionSystem.isMissionCompleted(it) }
            if (!prerequisitesMet) continue
            if (playerPos.dst(trigger.areaCenter) > VISUAL_ACTIVATION_DISTANCE && trigger.type != TriggerType.ON_TALK_TO_NPC) continue

            // --- Render Visual Based on Type ---
            when (trigger.type) {
                TriggerType.ON_ENTER_AREA -> {
                    highlightCircleInstance?.let { instance ->
                        val center = trigger.areaCenter
                        var groundY = game.sceneManager.findHighestSupportY(center.x, center.z, center.y, 0.1f, game.blockSize)
                        if (groundY < -500f) groundY = 0f
                        instance.transform.setToTranslation(center.x, groundY + GROUND_OFFSET, center.z)
                        // Reset scale to default in case it was changed by an objective
                        instance.transform.scale(1f, 1f, 1f)
                        renderables.add(instance)
                    }
                }
                TriggerType.ON_TALK_TO_NPC -> {
                    dialogIconInstance?.let { instance ->
                        val npc = game.sceneManager.activeNPCs.find { it.id == trigger.targetNpcId }
                        if (npc != null && playerPos.dst(npc.position) < VISUAL_ACTIVATION_DISTANCE) {
                            val iconPos = npc.position.cpy().add(0f, (npc.npcType.height / 2f) + NPC_ICON_Y_OFFSET + bobOffset, 0f)
                            instance.transform.setToTranslation(iconPos)
                            billboards.add(instance)
                        }
                    }
                }
                else -> { /* Other trigger types have no standard visual */ }
            }
        }
    }

    private fun renderActiveObjectiveMarkers(renderables: Array<ModelInstance>, billboards: Array<ModelInstance>, bobOffset: Float) {
        val objective = game.missionSystem.activeMission?.getCurrentObjective() ?: return
        if (!objective.showVisuals) return

        val condition = objective.completionCondition
        val currentSceneId = game.sceneManager.getCurrentSceneId()

        if (condition.sceneId != null && condition.sceneId != currentSceneId) return

        when (condition.type) {
            ConditionType.ENTER_AREA, ConditionType.DRIVE_TO_LOCATION, ConditionType.STAY_IN_AREA -> {
                highlightCircleInstance?.let { instance ->
                    val center = condition.areaCenter ?: return
                    var groundY = game.sceneManager.findHighestSupportY(center.x, center.z, center.y, 0.1f, game.blockSize)
                    if (groundY < -500f) groundY = 0f
                    instance.transform.setToTranslation(center.x, groundY + GROUND_OFFSET, center.z)
                    // Scale the visual to match the objective's functional radius
                    val scale = (condition.areaRadius ?: VISUAL_RADIUS) / VISUAL_RADIUS
                    instance.transform.scale(scale, 1f, scale)
                    renderables.add(instance)
                }
            }
            ConditionType.TALK_TO_NPC -> {
                dialogIconInstance?.let { instance ->
                    val npc = game.sceneManager.activeNPCs.find { it.id == condition.targetId }
                    if (npc != null) {
                        val iconPos = npc.position.cpy().add(0f, (npc.npcType.height / 2f) + NPC_ICON_Y_OFFSET + bobOffset, 0f)
                        instance.transform.setToTranslation(iconPos)
                        billboards.add(instance)
                    }
                }
            }
            ConditionType.COLLECT_SPECIFIC_ITEM -> {
                itemHighlightCircleInstance?.let { instance ->
                    val item = game.sceneManager.activeItems.find { it.id == condition.itemId }
                    if (item != null) {
                        val itemPos = item.position.cpy()
                        var groundY = game.sceneManager.findHighestSupportY(itemPos.x, itemPos.z, itemPos.y, 0.1f, game.blockSize)
                        if (groundY < -500f) groundY = 0f

                        instance.transform.setToTranslation(itemPos.x, groundY + GROUND_OFFSET, itemPos.z)
                        renderables.add(instance)
                    }
                }
            }
            else -> { /* No visual for this objective type */ }
        }
    }

    fun removeTriggerForSelectedMission(): Boolean {
        // Get the ID of the mission currently selected in the TriggerEditorUI
        val missionId = selectedMissionIdForEditing ?: return false

        // Find the mission definition
        val mission = game.missionSystem.getMissionDefinition(missionId) ?: return false

        // Reset its trigger's position and radius to a default state
        mission.startTrigger.areaCenter.set(Vector3.Zero)
        mission.startTrigger.areaRadius = 20f // A default radius

        // Save the mission file with the now-reset trigger
        game.missionSystem.saveMission(mission)
        game.uiManager.updatePlacementInfo("Reset trigger for '${mission.title}'")

        return true// Indicate that an action was successfully performed
    }

    fun findMissionForNpc(npcId: String): MissionDefinition? {
        return allMissions.values.find { missionDef ->

            // Search through all loaded missions
            val trigger = missionDef.startTrigger
            trigger.type == TriggerType.ON_TALK_TO_NPC &&
                trigger.targetNpcId == npcId &&
                !game.missionSystem.isMissionCompleted(missionDef.id) &&
                !game.missionSystem.isMissionActive(missionDef.id)
        }
    }

    override fun dispose() {
        modelBatch.dispose()
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
        highlightCircleTexture?.dispose()
        dialogIconTexture?.dispose()
        highlightCircleModel?.dispose()
        dialogIconModel?.dispose()
        itemHighlightCircleModel?.dispose()
        questionIconTexture?.dispose()
        exclamationIconTexture?.dispose()
        questionIconModel?.dispose()
        exclamationIconModel?.dispose()
    }
}
