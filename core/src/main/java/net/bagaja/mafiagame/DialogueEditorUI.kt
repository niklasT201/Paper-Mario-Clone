package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.graphics.g2d.TextureRegion

class DialogueEditorUI(
    private val skin: Skin,
    private val stage: Stage,
    private val uiManager: UIManager,
    private val dialogueManager: DialogueManager
) {
    private val window: Window = Window("Dialogue Editor", skin, "dialog")

    // --- UI Components ---
    private val fileSelectBox: SelectBox<String>
    private val sequenceList: com.badlogic.gdx.scenes.scene2d.ui.List<String>
    private val sequenceIdField: TextField
    private val linesContainer: VerticalGroup

    // Cache for available textures
    private val availableTextures = GdxArray<String>()
    private val previewTextures = mutableMapOf<String, Texture>()

    private val previewControllerWindow: Window
    private var isPreviewing = false
    private var previewLineIndex = 0
    private var currentPreviewLines = mutableListOf<DialogLine>()
    private var widthSlider: Slider
    private var heightSlider: Slider
    private var portraitXSlider: Slider
    private var portraitYSlider: Slider
    private var lineInfoLabel: Label
    private var widthInput: TextField
    private var heightInput: TextField
    private var portraitXInput: TextField
    private var portraitYInput: TextField
    private lateinit var camDistSlider: Slider
    private lateinit var camDistInput: TextField

    private lateinit var camHeightSlider: Slider
    private lateinit var camHeightInput: TextField

    private var syncSettingsCheckbox: CheckBox
    private var applyPresetButton: TextButton

    private var highlightedLineWidget: Table? = null
    private var isUpdatingSliders = false
    private var editingInstance: StandaloneDialog? = null
    private var editingInstanceCharacter: Any? = null

    init {
        window.isMovable = true
        window.isModal = false
        window.setSize(Gdx.graphics.width * 1.9f, Gdx.graphics.height * 1.7f)
        window.setPosition(stage.width / 2f, stage.height / 2f, Align.center)
        window.padTop(40f)

        // Scan for available textures
        scanAvailableTextures()

        val mainContentTable = Table()

        // --- Left Panel (File & Sequence List) ---
        val leftPanel = Table()
        leftPanel.pad(10f).defaults().fillX().expandX()

        val fileTable = Table()
        fileSelectBox = SelectBox(skin)
        val newFileButton = TextButton("New", skin)
        fileTable.add(Label("File:", skin)).left()
        fileTable.add(fileSelectBox).growX().padLeft(5f).padRight(5f)
        fileTable.add(newFileButton)
        leftPanel.add(fileTable).row()

        leftPanel.add(Label("Sequences:", skin)).left().padTop(10f).row()
        sequenceList = com.badlogic.gdx.scenes.scene2d.ui.List(skin)
        val sequenceScrollPane = ScrollPane(sequenceList, skin)
        sequenceScrollPane.fadeScrollBars = false
        leftPanel.add(sequenceScrollPane).expand().fill().padBottom(10f).row()

        val newSequenceButton = TextButton("New Sequence", skin)
        val deleteSequenceButton = TextButton("Delete Selected", skin)
        val leftButtonTable = Table()
        leftButtonTable.add(newSequenceButton).growX().pad(5f)
        leftButtonTable.add(deleteSequenceButton).growX().pad(5f)
        leftPanel.add(leftButtonTable).fillX()

        // --- Right Panel (Line Editor) ---
        val rightPanel = Table()
        rightPanel.pad(10f).defaults().fillX().expandX()

        val idTable = Table()
        idTable.add(Label("Sequence ID:", skin)).padRight(10f)
        sequenceIdField = TextField("", skin)
        idTable.add(sequenceIdField).growX()
        rightPanel.add(idTable).padBottom(10f).row()

        linesContainer = VerticalGroup().apply { space(5f); wrap(false); align(Align.topLeft) }
        val linesScrollPane = ScrollPane(linesContainer, skin)
        linesScrollPane.setFadeScrollBars(false)
        rightPanel.add(linesScrollPane).expand().fill().padBottom(10f).row()

        val addLineButton = TextButton("Add Line", skin)
        rightPanel.add(addLineButton).left()

        // --- Main Window Assembly ---
        val splitPane = SplitPane(leftPanel, rightPanel, false, skin)
        splitPane.splitAmount = 0.3f
        mainContentTable.add(splitPane).expand().fill().row()

        val saveButton = TextButton("Save & Reload", skin)
        val closeButton = TextButton("Close", skin)
        val previewButton = TextButton("Preview", skin)

        val bottomButtonTable = Table()
        bottomButtonTable.add(saveButton).pad(10f)
        bottomButtonTable.add(previewButton).pad(10f)
        bottomButtonTable.add(closeButton).pad(10f)

        mainContentTable.add(bottomButtonTable).padTop(10f)

        window.add(mainContentTable).expand().fill()
        window.isVisible = false
        stage.addActor(window)

        // --- PREVIEW CONTROLLER WINDOW SETUP ---
        previewControllerWindow = Window("Live Preview", skin, "dialog")
        previewControllerWindow.isMovable = true
        previewControllerWindow.padTop(40f)

        val controllerContent = Table()
        controllerContent.pad(10f).defaults().pad(4f)

        lineInfoLabel = Label("Line: 0/0", skin)

        // 1. Define Expanded Ranges for Sliders
        val maxWidth = Gdx.graphics.width * 1.2f // 120% screen width (allows oversizing)
        val maxHeight = Gdx.graphics.height * 1.0f // 100% screen height
        val offsetRange = 800f // -800 to +800 range for portrait offsets

        // 2. Initialize Sliders
        widthSlider = Slider(100f, maxWidth, 1f, false, skin)
        heightSlider = Slider(50f, maxHeight, 1f, false, skin)
        portraitXSlider = Slider(-offsetRange, offsetRange, 1f, false, skin)
        portraitYSlider = Slider(-offsetRange, offsetRange, 1f, false, skin)

        // 3. Initialize Input TextFields (Alignment Center)
        widthInput = TextField("", skin).apply { alignment = Align.center }
        heightInput = TextField("", skin).apply { alignment = Align.center }
        portraitXInput = TextField("", skin).apply { alignment = Align.center }
        portraitYInput = TextField("", skin).apply { alignment = Align.center }

        // 4. Navigation Buttons Row
        val prevLineButton = TextButton("<", skin)
        val nextLineButton = TextButton(">", skin)
        val navigationTable = Table()
        navigationTable.add(prevLineButton).padRight(10f)
        navigationTable.add(lineInfoLabel)
        navigationTable.add(nextLineButton).padLeft(10f)

        controllerContent.add(navigationTable).colspan(3).center().padBottom(15f).row()

        // 5. Build Control Rows (Label | Slider | Input)

        // Width Row
        controllerContent.add(Label("Width:", skin)).left()
        controllerContent.add(widthSlider).growX().padRight(5f)
        controllerContent.add(widthInput).width(70f).row()

        // Height Row
        controllerContent.add(Label("Height:", skin)).left()
        controllerContent.add(heightSlider).growX().padRight(5f)
        controllerContent.add(heightInput).width(70f).row()

        // Portrait X Row
        controllerContent.add(Label("Portrait X:", skin)).left()
        controllerContent.add(portraitXSlider).growX().padRight(5f)
        controllerContent.add(portraitXInput).width(70f).row()

        // Portrait Y Row
        controllerContent.add(Label("Portrait Y:", skin)).left()
        controllerContent.add(portraitYSlider).growX().padRight(5f)
        controllerContent.add(portraitYInput).width(70f).row()

        camDistSlider = Slider(2f, 40f, 0.5f, false, skin)
        camDistInput = TextField("", skin).apply { alignment = Align.center; messageText = "Def" }

        camHeightSlider = Slider(0f, 20f, 0.5f, false, skin)
        camHeightInput = TextField("", skin).apply { alignment = Align.center; messageText = "Def" }

        // Add Camera Distance Row
        controllerContent.add(Label("Cam Dist:", skin)).left()
        controllerContent.add(camDistSlider).growX().padRight(5f)
        controllerContent.add(camDistInput).width(70f).row()

        // Add Camera Height Row
        controllerContent.add(Label("Cam Height:", skin)).left()
        controllerContent.add(camHeightSlider).growX().padRight(5f)
        controllerContent.add(camHeightInput).width(70f).row()


        // 6. Options Row
        val optionsTable = Table()
        syncSettingsCheckbox = CheckBox(" Sync settings for all lines", skin)
        applyPresetButton = TextButton("Apply Bottom-Center Preset", skin)

        optionsTable.add(syncSettingsCheckbox).left().row()
        optionsTable.add(applyPresetButton).left().padTop(8f).row()
        controllerContent.add(optionsTable).colspan(3).padTop(15f).row()

        // 7. Bottom Buttons
        val previewButtonTable = Table()
        val saveAndCloseButton = TextButton("Save & Close", skin)
        val closePreviewButton = TextButton("Back to Editor", skin)
        previewButtonTable.add(saveAndCloseButton).pad(5f)
        previewButtonTable.add(closePreviewButton).pad(5f)
        controllerContent.add(previewButtonTable).colspan(3).center().padTop(10f)

        // Add content to window with forced minimum width
        previewControllerWindow.add(controllerContent).minWidth(800f)
        previewControllerWindow.pack()
        previewControllerWindow.isVisible = false
        stage.addActor(previewControllerWindow)

        // --- SETUP LISTENERS ---

        // File & Sequence Management Listeners
        fileSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = populateSequenceList() })
        sequenceList.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = populateLineEditor() })

        newFileButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { uiManager.showTextInputDialog("New Dialogue File", "chapter2.json") { fileName -> if (fileName.endsWith(".json")) { if (dialogueManager.createNewFile(fileName)) { populateFileSelectBox(); fileSelectBox.selected = fileName } } else { uiManager.showTemporaryMessage("Error: File must end with .json") } } } })

        newSequenceButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { val selectedFile = fileSelectBox.selected ?: return; val newId = "dialogue_${System.currentTimeMillis().toString().takeLast(6)}"; if (dialogueManager.createNewSequence(selectedFile, newId)) { populateSequenceList(); sequenceList.selected = newId } } })

        deleteSequenceButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { val selectedFile = fileSelectBox.selected ?: return; val selectedSequenceId = sequenceList.selected ?: return; dialogueManager.deleteSequence(selectedFile, selectedSequenceId); populateSequenceList() } })

        addLineButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { val newLine = DialogueLineData(); linesContainer.addActor(createLineWidget(newLine)) } })

        // Main Window Buttons
        saveButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                saveCurrentChanges()
                dialogueManager.loadAllDialogues()
                uiManager.game.missionSystem.refreshDialogueIds()
                hide()
            }
        })
        closeButton.addListener(object: ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = hide() })

        // Preview Listeners
        previewButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = startPreview() })

        // Preview Window Buttons
        closePreviewButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = stopPreview() })
        nextLineButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = navigatePreview(1) })
        prevLineButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = navigatePreview(-1) })

        // Save & Close button
        saveAndCloseButton.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                // Save logic based on mode
                if (editingInstance != null) {
                    // Instance Mode: Changes are saved to the in-memory object.
                    // We need to trigger a Mission Save or notify the user.
                    if (uiManager.game.uiManager.currentEditorMode == EditorMode.MISSION) {
                        val mission = uiManager.game.uiManager.selectedMissionForEditing
                        if (mission != null) uiManager.game.missionSystem.saveMission(mission)
                    } else {
                        uiManager.showTemporaryMessage("Instance settings updated. Save Game manually.")
                    }
                } else {
                    // Normal Mode: Save changes to the JSON file.
                    saveCurrentChanges()
                }

                dialogueManager.loadAllDialogues()
                uiManager.game.missionSystem.refreshDialogueIds()
                hide()
            }
        })

        // --- SMART SLIDER/INPUT DUAL CONTROLS ---
        // This helper function creates two listeners that keep the Slider and TextField in sync
        fun setupDualControl(slider: Slider, input: TextField) {
            // 1. Slider -> Input & Game
            slider.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    if (!isUpdatingSliders) {
                        // Update text field to match slider (cast to int for cleaner display)
                        input.text = slider.value.toInt().toString()

                        // Trigger visual update in game
                        if (isPreviewing) updateLineFromSliders()
                    }
                }
            })

            // 2. Input -> Slider & Game
            input.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    val valStr = input.text
                    val valFloat = valStr.toFloatOrNull()

                    if (valFloat != null) {
                        // Only update if value actually changed to prevent infinite loop
                        if (slider.value != valFloat) {
                            isUpdatingSliders = true // Lock slider listener
                            slider.value = valFloat  // Move slider knob
                            isUpdatingSliders = false // Unlock

                            // Trigger visual update in game
                            if (isPreviewing) updateLineFromSliders()
                        }
                    }
                }
            })
        }

        // Apply Dual Control to all 4 pairs
        setupDualControl(widthSlider, widthInput)
        setupDualControl(heightSlider, heightInput)
        setupDualControl(portraitXSlider, portraitXInput)
        setupDualControl(portraitYSlider, portraitYInput)

        setupDualControl(camDistSlider, camDistInput)
        setupDualControl(camHeightSlider, camHeightInput)

        // Preset Button Logic
        applyPresetButton.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (!isPreviewing) return

                val currentLineWidget = linesContainer.children.get(previewLineIndex) as? Table ?: return

                // Standard preset values
                val presetWidth = "%.0f".format(Gdx.graphics.width * 0.7f)
                val presetHeight = "%.0f".format(Gdx.graphics.height * 0.28f)
                val presetPortraitX = "-80"
                val presetPortraitY = "25"

                // Update text fields
                currentLineWidget.findActor<TextField>("customWidthField")?.text = presetWidth
                currentLineWidget.findActor<TextField>("customHeightField")?.text = presetHeight
                currentLineWidget.findActor<TextField>("portraitXField")?.text = presetPortraitX
                currentLineWidget.findActor<TextField>("portraitYField")?.text = presetPortraitY

                // Force sliders to sync with these new text values
                updateSlidersFromLine(currentLineWidget)
                // Force game visual update
                updateLineFromSliders()
            }
        })
    }

    private fun scanAvailableTextures() {
        Gdx.app.log("DialogueEditor", "--- Starting texture scan ---")
        Gdx.app.log("DialogueEditor", "Working directory: ${System.getProperty("user.dir")}")
        availableTextures.clear()
        availableTextures.add("(None)")

        val textureFolders = arrayOf(
            "textures/characters",
            "textures/player",
            "textures/player/animations",
            "textures/player/player_story_start"
        )

        textureFolders.forEach { folderPath ->
            try {
                // Try internal first (for packaged/deployed apps)
                var folder = Gdx.files.internal(folderPath)

                // If internal doesn't work as directory, try with assets/ prefix
                if (!folder.isDirectory && folder.exists()) {
                    Gdx.app.log("DialogueEditor", "  Internal path exists but not as directory, trying local...")
                    folder = Gdx.files.local("assets/$folderPath")
                }

                Gdx.app.log("DialogueEditor", "Checking folder: $folderPath")
                Gdx.app.log("DialogueEditor", "  Full path: ${folder.file().absolutePath}")
                Gdx.app.log("DialogueEditor", "  Exists: ${folder.exists()}")
                Gdx.app.log("DialogueEditor", "  Is directory: ${folder.isDirectory}")

                if (folder.exists() && folder.isDirectory) {
                    Gdx.app.log("DialogueEditor", "  Scanning folder: ${folder.path()}")
                    scanFolderForImages(folder, folderPath)
                } else {
                    Gdx.app.log("DialogueEditor", "  Skipping - folder not accessible")
                }
            } catch (e: Exception) {
                Gdx.app.error("DialogueEditor", "Error accessing folder '$folderPath': ${e.message}", e)
            }
        }

        availableTextures.sort()
        availableTextures.removeValue("(None)", false)
        availableTextures.insert(0, "(None)")

        Gdx.app.log("DialogueEditor", "--- Finished scanning. Found ${availableTextures.size - 1} total textures. ---")
        if (availableTextures.size > 1) {
            Gdx.app.log("DialogueEditor", "First 5 textures: ${availableTextures.take(6).toList()}")
        }
    }

    private fun scanFolderForImages(folder: FileHandle, basePath: String) {
        val imageExtensions = setOf("png", "jpg", "jpeg", "bmp")

        try {
            val files = folder.list()
            Gdx.app.log("DialogueEditor", "    Scanning ${files.size} items in ${folder.path()}")

            files.forEach { file ->
                try {
                    if (file.isDirectory) {
                        scanFolderForImages(file, basePath)
                    } else {
                        val ext = file.extension().lowercase()
                        if (ext in imageExtensions) {
                            // Use the original base path to ensure correct relative path
                            val relativePath = file.path()
                                .replace('\\', '/')
                                .substringAfter("assets/")
                                .let { if (it.startsWith(basePath)) it else "$basePath/${file.name()}" }

                            availableTextures.add(relativePath)
                            Gdx.app.log("DialogueEditor", "      -> Found texture: $relativePath")
                        }
                    }
                } catch (e: Exception) {
                    Gdx.app.error("DialogueEditor", "      Error processing file '${file.name()}': ${e.message}")
                }
            }
        } catch (e: Exception) {
            Gdx.app.error("DialogueEditor", "    Error listing folder '${folder.path()}': ${e.message}", e)
        }
    }

    private fun saveCurrentChanges() {
        val selectedFile = fileSelectBox.selected ?: return
        val dialogueFile = dialogueManager.getDialogueFile(selectedFile) ?: return

        // --- Save the currently edited sequence ---
        val currentSequenceId = sequenceList.selected
        if (currentSequenceId != null) {
            val sequenceData = dialogueFile.get(currentSequenceId)
            if (sequenceData != null) {
                // 1. Update ID if changed
                val newId = sequenceIdField.text
                if (newId != currentSequenceId && newId.isNotBlank()) {
                    dialogueFile.remove(currentSequenceId)
                    dialogueFile.put(newId, sequenceData)
                }

                // 2. Clear old lines and add new ones from the UI
                sequenceData.lines.clear()
                linesContainer.children.forEach { actor ->
                    if (actor is Table) {
                        val speakerField = actor.findActor<TextField>("speaker")
                        val textField = actor.findActor<TextArea>("text")
                        val textureField = actor.findActor<TextField>("texture")

                        // Only try to save the line if all required fields were successfully found.
                        if (speakerField != null && textField != null && textureField != null) {
                            sequenceData.lines.add(DialogueLineData().apply {
                                speaker = speakerField.text
                                text = textField.text
                                speakerTexturePath = textureField.text.replace('\\', '/').ifBlank { null }
                                customWidth = actor.findActor<TextField>("customWidthField").text.toFloatOrNull()
                                customHeight = actor.findActor<TextField>("customHeightField").text.toFloatOrNull()
                                portraitOffsetX = actor.findActor<TextField>("portraitXField").text.toFloatOrNull()
                                portraitOffsetY = actor.findActor<TextField>("portraitYField").text.toFloatOrNull()
                            })
                        }
                    }
                }
            }
        }

        dialogueManager.saveDialogueFile(selectedFile, dialogueFile)
    }

    private fun createLineWidget(lineData: DialogueLineData): Actor {
        val table = Table(skin)
        table.background = skin.getDrawable("textfield") // Use a background to frame the line
        table.pad(8f).defaults().pad(3f)

        val speakerField = TextField(lineData.speaker, skin).apply { name = "speaker" }
        val textField = TextArea(lineData.text, skin).apply { name = "text" }
        val removeButton = TextButton("X", skin)

        table.add(Label("Speaker:", skin)).left()
        table.add(speakerField).growX().colspan(2).row()
        table.add(Label("Text:", skin)).left().top()
        table.add(textField).growX().height(60f).colspan(2).row()

        // Portrait selection section with preview
        table.add(Label("Portrait:", skin)).left().top()

        val portraitControlTable = Table()

        // Dropdown selector
        val textureSelectBox = SelectBox<String>(skin)
        textureSelectBox.items = availableTextures

        // Manual path field
        val textureField = TextField(lineData.speakerTexturePath ?: "", skin).apply {
            name = "texture"
            messageText = "Or enter custom path..."
        }

        val initialPath = lineData.speakerTexturePath
        if (!initialPath.isNullOrBlank()) {
            val normalizedPath = initialPath.replace('\\', '/')
            val matchIndex = availableTextures.indexOfFirst { it.equals(normalizedPath, ignoreCase = true) }
            if (matchIndex != -1) {
                textureSelectBox.selectedIndex = matchIndex
            } else {
                textureSelectBox.selectedIndex = 0
            }
        } else {
            textureSelectBox.selectedIndex = 0 // Select "(None)"
        }

        // Preview image
        val previewImage = Image()
        previewImage.setSize(64f, 64f)

        // Update preview function
        fun updatePreview(path: String?) {
            try {
                if (!path.isNullOrBlank() && path != "(None)") {
                    val texture = previewTextures.getOrPut(path) {
                        val textureFile = Gdx.files.internal(path)
                        if (textureFile.exists()) Texture(textureFile) else throw Exception("File not found")
                    }
                    previewImage.drawable = TextureRegionDrawable(TextureRegion(texture))
                } else {
                    previewImage.drawable = null
                }
            } catch (e: Exception) {
                previewImage.drawable = null
                Gdx.app.error("DialogueEditor", "Failed to load preview for '$path': ${e.message}")
            }
        }

        // Initial preview
        updatePreview(lineData.speakerTexturePath)

        // Listener for dropdown - updates text field when dropdown changes
        textureSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val selected = textureSelectBox.selected
                if (selected != "(None)") {
                    textureField.text = selected
                    updatePreview(selected)
                } else {
                    textureField.text = ""
                    updatePreview(null)
                }
            }
        })

        // Listener for manual text field - updates dropdown if matching path exists
        textureField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val enteredPath = textureField.text.trim()
                if (enteredPath.isNotBlank()) {
                    updatePreview(enteredPath)
                    val normalizedPath = enteredPath.replace('\\', '/')
                    val matchIndex = availableTextures.indexOfFirst { it.equals(normalizedPath, ignoreCase = true) }
                    if (matchIndex != -1) {
                        textureSelectBox.selectedIndex = matchIndex
                    } else {
                        // Path not in dropdown, but might still be valid - keep dropdown at (None)
                        textureSelectBox.selectedIndex = 0
                    }
                } else {
                    updatePreview(null)
                    textureSelectBox.selectedIndex = 0
                }
            }
        })

        portraitControlTable.add(Label("Select:", skin)).padRight(5f)
        portraitControlTable.add(textureSelectBox).width(250f).row()
        portraitControlTable.add(Label("Path:", skin)).padRight(5f).padTop(5f)
        portraitControlTable.add(textureField).width(250f).padTop(5f)

        table.add(portraitControlTable).left().padRight(10f)
        table.add(previewImage).size(64f).right().row()

        val modifiersTable = Table(skin)
        modifiersTable.defaults().pad(2f)

        val customWidthField = TextField(lineData.customWidth?.toString() ?: "", skin).apply { name = "customWidthField"; messageText = "Default" }
        val customHeightField = TextField(lineData.customHeight?.toString() ?: "", skin).apply { name = "customHeightField"; messageText = "Default" }
        val portraitXField = TextField(lineData.portraitOffsetX?.toString() ?: "", skin).apply { name = "portraitXField"; messageText = "0" }
        val portraitYField = TextField(lineData.portraitOffsetY?.toString() ?: "", skin).apply { name = "portraitYField"; messageText = "0" }

        modifiersTable.add(Label("W:", skin)); modifiersTable.add(customWidthField).width(70f)
        modifiersTable.add(Label("H:", skin)).padLeft(8f); modifiersTable.add(customHeightField).width(70f)
        modifiersTable.add(Label("PX:", skin)).padLeft(8f); modifiersTable.add(portraitXField).width(70f)
        modifiersTable.add(Label("PY:", skin)).padLeft(8f); modifiersTable.add(portraitYField).width(70f)

        table.add(Label("Modifiers:", skin)).left().padTop(8f)
        table.add(modifiersTable).colspan(2).left().padTop(8f).row()

        table.add(removeButton).right().colspan(3)

        removeButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                linesContainer.removeActor(table)
            }
        })

        return table
    }

    fun show() {
        window.isVisible = true
        window.toFront()
        populateFileSelectBox() // Load data when the editor is shown
    }

    fun selectSequence(fileName: String, sequenceId: String) {
        // 1. Select the file
        if (fileSelectBox.items.contains(fileName)) {
            fileSelectBox.selected = fileName
            // Trigger the update manually
            val event = ChangeListener.ChangeEvent()
            fileSelectBox.fire(event)
        }

        // 2. Wait for the list to populate, then select the sequence
        // Since populateSequenceList is synchronous, we can just set it immediately
        if (sequenceList.items.contains(sequenceId)) {
            sequenceList.selected = sequenceId
            // Trigger the update to load the lines
            val event = ChangeListener.ChangeEvent()
            sequenceList.fire(event)
        }
    }

    private fun populateFileSelectBox() {
        val fileNames = dialogueManager.getDialogueFileNames()
        if (fileNames.isEmpty()) {
            fileSelectBox.setItems("(No files found)")
            sequenceList.clearItems()
        } else {
            fileSelectBox.items = GdxArray(fileNames.toTypedArray())
            // Automatically select the first file and populate the next list
            if (fileSelectBox.items.size > 0) {
                fileSelectBox.selectedIndex = 0
                populateSequenceList()
            }
        }
    }

    private fun populateSequenceList() {
        val selectedFile = fileSelectBox.selected ?: return
        val dialogueFile = dialogueManager.getDialogueFile(selectedFile) ?: return

        val sequenceIdArray = GdxArray<String>()
        for (id in dialogueFile.keys()) {
            sequenceIdArray.add(id)
        }
        sequenceList.setItems(sequenceIdArray)

        if (sequenceList.items.size > 0) {
            sequenceList.selectedIndex = 0
            populateLineEditor()
        } else {
            // Clear the editor if there are no sequences
            sequenceIdField.text = ""
            linesContainer.clearChildren()
        }
    }

    private fun populateLineEditor() {
        val selectedFile = fileSelectBox.selected ?: return
        val selectedSequenceId = sequenceList.selected ?: return
        val dialogueFile = dialogueManager.getDialogueFile(selectedFile) ?: return
        val sequenceData = dialogueFile.get(selectedSequenceId) ?: return

        sequenceIdField.text = selectedSequenceId
        linesContainer.clearChildren()

        sequenceData.lines.forEach { lineData ->
            linesContainer.addActor(createLineWidget(lineData))
        }
    }

    fun hide() {
        if (isPreviewing) stopPreview()
        window.isVisible = false
        stage.unfocusAll()

        // Reset to Normal Mode
        editingInstance = null
        editingInstanceCharacter = null
        window.titleLabel.setText("Dialogue Editor")
        fileSelectBox.isDisabled = false
        lockTextEditing(false)
    }

    fun isVisible(): Boolean = window.isVisible

    fun dispose() {
        previewTextures.values.forEach { it.dispose() }
        previewTextures.clear()
    }

    private fun startPreview() {
        if (isPreviewing) return
        if (linesContainer.children.size == 0) {
            uiManager.showTemporaryMessage("Cannot preview an empty dialogue.")
            return
        }

        isPreviewing = true
        previewLineIndex = 0
        window.isVisible = false // Hide main editor
        previewControllerWindow.isVisible = true
        previewControllerWindow.toFront()

        currentPreviewLines.clear()
        linesContainer.children.forEach { actor ->
            if (actor is Table) {
                currentPreviewLines.add(getLineDataFromWidget(actor))
            }
        }

        showLineInPreview()
    }

    private fun stopPreview() {
        if (!isPreviewing) return
        isPreviewing = false
        previewControllerWindow.isVisible = false
        uiManager.dialogSystem.hidePreview()

        // Clear the highlight and show the main editor again
        highlightedLineWidget?.background = skin.getDrawable("textfield")
        highlightedLineWidget = null
        window.isVisible = true
        window.toFront()
    }

    private fun navigatePreview(direction: Int) {
        if (!isPreviewing || currentPreviewLines.isEmpty()) return

        val newIndex = previewLineIndex + direction
        if (newIndex in currentPreviewLines.indices) {
            previewLineIndex = newIndex
            showLineInPreview()
        }
    }

    private fun showLineInPreview() {
        if (!isPreviewing) return

        // 1. Highlight the active line in the list (Visual feedback)
        // Reset the background of the previously highlighted widget
        highlightedLineWidget?.background = skin.getDrawable("textfield")

        // Find and highlight the new widget
        val currentLineWidget = linesContainer.children.get(previewLineIndex) as? Table
        if (currentLineWidget != null) {
            currentLineWidget.background = skin.newDrawable("textfield", Color.DARK_GRAY)
            highlightedLineWidget = currentLineWidget

            // IMPORTANT: Update the sliders to match the current line's data
            // (This prevents the sliders from jumping when switching lines)
            // We set the flag to true so this doesn't trigger a save immediately
            isUpdatingSliders = true
            updateSlidersFromLine(currentLineWidget)
            isUpdatingSliders = false
        }

        // 2. Update the info label
        lineInfoLabel.setText("Line: ${previewLineIndex + 1}/${currentPreviewLines.size}")

        // 3. DETERMINE PREVIEW DATA
        // If we are in Instance Mode (editingInstance != null), we pass the real game data.
        // If we are in Normal Mode, these will be null, and it behaves like a standard preview.
        val outcomeToPreview = editingInstance?.outcome
        val overridesToPreview = editingInstance?.styleOverrides

        // 4. CALL THE SYSTEM
        // This is the key fix. We pass the outcome.
        // The DialogSystem checks if outcome.type is SELL/BUY/TRADE.
        // If yes, and it's the last line, it draws the buttons.
        uiManager.dialogSystem.previewDialog(
            currentPreviewLines,
            previewLineIndex,
            outcomeToPreview, // <--- Pass the transaction info here
            overridesToPreview // <--- Pass the visual overrides here
        )
    }

    private fun getLineDataFromWidget(widget: Table): DialogLine {
        // Helper function to safely parse Float from a TextField
        fun Actor?.toFloatOrNull(): Float? = (this as? TextField)?.text?.toFloatOrNull()

        return DialogLine(
            speaker = (widget.findActor("speaker") as TextField).text,
            text = (widget.findActor("text") as TextArea).text,
            speakerTexturePath = (widget.findActor("texture") as TextField).text.ifBlank { null },
            customWidth = widget.findActor<TextField>("customWidthField").toFloatOrNull(),
            customHeight = widget.findActor<TextField>("customHeightField").toFloatOrNull(),
            portraitOffsetX = widget.findActor<TextField>("portraitXField").toFloatOrNull(),
            portraitOffsetY = widget.findActor<TextField>("portraitYField").toFloatOrNull()
        )
    }

    fun editCharacterInstance(character: Any, dialogInfo: StandaloneDialog) {
        // 1. Set Mode
        editingInstance = dialogInfo
        editingInstanceCharacter = character

        // 2. Load the file and sequence
        // We have to find which file contains this ID
        var foundFile: String? = null
        for (fileName in dialogueManager.getDialogueFileNames()) {
            val file = dialogueManager.getDialogueFile(fileName)
            if (file != null && file.containsKey(dialogInfo.dialogId)) {
                foundFile = fileName
                break
            }
        }

        if (foundFile == null) {
            uiManager.showTemporaryMessage("Error: Dialogue ID '${dialogInfo.dialogId}' not found in any file.")
            return
        }

        show() // Open window

        // 3. Set UI state
        window.titleLabel.setText("Dialogue Editor - [INSTANCE OVERRIDE MODE]")
        fileSelectBox.selected = foundFile
        fileSelectBox.isDisabled = true // Lock file selection
        sequenceList.selected = dialogInfo.dialogId
        // We don't disable the list, in case they want to swap the ID for this character

        // 4. Populate fields but LOCK text editing
        populateLineEditor()
        lockTextEditing(true)

        // 5. Apply existing overrides to the UI sliders if a line is selected
        // (This happens automatically via populateLineEditor -> createLineWidget)
    }

    private fun lockTextEditing(locked: Boolean) {
        linesContainer.children.forEach { actor ->
            if (actor is Table) {
                actor.findActor<TextField>("speaker")?.isDisabled = locked
                actor.findActor<TextArea>("text")?.isDisabled = locked
                actor.findActor<TextField>("texture")?.isDisabled = locked
                // We leave the "Modifiers" fields unlocked!
            }
        }
        // Visual feedback
        if (locked) {
            uiManager.showTemporaryMessage("Editing Instance: Text is locked. Modify visuals only.")
        }
    }

    private fun updateLineFromSliders() {
        if (isUpdatingSliders) return // Prevent recursion loop

        // 1. Get values from sliders
        val newWidth = widthSlider.value
        val newHeight = heightSlider.value
        val newX = portraitXSlider.value
        val newY = portraitYSlider.value

        // Format strings for the text fields
        val widthStr = "%.0f".format(newWidth)
        val heightStr = "%.0f".format(newHeight)
        val xStr = "%.0f".format(newX)
        val yStr = "%.0f".format(newY)

        // 2. Update the UI Text Fields (Visual Feedback in the Editor List)
        if (syncSettingsCheckbox.isChecked) {
            // If sync is on, apply to ALL lines visually
            linesContainer.children.forEach { actor ->
                if (actor is Table) {
                    actor.findActor<TextField>("customWidthField")?.text = widthStr
                    actor.findActor<TextField>("customHeightField")?.text = heightStr
                    actor.findActor<TextField>("portraitXField")?.text = xStr
                    actor.findActor<TextField>("portraitYField")?.text = yStr
                }
            }
        } else {
            // Apply only to current line widget
            val currentLineWidget = linesContainer.children.get(previewLineIndex) as? Table
            currentLineWidget?.findActor<TextField>("customWidthField")?.text = widthStr
            currentLineWidget?.findActor<TextField>("customHeightField")?.text = heightStr
            currentLineWidget?.findActor<TextField>("portraitXField")?.text = xStr
            currentLineWidget?.findActor<TextField>("portraitYField")?.text = yStr
        }

        // 3. Save Data & Refresh Preview
        val camDist = camDistInput.text.toFloatOrNull()
        val camHeight = camHeightInput.text.toFloatOrNull()

        if (editingInstance != null) {
            // --- INSTANCE MODE ---
            if (syncSettingsCheckbox.isChecked) {
                for (i in currentPreviewLines.indices) {
                    val override = editingInstance!!.styleOverrides.getOrPut(i.toString()) { LineStyleOverride() }
                    override.customWidth = newWidth
                    override.customHeight = newHeight
                    override.portraitOffsetX = newX
                    override.portraitOffsetY = newY
                    override.cameraDistance = camDist
                    override.cameraHeight = camHeight
                }
            } else {
                val override = editingInstance!!.styleOverrides.getOrPut(previewLineIndex.toString()) { LineStyleOverride() }
                override.customWidth = newWidth
                override.customHeight = newHeight
                override.portraitOffsetX = newX
                override.portraitOffsetY = newY
                override.cameraDistance = camDist
                override.cameraHeight = camHeight
            }

            // Refresh Preview
            uiManager.dialogSystem.previewDialog(
                currentPreviewLines,
                previewLineIndex,
                editingInstance!!.outcome,
                editingInstance!!.styleOverrides
            )

        } else {
            // --- NORMAL MODE ---
            // We are editing the base JSON data via the text fields.
            // We need to update the 'currentPreviewLines' data structure so the preview reflects the slider change immediately.

            if (syncSettingsCheckbox.isChecked) {
                linesContainer.children.forEachIndexed { index, actor ->
                    if (index < currentPreviewLines.size && actor is Table) {
                        // Re-read the data from the widget (which we just updated in Step 2)
                        currentPreviewLines[index] = getLineDataFromWidget(actor)
                    }
                }
            } else {
                val currentLineWidget = linesContainer.children.get(previewLineIndex) as? Table
                if (currentLineWidget != null) {
                    currentPreviewLines[previewLineIndex] = getLineDataFromWidget(currentLineWidget)
                }
            }

            // Refresh preview normally
            uiManager.dialogSystem.previewDialog(currentPreviewLines, previewLineIndex)
        }
    }

    private fun updateSlidersFromLine(widget: Table) {
        fun TextField?.toFloatOrDefault(default: Float): Float = this?.text?.toFloatOrNull() ?: default

        isUpdatingSliders = true // Lock listeners to prevent saving while loading

        // Read from the Main Editor Widgets
        val w = widget.findActor<TextField>("customWidthField").toFloatOrDefault(Gdx.graphics.width * 0.7f)
        val h = widget.findActor<TextField>("customHeightField").toFloatOrDefault(Gdx.graphics.height * 0.28f)
        val x = widget.findActor<TextField>("portraitXField").toFloatOrDefault(0f)
        val y = widget.findActor<TextField>("portraitYField").toFloatOrDefault(0f)

        // Update Sliders
        widthSlider.value = w
        heightSlider.value = h
        portraitXSlider.value = x
        portraitYSlider.value = y

        // Update TextFields
        widthInput.text = w.toInt().toString()
        heightInput.text = h.toInt().toString()
        portraitXInput.text = x.toInt().toString()
        portraitYInput.text = y.toInt().toString()

        if (editingInstance != null) {
            // 1. Get the override for the current line (if it exists)
            val override = editingInstance!!.styleOverrides[previewLineIndex.toString()]

            // 2. Update Inputs (Empty string means "Default")
            camDistInput.text = override?.cameraDistance?.toString() ?: ""
            camHeightInput.text = override?.cameraHeight?.toString() ?: ""

            // 3. Update Sliders (If null, set to min value or a default)
            camDistSlider.value = override?.cameraDistance ?: 2f
            camHeightSlider.value = override?.cameraHeight ?: 0f

        } else {
            // Normal Mode (Camera editing not supported in JSON mode yet)
            camDistInput.text = ""
            camHeightInput.text = ""
        }

        isUpdatingSliders = false
    }
}
