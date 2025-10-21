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

    private var highlightedLineWidget: Table? = null
    private var isUpdatingSliders = false

    init {
        window.isMovable = true
        window.isModal = false
        window.setSize(Gdx.graphics.width * 1.9f, Gdx.graphics.height * 1.7f)
        window.setPosition(stage.width / 2f, stage.height / 2f, Align.center)
        window.padTop(40f)

        // Scan for available textures
        scanAvailableTextures()

        val mainContentTable = Table()

        // --- Left Panel ---
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

        // --- Right Panel ---
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

        // --- Assembly ---
        val splitPane = SplitPane(leftPanel, rightPanel, false, skin)
        splitPane.splitAmount = 0.3f
        mainContentTable.add(splitPane).expand().fill().row()

        val saveButton = TextButton("Save & Reload", skin)
        val closeButton = TextButton("Close", skin)
        val bottomButtonTable = Table()
        bottomButtonTable.add(saveButton).pad(10f)

        val previewButton = TextButton("Preview", skin)
        bottomButtonTable.add(previewButton).pad(10f) // Add it next to Save/Close

        bottomButtonTable.add(closeButton).pad(10f)
        mainContentTable.add(bottomButtonTable).padTop(10f)

        window.add(mainContentTable).expand().fill()
        window.isVisible = false
        stage.addActor(window)

        // Preview Controller Window Setup
        previewControllerWindow = Window("Live Preview", skin, "dialog")
        previewControllerWindow.isMovable = true
        previewControllerWindow.padTop(40f)

        val controllerContent = Table()
        controllerContent.pad(10f).defaults().pad(4f)

        lineInfoLabel = Label("Line: 0/0", skin)
        widthSlider = Slider(200f, Gdx.graphics.width * 0.95f, 10f, false, skin)
        heightSlider = Slider(100f, Gdx.graphics.height * 0.5f, 5f, false, skin)
        portraitXSlider = Slider(-200f, 200f, 1f, false, skin)
        portraitYSlider = Slider(-100f, 200f, 1f, false, skin)

        val prevLineButton = TextButton("<", skin)
        val nextLineButton = TextButton(">", skin)
        val navigationTable = Table()
        navigationTable.add(prevLineButton).padRight(10f)
        navigationTable.add(lineInfoLabel)
        navigationTable.add(nextLineButton).padLeft(10f)

        controllerContent.add(navigationTable).colspan(2).center().padBottom(10f).row()
        controllerContent.add(Label("Width:", skin)).left(); controllerContent.add(widthSlider).growX().row()
        controllerContent.add(Label("Height:", skin)).left(); controllerContent.add(heightSlider).growX().row()
        controllerContent.add(Label("Portrait X:", skin)).left(); controllerContent.add(portraitXSlider).growX().row()
        controllerContent.add(Label("Portrait Y:", skin)).left(); controllerContent.add(portraitYSlider).growX().row()

        val previewButtonTable = Table()
        val saveAndCloseButton = TextButton("Save & Close", skin)
        val closePreviewButton = TextButton("Back to Editor", skin) // Renamed for clarity
        previewButtonTable.add(saveAndCloseButton).pad(5f)
        previewButtonTable.add(closePreviewButton).pad(5f)
        controllerContent.add(previewButtonTable).colspan(2).center().padTop(10f)

        previewControllerWindow.add(controllerContent)
        previewControllerWindow.pack()
        previewControllerWindow.isVisible = false
        stage.addActor(previewControllerWindow)

        // --- LISTENERS ---
        fileSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = populateSequenceList() })
        sequenceList.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = populateLineEditor() })
        newFileButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { uiManager.showTextInputDialog("New Dialogue File", "chapter2.json") { fileName -> if (fileName.endsWith(".json")) { if (dialogueManager.createNewFile(fileName)) { populateFileSelectBox(); fileSelectBox.selected = fileName } } else { uiManager.showTemporaryMessage("Error: File must end with .json") } } } })
        newSequenceButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { val selectedFile = fileSelectBox.selected ?: return; val newId = "dialogue_${System.currentTimeMillis().toString().takeLast(6)}"; if (dialogueManager.createNewSequence(selectedFile, newId)) { populateSequenceList(); sequenceList.selected = newId } } })
        deleteSequenceButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { val selectedFile = fileSelectBox.selected ?: return; val selectedSequenceId = sequenceList.selected ?: return; dialogueManager.deleteSequence(selectedFile, selectedSequenceId); populateSequenceList() } })
        addLineButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { val newLine = DialogueLineData(); linesContainer.addActor(createLineWidget(newLine)) } })

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
        closePreviewButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = stopPreview() })
        nextLineButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = navigatePreview(1) })
        prevLineButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = navigatePreview(-1) })

        // Save & Close button
        saveAndCloseButton.addListener(object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                saveCurrentChanges()
                dialogueManager.loadAllDialogues()
                uiManager.game.missionSystem.refreshDialogueIds()
                hide() // This now correctly closes both windows and returns focus
            }
        })

        val sliderListener = object: ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (isPreviewing) updateLineFromSliders()
            }
        }
        widthSlider.addListener(sliderListener)
        heightSlider.addListener(sliderListener)
        portraitXSlider.addListener(sliderListener)
        portraitYSlider.addListener(sliderListener)
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
        if (isPreviewing) {
            stopPreview()
        }
        window.isVisible = false
        stage.unfocusAll()
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

        // Reset the background of the previously highlighted widget
        highlightedLineWidget?.background = skin.getDrawable("textfield")

        // Find and highlight the new widget
        val currentLineWidget = linesContainer.children.get(previewLineIndex) as? Table
        if (currentLineWidget != null) {
            currentLineWidget.background = skin.newDrawable("textfield", Color.DARK_GRAY)
            highlightedLineWidget = currentLineWidget
            updateSlidersFromLine(currentLineWidget)
        }

        lineInfoLabel.setText("Line: ${previewLineIndex + 1}/${currentPreviewLines.size}")
        uiManager.dialogSystem.previewDialog(currentPreviewLines, previewLineIndex)
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

    private fun updateLineFromSliders() {
        if (isUpdatingSliders) return // Prevent recursion

        val currentLineWidget = linesContainer.children.get(previewLineIndex) as? Table ?: return

        // Find the text fields in the editor widget
        val widthField = currentLineWidget.findActor<TextField>("customWidthField")
        val heightField = currentLineWidget.findActor<TextField>("customHeightField")
        val xField = currentLineWidget.findActor<TextField>("portraitXField")
        val yField = currentLineWidget.findActor<TextField>("portraitYField")

        // Update the text fields with the slider values
        widthField.text = "%.0f".format(widthSlider.value)
        heightField.text = "%.0f".format(heightSlider.value)
        xField.text = "%.0f".format(portraitXSlider.value)
        yField.text = "%.0f".format(portraitYSlider.value)

        // Re-read the data from the widget and refresh the preview
        currentPreviewLines[previewLineIndex] = getLineDataFromWidget(currentLineWidget)
        uiManager.dialogSystem.previewDialog(currentPreviewLines, previewLineIndex)
    }

    private fun updateSlidersFromLine(widget: Table) {
        // Helper to read from a TextField with proper default value
        fun TextField?.toFloatOrDefault(default: Float): Float = this?.text?.toFloatOrNull() ?: default

        // Set the flag to prevent the change listener from triggering updateLineFromSliders
        isUpdatingSliders = true

        // Update slider values from the text fields
        widthSlider.value = widget.findActor<TextField>("customWidthField").toFloatOrDefault(Gdx.graphics.width * 0.7f)
        heightSlider.value = widget.findActor<TextField>("customHeightField").toFloatOrDefault(Gdx.graphics.height * 0.28f)
        portraitXSlider.value = widget.findActor<TextField>("portraitXField").toFloatOrDefault(0f)
        portraitYSlider.value = widget.findActor<TextField>("portraitYField").toFloatOrDefault(0f)

        // Reset the flag after a brief moment to allow the sliders to settle
        isUpdatingSliders = false
    }
}
