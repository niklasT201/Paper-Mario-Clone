package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import net.bagaja.mafiagame.MafiaGame

data class VisualMissionTrigger(
    val definition: MissionTrigger,
    val modelInstance: ModelInstance,
    var isVisible: Boolean = false
)

class TriggerSystem(private val game: MafiaGame) : Disposable {

    // --- Rendering Components ---
    private lateinit var modelBatch: ModelBatch
    private lateinit var shaderProvider: BillboardShaderProvider
    private lateinit var triggerTexture: Texture
    private val modelBuilder = ModelBuilder()
    private val models = mutableMapOf<Float, Model>()

    // --- State ---
    private val missionTriggers = mutableMapOf<String, VisualMissionTrigger>()
    private val renderableInstances = Array<ModelInstance>()

    var isEditorVisible = false // Controlled by the new UI tool
    var selectedMissionIdForEditing: String? = null

    // --- Configuration ---
    companion object {
        const val VISUAL_RADIUS = 2.5f
        private const val VISUAL_ACTIVATION_DISTANCE = 40f
        private const val GROUND_OFFSET = 0.08f
    }

    fun initialize(allMissions: Map<String, MissionDefinition>) {
        shaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.9f)
            setMinLightLevel(0.4f)
        }
        modelBatch = ModelBatch(shaderProvider)

        try {
            triggerTexture = Texture(Gdx.files.local("assets/gui/highlight_circle_trans.png"))
            triggerTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        } catch (e: Exception) {
            println("ERROR: Could not load 'gui/highlight_circle_trans.png'. Trigger visuals will be invisible.")
            return
        }

        // Iterate through all loaded missions and create visual triggers for them
        for ((missionId, missionDef) in allMissions) {
            if (missionDef.startTrigger.type == TriggerType.ON_ENTER_AREA) {
                addTrigger(missionId, missionDef.startTrigger)
            }
        }
    }

    fun refreshTriggers() {
        println("Refreshing mission triggers...")
        // Clear out the old visual triggers
        missionTriggers.clear()

        // Get the most up-to-date list of all missions from the MissionSystem
        val allMissions = game.missionSystem.getAllMissionDefinitions()

        // Re-build the visual triggers, just like we do in initialize()
        for ((missionId, missionDef) in allMissions) {
            if (missionDef.startTrigger.type == TriggerType.ON_ENTER_AREA) {
                addTrigger(missionId, missionDef.startTrigger)
            }
        }
        println("Triggers refreshed. Total active triggers: ${missionTriggers.size}")
    }

    private fun addTrigger(missionId: String, definition: MissionTrigger) {
        val radius = definition.areaRadius ?: return
        val model = getOrCreateModelForRadius(radius)
        val instance = ModelInstance(model)
        instance.userData = "effect"

        val visualTrigger = VisualMissionTrigger(definition, instance)
        missionTriggers[missionId] = visualTrigger
    }

    private fun getOrCreateModelForRadius(radius: Float): Model {
        // We now use a constant visual radius (VISUAL_RADIUS) as the key,
        // so we only ever create ONE model for all triggers.
        return models.getOrPut(VISUAL_RADIUS) {
            println("Creating new trigger model for visual radius: $VISUAL_RADIUS")
            val modelBuilder = ModelBuilder()
            val material = Material(
                TextureAttribute.createDiffuse(triggerTexture),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                IntAttribute.createCullFace(GL20.GL_NONE)
            )
            // Use the constant VISUAL_RADIUS to build the rectangle, NOT the functional 'radius' parameter.
            val size = VISUAL_RADIUS * 2
            modelBuilder.createRect(
                -size / 2f, 0f,  size / 2f,
                -size / 2f, 0f, -size / 2f,
                size / 2f, 0f, -size / 2f,
                size / 2f, 0f,  size / 2f,
                0f, 1f, 0f, // Normal pointing straight up
                material,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
            )
        }
    }

    fun update() {
        val playerPos = game.playerSystem.getPosition()

        // Determine the ID of the currently active scene
        val currentSceneId = if (game.sceneManager.currentScene == SceneType.HOUSE_INTERIOR) {
            game.sceneManager.getCurrentHouse()?.id
        } else {
            "WORLD"
        }
        // If we're in an interior but can't get the ID, do nothing to be safe.
        if (currentSceneId == null) return

        for ((missionId, trigger) in missionTriggers) {
            // Check if the trigger belongs to the current scene
            if (trigger.definition.sceneId != currentSceneId) {
                trigger.isVisible = false // Ensure triggers from other scenes are hidden
                continue // Skip this trigger entirely
            }

            // Skip triggers for missions that are already completed
            if (game.missionSystem.isMissionActive(missionId) || game.missionSystem.isMissionCompleted(missionId)) {
                trigger.isVisible = false
                continue // Skip the rest of the logic for this trigger
            }

            if (trigger.definition.type == TriggerType.ON_ENTER_AREA) {
                val center = trigger.definition.areaCenter
                val radius = trigger.definition.areaRadius

                // Check for mission activation
                if (playerPos.dst(center) < radius) {
                    game.missionSystem.startMission(missionId)
                }

                val distanceToVisual = playerPos.dst(center)
                if (distanceToVisual < VISUAL_ACTIVATION_DISTANCE) {
                    // Find the ground height to place the visual correctly
                    val groundY = game.sceneManager.findHighestSupportY(center.x, center.z, center.y, 0.1f, game.blockSize)

                    // Update the position every frame
                    trigger.modelInstance.transform.setToTranslation(center.x, groundY + GROUND_OFFSET, center.z)

                    trigger.isVisible = true
                } else {
                    trigger.isVisible = false
                }
            }
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

        game.uiManager.updatePlacementInfo("Removed/Reset trigger for '${mission.title}'")

        return true // Indicate that an action was successfully performed
    }

    fun findMissionForNpc(npcId: String): MissionDefinition? {
        // Search through all loaded missions
        for ((missionId, missionDef) in game.missionSystem.getAllMissionDefinitions()) {
            val trigger = missionDef.startTrigger

            // Check three conditions:
            if (trigger.type == TriggerType.ON_TALK_TO_NPC &&
                trigger.targetNpcId == npcId &&
                !game.missionSystem.isMissionCompleted(missionId) &&
                !game.missionSystem.isMissionActive(missionId)
            ) {
                // We found a match!
                return missionDef
            }
        }
        // No mission was found for this NPC
        return null
    }

    fun render(camera: Camera, environment: Environment) {
        renderableInstances.clear()

        // Determine the ID of the currently active scene
        val currentSceneId = if (game.sceneManager.currentScene == SceneType.HOUSE_INTERIOR) {
            game.sceneManager.getCurrentHouse()?.id
        } else {
            "WORLD"
        }

        for (trigger in missionTriggers.values) {
            // RENDER RULE 1: Gameplay triggers that are visible AND in the current scene
            if (trigger.isVisible && trigger.definition.sceneId == currentSceneId) {
                renderableInstances.add(trigger.modelInstance)
            }
        }

        // RENDER RULE 2: Editor visual for the selected trigger, but ONLY if it's in the current scene
        if (isEditorVisible) {
            selectedMissionIdForEditing?.let { missionId ->
                missionTriggers[missionId]?.let { visualTrigger ->
                    // Check if the trigger being edited belongs to the current scene
                    if (visualTrigger.definition.sceneId == currentSceneId) {
                        val center = visualTrigger.definition.areaCenter
                        val groundY = game.sceneManager.findHighestSupportY(center.x, center.z, center.y, 0.1f, game.blockSize)
                        visualTrigger.modelInstance.transform.setToTranslation(center.x, groundY + GROUND_OFFSET, center.z)

                        // Add it to the render list if it's not already there
                        if (!renderableInstances.contains(visualTrigger.modelInstance, true)) {
                            renderableInstances.add(visualTrigger.modelInstance)
                        }
                    }
                }
            }
        }

        if (renderableInstances.size > 0) {
            shaderProvider.setEnvironment(environment)
            modelBatch.begin(camera)
            modelBatch.render(renderableInstances, environment)
            modelBatch.end()
        }
    }

    override fun dispose() {
        modelBatch.dispose()
        shaderProvider.dispose()
        triggerTexture.dispose()
        models.values.forEach { it.dispose() }
    }
}
