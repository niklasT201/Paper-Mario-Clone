package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
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
        //val refreshButton = TextButton("Refresh", skin)
        fileTable.add(Label("File:", skin)).left()
        fileTable.add(fileSelectBox).growX().padLeft(5f).padRight(5f)
        fileTable.add(newFileButton)//.padRight(5f)
        //fileTable.add(refreshButton)
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
        bottomButtonTable.add(closeButton).pad(10f)
        mainContentTable.add(bottomButtonTable).padTop(10f)

        window.add(mainContentTable).expand().fill()
        window.isVisible = false
        stage.addActor(window)

        // --- LISTENERS ---
        fileSelectBox.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = populateSequenceList() })
        sequenceList.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = populateLineEditor() })

        newFileButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                uiManager.showTextInputDialog("New Dialogue File", "chapter2.json") { fileName ->
                    if (fileName.endsWith(".json")) {
                        if (dialogueManager.createNewFile(fileName)) {
                            populateFileSelectBox()
                            fileSelectBox.selected = fileName
                        }
                    } else {
                        uiManager.showTemporaryMessage("Error: File must end with .json")
                    }
                }
            }
        })
//
//        refreshButton.addListener(object : ChangeListener() {
//            override fun changed(event: ChangeEvent?, actor: Actor?) {
//                scanAvailableTextures()
//                uiManager.showTemporaryMessage("Found ${availableTextures.size - 1} textures")
//            }
//        })

        newSequenceButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val selectedFile = fileSelectBox.selected ?: return
                val newId = "dialogue_${System.currentTimeMillis().toString().takeLast(6)}"
                if (dialogueManager.createNewSequence(selectedFile, newId)) {
                    populateSequenceList()
                    sequenceList.selected = newId
                }
            }
        })

        deleteSequenceButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val selectedFile = fileSelectBox.selected ?: return
                val selectedSequenceId = sequenceList.selected ?: return
                dialogueManager.deleteSequence(selectedFile, selectedSequenceId)
                populateSequenceList()
            }
        })

        addLineButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val newLine = DialogueLineData()
                linesContainer.addActor(createLineWidget(newLine))
            }
        })

        saveButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                saveCurrentChanges()
                dialogueManager.loadAllDialogues()
                uiManager.game.missionSystem.refreshDialogueIds()
                hide()
            }
        })

        closeButton.addListener(object: ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) = hide() })
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
                    val matchIndex = availableTextures.indexOfFirst {
                        it.equals(normalizedPath, ignoreCase = true)
                    }

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
        window.isVisible = false
        stage.unfocusAll()
    }

    fun isVisible(): Boolean = window.isVisible

    fun dispose() {
        previewTextures.values.forEach { it.dispose() }
        previewTextures.clear()
    }
}
