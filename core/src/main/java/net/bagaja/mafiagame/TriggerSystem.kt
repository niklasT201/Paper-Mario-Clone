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

    private var editorCylinderModel: Model? = null
    private var editorCylinderInstance: ModelInstance? = null
    var isEditorVisible = false // Controlled by the new UI tool
    var selectedMissionIdForEditing: String? = null

    // --- Configuration ---
    companion object {
        private const val VISUAL_RADIUS = 2.5f
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
        return models.getOrPut(VISUAL_RADIUS) {
            println("Creating new trigger model for visual radius: $VISUAL_RADIUS")
            val modelBuilder = ModelBuilder()
            val material = Material(
                TextureAttribute.createDiffuse(triggerTexture),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                IntAttribute.createCullFace(GL20.GL_NONE)
            )
            // Use VISUAL_RADIUS to build the rectangle, NOT the functional radius.
            modelBuilder.createRect(
                -VISUAL_RADIUS, 0f,  VISUAL_RADIUS,
                -VISUAL_RADIUS, 0f, -VISUAL_RADIUS,
                VISUAL_RADIUS, 0f, -VISUAL_RADIUS,
                VISUAL_RADIUS, 0f,  VISUAL_RADIUS,
                0f, 1f, 0f, // Normal pointing straight up
                material,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
            )
        }
    }

    fun createOrUpdateEditorCylinder(radius: Float, height: Float) {
        editorCylinderModel?.dispose() // Dispose old model first
        val material = Material(
            ColorAttribute.createDiffuse(Color.CYAN.r, Color.CYAN.g, Color.CYAN.b, 0.3f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )
        editorCylinderModel = modelBuilder.createCylinder(radius * 2, height, radius * 2, 32, material,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())
        editorCylinderInstance = ModelInstance(editorCylinderModel)
    }

    fun update() {
        val playerPos = game.playerSystem.getPosition()

        for ((missionId, trigger) in missionTriggers) {
            // Skip triggers for missions that are already completed
            if (game.missionSystem.isMissionActive(missionId) || game.missionSystem.isMissionCompleted(missionId)) {
                trigger.isVisible = false
                continue // Skip the rest of the logic for this trigger
            }

            if (trigger.definition.type == TriggerType.ON_ENTER_AREA) {
                val center = trigger.definition.areaCenter!!
                val radius = trigger.definition.areaRadius!!

                // Check for mission activation
                if (playerPos.dst(center) < radius) {
                    game.missionSystem.startMission(missionId)
                }

                // --- MODIFIED VISUAL LOGIC ---
                val distanceToVisual = playerPos.dst(center)
                if (distanceToVisual < VISUAL_ACTIVATION_DISTANCE) {
                    // Find the ground height to place the visual correctly
                    val groundY = game.sceneManager.findHighestSupportY(center.x, center.z, center.y, 0.1f, game.blockSize)

                    // THIS IS THE CRITICAL FIX: Update the position every frame
                    trigger.modelInstance.transform.setToTranslation(center.x, groundY + GROUND_OFFSET, center.z)

                    trigger.isVisible = true
                } else {
                    trigger.isVisible = false
                }
                // --- END MODIFIED VISUAL LOGIC ---
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

    fun render(camera: Camera, environment: Environment) {
        renderableInstances.clear()
        for (trigger in missionTriggers.values) {
            if (trigger.isVisible) {
                renderableInstances.add(trigger.modelInstance)
            }
        }

        if (renderableInstances.size > 0) {
            shaderProvider.setEnvironment(environment)
            modelBatch.begin(camera)
            modelBatch.render(renderableInstances, environment)
            modelBatch.end()
        }


        if (isEditorVisible && editorCylinderInstance != null) {
            val missionId = selectedMissionIdForEditing
            val trigger = missionTriggers[missionId]?.definition
            if (trigger != null) {
                // Update the cylinder's position and render it
                editorCylinderInstance!!.transform.setToTranslation(trigger.areaCenter)

                Gdx.gl.glEnable(GL20.GL_BLEND)
                modelBatch.begin(camera)
                modelBatch.render(editorCylinderInstance!!, environment)
                modelBatch.end()
                Gdx.gl.glDisable(GL20.GL_BLEND)
            }
        }
    }

    override fun dispose() {
        modelBatch.dispose()
        shaderProvider.dispose()
        triggerTexture.dispose()
        models.values.forEach { it.dispose() }
    }
}
