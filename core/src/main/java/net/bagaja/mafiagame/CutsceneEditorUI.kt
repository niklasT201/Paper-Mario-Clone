package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray

class CutsceneEditorUI(
    private val skin: Skin,
    private val stage: Stage,
    private val cutsceneSystem: CutsceneSystem,
    private val uiManager: UIManager,
    private val game: MafiaGame
) {
    val window = Window("Cutscene Editor", skin, "dialog")
    private val fileSelectBox = SelectBox<String>(skin)
    private val stepsList = com.badlogic.gdx.scenes.scene2d.ui.List<String>(skin)

    // Current Data
    private var currentDefinition: CutsceneDefinition? = null
    private var selectedStepIndex: Int = -1

    // UI Fields
    private val durationField = TextField("2.0", skin)
    private val eventTypeSelect = SelectBox<String>(skin)

    // Event Param Fields
    private val targetIdField = TextField("", skin).apply { messageText = "Target ID / Path Node ID" }

    // Camera Fields
    private val useCameraCheckbox = CheckBox(" Override Camera", skin)
    private val camX = TextField("0", skin)
    private val camY = TextField("0", skin)
    private val camZ = TextField("0", skin)
    private val lookX = TextField("0", skin)
    private val lookY = TextField("0", skin)
    private val lookZ = TextField("0", skin)
    private val camSpeedField = TextField("5.0", skin)

    init {
        window.setSize(1500f, 600f)
        window.setPosition(stage.width / 2f, stage.height / 2f, Align.center)
        window.isMovable = true
        window.isResizable = true

        val mainSplit = SplitPane(createLeftPanel(), createRightPanel(), false, skin)
        mainSplit.splitAmount = 0.3f

        window.add(mainSplit).grow()
        window.row()

        val bottomTable = Table()
        val saveButton = TextButton("Save", skin)
        val closeButton = TextButton("Close", skin)

        saveButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                saveCurrentCutscene()
            }
        })
        closeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { hide() }
        })

        bottomTable.add(saveButton).pad(10f)
        bottomTable.add(closeButton).pad(10f)
        window.add(bottomTable).fillX()

        stage.addActor(window)
        window.isVisible = false
    }

    private fun clearRightPanel() {
        durationField.text = "2.0"
        if (eventTypeSelect.items.size > 0) eventTypeSelect.selectedIndex = 0
        targetIdField.text = ""

        useCameraCheckbox.isChecked = false
        camX.text = "0"; camY.text = "0"; camZ.text = "0"
        lookX.text = "0"; lookY.text = "0"; lookZ.text = "0"
        camSpeedField.text = "5.0"
    }

    private fun createLeftPanel(): Table {
        val table = Table()
        table.pad(10f)

        // File Selection
        val fileTable = Table()
        val newButton = TextButton("New", skin)
        val loadButton = TextButton("Load", skin)

        fileTable.add(fileSelectBox).growX().padRight(5f)
        fileTable.add(loadButton)

        table.add(Label("Cutscene File:", skin)).left().row()
        table.add(fileTable).growX().row()
        table.add(newButton).growX().padBottom(10f).row()

        // Steps List
        table.add(Label("Timeline Steps:", skin)).left().row()
        val scrollPane = ScrollPane(stepsList, skin)
        table.add(scrollPane).grow().row()

        // Step Buttons
        val buttonTable = Table()
        val addStepBtn = TextButton("+", skin)
        val removeStepBtn = TextButton("-", skin)
        val moveUpBtn = TextButton("Up", skin)
        val moveDownBtn = TextButton("Down", skin)

        buttonTable.add(addStepBtn).width(40f)
        buttonTable.add(removeStepBtn).width(40f)
        buttonTable.add(moveUpBtn).padLeft(10f)
        buttonTable.add(moveDownBtn)

        table.add(buttonTable).growX()

        // Listeners
        newButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                uiManager.showTextInputDialog("New Cutscene", "cutscene_name") { name ->
                    currentDefinition = CutsceneDefinition(id = name)

                    // Deselect the file box so it doesn't look like we are editing the old file
                    fileSelectBox.selected = null

                    refreshUI()
                    // Show message so you know it worked
                    uiManager.showTemporaryMessage("Created new cutscene: $name")
                }
            }
        })

        loadButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (fileSelectBox.selected != null) {
                    currentDefinition = cutsceneSystem.loadCutsceneDefinition(fileSelectBox.selected)
                    refreshUI()
                }
            }
        })

        stepsList.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                saveCurrentStepToMemory() // Save previous step before switching
                selectedStepIndex = stepsList.selectedIndex
                loadStepIntoUI()
            }
        })

        addStepBtn.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                currentDefinition?.let {
                    it.steps.add(CutsceneStep(event = GameEvent(type = GameEventType.WAIT)))
                    refreshStepsList()
                    stepsList.selectedIndex = it.steps.size - 1
                }
            }
        })

        removeStepBtn.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                currentDefinition?.let {
                    if (selectedStepIndex in it.steps.indices) {
                        it.steps.removeAt(selectedStepIndex)
                        refreshStepsList()
                    }
                }
            }
        })

        return table
    }

    private fun createRightPanel(): Table {
        val table = Table()
        table.pad(10f).top().left()
        table.defaults().left().padBottom(5f)

        // Duration
        table.add(Label("Duration (seconds):", skin))
        table.add(durationField).width(100f).row()

        table.add(HSeparator(skin)).growX().colspan(2).pad(10f).row()

        // Event Config
        table.add(Label("Event Type:", skin))
        eventTypeSelect.items = GdxArray(GameEventType.entries.map { it.name }.toTypedArray())
        table.add(eventTypeSelect).growX().row()

        table.add(Label("Target ID / Path Node:", skin))
        table.add(targetIdField).growX().row()

        table.add(HSeparator(skin)).growX().colspan(2).pad(10f).row()

        // Camera Config
        table.add(useCameraCheckbox).colspan(2).row()

        val camTable = Table()
        camTable.add(Label("Pos:", skin)).padRight(5f)
        camTable.add(camX).width(50f); camTable.add(camY).width(50f); camTable.add(camZ).width(50f)
        table.add(camTable).colspan(2).row()

        val lookTable = Table()
        lookTable.add(Label("Look:", skin)).padRight(5f)
        lookTable.add(lookX).width(50f); lookTable.add(lookY).width(50f); lookTable.add(lookZ).width(50f)
        table.add(lookTable).colspan(2).row()

        val captureBtn = TextButton("Capture Current View", skin)
        captureBtn.color = Color.ORANGE
        captureBtn.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val cam = game.cameraManager.camera
                camX.text = "%.1f".format(cam.position.x)
                camY.text = "%.1f".format(cam.position.y)
                camZ.text = "%.1f".format(cam.position.z)

                val lookAt = cam.position.cpy().add(cam.direction.cpy().scl(10f))
                lookX.text = "%.1f".format(lookAt.x)
                lookY.text = "%.1f".format(lookAt.y)
                lookZ.text = "%.1f".format(lookAt.z)

                useCameraCheckbox.isChecked = true
            }
        })
        table.add(captureBtn).colspan(2).fillX().row()

        table.add(Label("Cam Speed:", skin))
        table.add(camSpeedField).width(100f).row()

        return table
    }

    private fun refreshStepsList() {
        val def = currentDefinition ?: return
        val items = GdxArray<String>()
        def.steps.forEachIndexed { index, step ->
            val camStr = if (step.moveCameraTo != null) "[CAM]" else ""
            items.add("$index. ${step.event.type} (${step.duration}s) $camStr")
        }

        // FIX: Use setItems() method instead of property assignment
        stepsList.setItems(items)
    }

    private fun loadStepIntoUI() {
        val def = currentDefinition ?: return

        // If index is invalid, clear inputs instead of leaving old data
        if (selectedStepIndex !in def.steps.indices) {
            clearRightPanel() // <--- ADD THIS
            return
        }

        val step = def.steps[selectedStepIndex]

        durationField.text = step.duration.toString()
        eventTypeSelect.selected = step.event.type.name
        targetIdField.text = step.event.targetId ?: step.event.pathNodeId ?: ""

        useCameraCheckbox.isChecked = step.moveCameraTo != null
        if (step.moveCameraTo != null) {
            camX.text = step.moveCameraTo!!.x.toString()
            camY.text = step.moveCameraTo!!.y.toString()
            camZ.text = step.moveCameraTo!!.z.toString()
        }
        if (step.lookAt != null) {
            lookX.text = step.lookAt!!.x.toString()
            lookY.text = step.lookAt!!.y.toString()
            lookZ.text = step.lookAt!!.z.toString()
        }
        camSpeedField.text = step.cameraSpeed.toString()
    }

    private fun saveCurrentStepToMemory() {
        val def = currentDefinition ?: return
        if (selectedStepIndex !in def.steps.indices) return

        // Construct vectors
        val camPos = if (useCameraCheckbox.isChecked) Vector3(
            camX.text.toFloatOrNull() ?: 0f,
            camY.text.toFloatOrNull() ?: 0f,
            camZ.text.toFloatOrNull() ?: 0f
        ) else null

        val lookPos = if (useCameraCheckbox.isChecked) Vector3(
            lookX.text.toFloatOrNull() ?: 0f,
            lookY.text.toFloatOrNull() ?: 0f,
            lookZ.text.toFloatOrNull() ?: 0f
        ) else null

        // Construct Event
        val type = GameEventType.valueOf(eventTypeSelect.selected)
        val idText = targetIdField.text

        // Map text field to correct ID property based on event type
        val event = GameEvent(
            type = type,
            targetId = if(type != GameEventType.PLAYER_MOVE_TO_NODE) idText else null,
            pathNodeId = if(type == GameEventType.PLAYER_MOVE_TO_NODE) idText else null,
            // Add more event mapping here as you expand the system
        )

        val newStep = CutsceneStep(
            duration = durationField.text.toFloatOrNull() ?: 2f,
            event = event,
            moveCameraTo = camPos,
            lookAt = lookPos,
            cameraSpeed = camSpeedField.text.toFloatOrNull() ?: 5f
        )

        def.steps[selectedStepIndex] = newStep
    }

    private fun saveCurrentCutscene() {
        saveCurrentStepToMemory() // Save currently open step first
        currentDefinition?.let {
            cutsceneSystem.saveCutscene(it)
            uiManager.showTemporaryMessage("Cutscene Saved!")
            refreshFileList()
        }
    }

    private fun refreshUI() {
        refreshFileList()
        refreshStepsList()

        // If there are steps, load the first one.
        if (currentDefinition != null && currentDefinition!!.steps.size > 0) {
            stepsList.selectedIndex = 0
            selectedStepIndex = 0
            loadStepIntoUI()
        } else {
            // If new/empty, clear selection and inputs
            stepsList.selection.clear()
            selectedStepIndex = -1
            clearRightPanel() // <--- ADD THIS
        }
    }

    private fun refreshFileList() {
        val files = cutsceneSystem.getAllCutsceneIds()
        // FIX: Use setItems()
        fileSelectBox.setItems(GdxArray(files.toTypedArray()))
    }

    fun show() {
        refreshUI()
        window.isVisible = true
        window.toFront()
    }

    fun hide() {
        window.isVisible = false
        stage.unfocusAll()
    }

    // Helper class for line separator
    class HSeparator(skin: Skin) : Image(skin.newDrawable("white", Color.GRAY)) {
        init { height = 2f }
    }
}
