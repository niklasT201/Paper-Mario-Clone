package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.Array

class DialogueManager {

    private val json = Json().apply {
        setOutputType(JsonWriter.OutputType.json)
        setUsePrototypes(false)
    }

    // This map will hold all dialogues from all files, keyed by their file name.
    private val loadedFiles = mutableMapOf<String, DialogueFile>()

    private val DIALOGUES_DIR = "dialogues"

    fun loadAllDialogues() {
        loadedFiles.clear()
        val dirHandle = Gdx.files.local(DIALOGUES_DIR)

        // If the directory doesn't exist, create it.
        if (!dirHandle.exists()) {
            println("DialogueManager: Directory '$DIALOGUES_DIR' not found. Creating it.")
            dirHandle.mkdirs()
        }

        val dialogueFiles = dirHandle.list(".json")
        println("DialogueManager: Found ${dialogueFiles.size} dialogue file(s).")

        dialogueFiles.forEach { file ->
            try {
                if (file.readString().isNotBlank()) {
                    val dialogueFile = json.fromJson(DialogueFile::class.java, file)
                    loadedFiles[file.name()] = dialogueFile
                    println(" -> Loaded dialogue file: ${file.name()}")
                } else {
                    // Handle empty files by creating an empty object for them
                    loadedFiles[file.name()] = DialogueFile()
                    println(" -> Loaded empty dialogue file: ${file.name()}")
                }
            } catch (e: Exception) {
                println("ERROR: Failed to parse dialogue file ${file.name()}: ${e.message}")
            }
        }
    }

    // New function to save a file
    fun saveDialogueFile(fileName: String, dialogueFile: DialogueFile) {
        val fileHandle = Gdx.files.local("$DIALOGUES_DIR/$fileName")
        val jsonString = json.prettyPrint(dialogueFile)
        fileHandle.writeString(jsonString, false) // false = overwrite
        println("Saved dialogue file: $fileName")
    }

    // Helper functions for the editor and game
    fun getDialogueFileNames(): List<String> = loadedFiles.keys.toList()
    fun getDialogueFile(fileName: String): DialogueFile? = loadedFiles[fileName]

    fun createNewFile(fileName: String): Boolean {
        if (loadedFiles.containsKey(fileName)) {
            println("ERROR: A dialogue file with the name '$fileName' already exists.")
            return false
        }
        val newFile = DialogueFile()
        loadedFiles[fileName] = newFile
        saveDialogueFile(fileName, newFile) // Save the empty file immediately
        return true
    }

    fun createNewSequence(fileName: String, sequenceId: String): Boolean {
        val file = loadedFiles[fileName] ?: return false
        if (file.containsKey(sequenceId)) {
            println("ERROR: A sequence with the ID '$sequenceId' already exists in this file.")
            return false
        }
        file.put(sequenceId, DialogueSequenceData())
        return true
    }

    fun deleteSequence(fileName: String, sequenceId: String) {
        loadedFiles[fileName]?.remove(sequenceId)
    }

    fun getDialogue(dialogId: String): DialogSequence? {
        // Search through all loaded files to find the dialogue
        for (file in loadedFiles.values) {
            if (file.containsKey(dialogId)) {
                val sequenceData = file.get(dialogId)
                // Convert from data class to runtime class
                val lines = sequenceData.lines.map { DialogLine(it.speaker, it.text, it.speakerTexturePath) }
                return DialogSequence(lines)
            }
        }
        return null
    }

    fun getAllDialogueIds(): List<String> {
        return loadedFiles.values.flatMap { it.keys() }.toList()
    }
}

class DialogueLineData {
    var speaker: String = "Unknown"
    var text: String = "..."
    var speakerTexturePath: String? = null
}

class DialogueSequenceData {
    var lines: Array<DialogueLineData> = Array()
}

class DialogueFile : ObjectMap<String, DialogueSequenceData>()
