package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter


// A single step in the timeline
data class CutsceneStep(
    var duration: Float = 2.0f,
    var event: GameEvent = GameEvent(),

    var moveCameraTo: Vector3? = null,
    var lookAt: Vector3? = null,
    var cameraSpeed: Float = 5.0f
)

// The file structure
data class CutsceneDefinition(
    var id: String = "",
    var steps: MutableList<CutsceneStep> = mutableListOf()
)

class CutsceneSystem(private val game: MafiaGame) {
    var isPlaying = false
        private set

    private var currentCutscene: CutsceneDefinition? = null
    private var currentStepIndex = 0
    private var stepTimer = 0f
    private var speedMultiplier = 1.0f

    // Camera control
    private val cameraTargetPos = Vector3()
    private val cameraLookAtPos = Vector3()
    private var isCameraControlled = false
    private val cutscenesDir = "cutscenes"

    private val json = Json().apply {
        setOutputType(JsonWriter.OutputType.json)
        setUsePrototypes(false)
    }

    init {
        val dir = Gdx.files.local(cutscenesDir)
        if (!dir.exists()) dir.mkdirs()
    }

    fun startCutscene(cutsceneId: String) {
        val file = Gdx.files.local("cutscenes/$cutsceneId.json")
        if (!file.exists()) {
            println("ERROR: Cutscene file '$cutsceneId' not found!")
            return
        }

        try {
            currentCutscene = json.fromJson(CutsceneDefinition::class.java, file)
            println("Starting Cutscene: $cutsceneId")

            isPlaying = true
            currentStepIndex = 0
            stepTimer = 0f
            speedMultiplier = 1.0f

            // 1. Lock Player & UI
            game.playerSystem.setCutsceneControl(true)
            game.uiManager.setCinematicMode(true)

            // 2. Execute first step immediately
            executeCurrentStep()

        } catch (e: Exception) {
            println("Error loading cutscene: ${e.message}")
            endCutscene()
        }
    }

    fun getAllCutsceneIds(): List<String> {
        val dir = Gdx.files.local(cutscenesDir)
        if (!dir.exists()) return emptyList()
        return dir.list(".json").map { it.nameWithoutExtension() }.sorted()
    }

    fun loadCutsceneDefinition(id: String): CutsceneDefinition? {
        val file = Gdx.files.local("$cutscenesDir/$id.json")
        if (!file.exists()) return null
        return try {
            json.fromJson(CutsceneDefinition::class.java, file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveCutscene(def: CutsceneDefinition) {
        try {
            val file = Gdx.files.local("$cutscenesDir/${def.id}.json")
            file.writeString(json.prettyPrint(def), false)
            println("Saved cutscene: ${def.id}")
        } catch (e: Exception) {
            println("Error saving cutscene: ${e.message}")
        }
    }

    fun update(deltaTime: Float) {
        if (!isPlaying || currentCutscene == null) return

        // --- Input Handling ---
        // 1. Skip (Enter)
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            endCutscene()
            return
        }
        // 2. Fast Forward (Num 2) - Hold to speed up
        if (Gdx.input.isKeyPressed(Input.Keys.NUM_2)) {
            speedMultiplier = 4.0f // 4x speed
        } else {
            speedMultiplier = 1.0f
        }

        val dt = deltaTime * speedMultiplier
        stepTimer += dt

        // --- Timeline Logic ---
        val currentStep = currentCutscene!!.steps[currentStepIndex]

        // Camera Interpolation
        if (isCameraControlled) {
            game.cameraManager.updateCutsceneCamera(cameraTargetPos, cameraLookAtPos, currentStep.cameraSpeed, dt)
        }

        // Check if time for next step
        if (stepTimer >= currentStep.duration) {
            stepTimer = 0f
            currentStepIndex++

            if (currentStepIndex >= currentCutscene!!.steps.size) {
                endCutscene()
            } else {
                executeCurrentStep()
            }
        }

        // Update Player AI Movement if active
        game.playerSystem.updateCutsceneMovement(dt)
    }

    private fun executeCurrentStep() {
        val step = currentCutscene!!.steps[currentStepIndex]
        val event = step.event

        // 1. Handle Camera Overrides in this step
        if (step.moveCameraTo != null) {
            isCameraControlled = true
            cameraTargetPos.set(step.moveCameraTo)
            game.cameraManager.switchToCutsceneMode()
        }
        if (step.lookAt != null) {
            cameraLookAtPos.set(step.lookAt)
        }

        // 2. Execute Event Logic
        when (event.type) {
            GameEventType.PLAYER_MOVE_TO_NODE -> {
                if (event.pathNodeId != null) {
                    game.playerSystem.startCutsceneMovement(event.pathNodeId)
                }
            }
            GameEventType.CAMERA_FOCUS -> {
                // Just helper for camera lookat logic handled above
            }
            // Reuse Mission System for standard events (Spawn car, explosion, dialog, etc)
            else -> {
                // We temporarily use the mission system to execute standard events
                // NOTE: We might need to expose 'executeEvent' in MissionSystem or copy logic.
                // For now, let's assume you make `executeEvent` public in MissionSystem.
                // If not, you duplicate the spawn logic here.
                game.missionSystem.executeEventPublic(event)
            }
        }
    }

    private fun endCutscene() {
        println("Cutscene Finished.")
        isPlaying = false
        currentCutscene = null
        isCameraControlled = false

        // Unlock Player & UI
        game.playerSystem.setCutsceneControl(false)
        game.uiManager.setCinematicMode(false)
        game.cameraManager.switchToPlayerCamera()
    }
}
