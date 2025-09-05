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
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import net.bagaja.mafiagame.MafiaGame

/**
 * Now holds a visual ModelInstance along with the trigger data.
 */
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
    private val models = mutableMapOf<Float, Model>() // Cache models by radius

    // --- State ---
    private val missionTriggers = mutableMapOf<String, VisualMissionTrigger>()
    private val renderableInstances = Array<ModelInstance>()

    // --- Configuration ---
    companion object {
        private const val VISUAL_RADIUS = 2.5f
        private const val VISUAL_ACTIVATION_DISTANCE = 40f
        private const val GROUND_OFFSET = 0.08f
    }

    fun initialize() {
        shaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.9f)
            setMinLightLevel(0.4f)
        }
        modelBatch = ModelBatch(shaderProvider)

        try {
            triggerTexture = Texture(Gdx.files.internal("gui/highlight_circle_trans.png"))
            triggerTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        } catch (e: Exception) {
            println("ERROR: Could not load 'gui/highlight_circle_trans.png'. Trigger visuals will be invisible.")
            return
        }

        // For now, we manually add the trigger for our test mission
        val testMissionTriggerDef = MissionTrigger(
            type = TriggerType.ON_ENTER_AREA,
            areaCenter = Vector3(-10f, 0f, 10f), // Y will be determined dynamically
            areaRadius = 5f
        )
        addTrigger("test_mission_01", testMissionTriggerDef)
    }

    /**
     * Creates a new VisualMissionTrigger and adds it to the system.
     */
    private fun addTrigger(missionId: String, definition: MissionTrigger) {
        val radius = definition.areaRadius ?: return
        val model = getOrCreateModelForRadius(radius)
        val instance = ModelInstance(model)
        instance.userData = "effect"

        val visualTrigger = VisualMissionTrigger(definition, instance)
        missionTriggers[missionId] = visualTrigger
    }

    /**
     * Creates or retrieves a cached model for a specific radius. This is efficient.
     */
    private fun getOrCreateModelForRadius(radius: Float): Model {
        // We now use the *fixed visual size* as the key for our cache.
        // This is more efficient as we will likely only ever create ONE model for all triggers.
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

    fun update() {
        val playerPos = game.playerSystem.getPosition()

        for ((missionId, trigger) in missionTriggers) {
            // Skip triggers for missions that are already completed
            if (game.missionSystem.isMissionCompleted(missionId)) {
                trigger.isVisible = false
                continue
            }

            if (trigger.definition.type == TriggerType.ON_ENTER_AREA) {
                val center = trigger.definition.areaCenter!!
                val radius = trigger.definition.areaRadius!!

                // Check for mission activation
                if (playerPos.dst(center) < radius) {
                    game.missionSystem.startMission(missionId)
                }

                // Check for visual activation
                val distanceToVisual = playerPos.dst(center)
                if (distanceToVisual < VISUAL_ACTIVATION_DISTANCE) {
                    // Find the ground height to place the visual correctly
                    val groundY = game.sceneManager.findHighestSupportY(center.x, center.z, center.y, 0.1f, game.blockSize)
                    trigger.modelInstance.transform.setToTranslation(center.x, groundY + GROUND_OFFSET, center.z)
                    trigger.isVisible = true
                } else {
                    trigger.isVisible = false
                }
            }
        }
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
    }

    override fun dispose() {
        modelBatch.dispose()
        shaderProvider.dispose()
        triggerTexture.dispose()
        models.values.forEach { it.dispose() }
    }
}
