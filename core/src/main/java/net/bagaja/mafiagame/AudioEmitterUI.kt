package net.bagaja.mafiagame

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray

class AudioEmitterUI(
    private val skin: Skin,
    private val stage: Stage,
    private val soundManager: SoundManager,
    private val audioEmitterSystem: AudioEmitterSystem
) {
    private val window = Dialog("Audio Emitter Settings", skin, "dialog")
    private var currentEmitter: AudioEmitter? = null

    // --- UI Elements ---
    private val soundIdArea: TextArea
    private val volumeSlider: Slider
    private val rangeField: TextField
    private val playbackModeSelect: SelectBox<String>
    private val falloffModeSelect: SelectBox<String>
    private val playlistModeSelect: SelectBox<String>
    private val reactivationModeSelect: SelectBox<String>
    private val intervalField: TextField
    private val timedLoopDurationField: TextField
    private val minPitchField: TextField
    private val maxPitchField: TextField
    private val playbackLengthSelect: SelectBox<String>
    private val customLengthField: TextField
    private val parentIdField: TextField // NEW: Field for parent ID

    // Table wrappers for controlling visibility of entire rows
    private val intervalTable: Table
    private val playlistTable: Table
    private val reactivationTable: Table
    private val customLengthTable: Table
    private val playbackLengthTable: Table
    private val timedLoopTable: Table

    init {
        window.isMovable = true
        val content = window.contentTable
        content.pad(10f).defaults().pad(5f).align(Align.left)

        // --- Initialize Components ---
        soundIdArea = TextArea("", skin)
        val browseButton = TextButton("Browse...", skin)
        volumeSlider = Slider(0f, 1f, 0.01f, false, skin)
        rangeField = TextField("", skin)
        playbackModeSelect = SelectBox<String>(skin).apply { items = GdxArray(EmitterPlaybackMode.entries.map { it.name }.toTypedArray()) }
        playlistModeSelect = SelectBox<String>(skin).apply { items = GdxArray(EmitterPlaylistMode.entries.map { it.name }.toTypedArray()) }
        falloffModeSelect = SelectBox<String>(skin).apply { items = GdxArray(EmitterFalloffMode.entries.map { it.name }.toTypedArray()) }
        reactivationModeSelect = SelectBox<String>(skin).apply { items = GdxArray(EmitterReactivationMode.entries.map { it.name }.toTypedArray()) }
        intervalField = TextField("", skin)
        timedLoopDurationField = TextField("", skin)
        minPitchField = TextField("", skin)
        maxPitchField = TextField("", skin)
        playbackLengthSelect = SelectBox<String>(skin).apply { items = GdxArray(PlaybackLengthMode.entries.map { it.name }.toTypedArray()) }
        customLengthField = TextField("", skin)
        parentIdField = TextField("", skin).apply { messageText = "Optional NPC ID" } // NEW

        // --- Layout ---
        content.add(Label("Sound IDs (one per line):", skin)).colspan(2).left().row()
        content.add(ScrollPane(soundIdArea, skin)).growX().height(80f).colspan(2).row()
        content.add(browseButton).colspan(2).left().row()

        // NEW: Add parent ID field to the layout
        val parentTable = Table(); parentTable.add(Label("Parent NPC ID:", skin)).padRight(5f); parentTable.add(parentIdField).growX(); content.add(parentTable).colspan(2).left().row()

        val playbackModeTable = Table(); playbackModeTable.add(Label("Playback Mode:", skin)).padRight(5f); playbackModeTable.add(playbackModeSelect); content.add(playbackModeTable).colspan(2).left().row()
        playbackLengthTable = Table(); playbackLengthTable.add(Label("Playback Length:", skin)).padRight(5f); playbackLengthTable.add(playbackLengthSelect); content.add(playbackLengthTable).colspan(2).left().row()
        timedLoopTable = Table(); timedLoopTable.add(Label("Full Playback Length (s):", skin)).padRight(5f); timedLoopTable.add(timedLoopDurationField).width(100f); content.add(timedLoopTable).colspan(2).left().row()
        customLengthTable = Table(); customLengthTable.add(Label("Custom Length (s):", skin)).padRight(5f); customLengthTable.add(customLengthField).width(100f); content.add(customLengthTable).colspan(2).left().row()
        playlistTable = Table(); playlistTable.add(Label("Playlist Mode:", skin)).padRight(5f); playlistTable.add(playlistModeSelect); content.add(playlistTable).colspan(2).left().row()
        val falloffTable = Table(); falloffTable.add(Label("Volume Falloff:", skin)).padRight(5f); falloffTable.add(falloffModeSelect); content.add(falloffTable).colspan(2).left().row()
        reactivationTable = Table(); reactivationTable.add(Label("Reactivation:", skin)).padRight(5f); reactivationTable.add(reactivationModeSelect); content.add(reactivationTable).colspan(2).left().row()
        intervalTable = Table(); intervalTable.add(Label("Interval/Delay (s):", skin)).padRight(5f); intervalTable.add(intervalField).width(100f); content.add(intervalTable).colspan(2).left().row()
        val volumeTable = Table(); volumeTable.add(Label("Volume:", skin)).padRight(5f); volumeTable.add(volumeSlider).growX(); content.add(volumeTable).colspan(2).left().row()
        val rangeTable = Table(); rangeTable.add(Label("Range:", skin)).padRight(5f); rangeTable.add(rangeField).width(100f); content.add(rangeTable).colspan(2).left().row()
        val pitchTable = Table(); pitchTable.add(Label("Pitch (Min/Max):", skin)).padRight(5f); pitchTable.add(minPitchField).width(80f); pitchTable.add(maxPitchField).width(80f).padLeft(5f); content.add(pitchTable).colspan(2).left().row()

        // --- Buttons ---
        val saveButton = TextButton("Save", skin)
        val removeButton = TextButton("Remove", skin)
        window.button(saveButton)
        window.button(removeButton)
        window.button("Close")

        // --- Listeners ---
        saveButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { applyChanges(); window.hide() } })
        removeButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { currentEmitter?.let { audioEmitterSystem.removeEmitter(it) }; window.hide() } })
        browseButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { showSoundBrowser() } })
        playbackModeSelect.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updateFieldVisibility() } })
        playbackLengthSelect.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updateFieldVisibility() } })
        playlistModeSelect.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updateFieldVisibility() } })
    }

    private fun showSoundBrowser() {
        val dialog = Dialog("Select a Sound", skin, "dialog")
        val soundIds = soundManager.getAllSoundIds().sorted()
        val list = List<String>(skin)
        list.setItems(*soundIds.toTypedArray())

        val scroll = ScrollPane(list, skin); scroll.setFadeScrollBars(false)
        dialog.contentTable.add(scroll).width(400f).height(300f)

        dialog.button("Add to Playlist").addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val currentText = soundIdArea.text
                val selected = list.selected ?: return

                if (currentText.isBlank()) {
                    soundIdArea.text = selected
                } else {
                    soundIdArea.text = "$currentText\n$selected"
                }
            }
        })
        dialog.button("Close")
        dialog.show(stage)
    }

    fun show(emitter: AudioEmitter) {
        currentEmitter = emitter
        soundIdArea.text = emitter.soundIds.joinToString("\n")
        parentIdField.text = emitter.parentId ?: "" // NEW
        volumeSlider.value = emitter.volume
        rangeField.text = emitter.range.toString()
        playbackModeSelect.selected = emitter.playbackMode.name
        playbackLengthSelect.selected = emitter.playbackLengthMode.name
        customLengthField.text = emitter.customPlaybackLength.toString()
        playlistModeSelect.selected = emitter.playlistMode.name
        falloffModeSelect.selected = emitter.falloffMode.name
        reactivationModeSelect.selected = emitter.reactivationMode.name
        intervalField.text = emitter.interval.toString()
        timedLoopDurationField.text = emitter.timedLoopDuration.toString()
        minPitchField.text = emitter.minPitch.toString()
        maxPitchField.text = emitter.maxPitch.toString()

        updateFieldVisibility()
        window.show(stage)
    }

    private fun applyChanges() {
        currentEmitter?.let { emitter ->
            emitter.soundIds = soundIdArea.text.split("\n").map { s -> s.trim() }.filter { s -> s.isNotBlank() }.toMutableList()
            if (emitter.soundIds.isEmpty()) emitter.soundIds.add("FOOTSTEP_V1")

            emitter.volume = volumeSlider.value
            emitter.range = rangeField.text.toFloatOrNull() ?: 100f
            emitter.playbackMode = EmitterPlaybackMode.valueOf(playbackModeSelect.selected)
            emitter.playbackLengthMode = PlaybackLengthMode.valueOf(playbackLengthSelect.selected)
            emitter.customPlaybackLength = customLengthField.text.toFloatOrNull() ?: 2.0f
            emitter.playlistMode = EmitterPlaylistMode.valueOf(playlistModeSelect.selected)
            emitter.falloffMode = EmitterFalloffMode.valueOf(falloffModeSelect.selected)
            emitter.reactivationMode = EmitterReactivationMode.valueOf(reactivationModeSelect.selected)
            emitter.interval = intervalField.text.toFloatOrNull() ?: 1.0f
            emitter.timedLoopDuration = timedLoopDurationField.text.toFloatOrNull() ?: 30f
            emitter.minPitch = minPitchField.text.toFloatOrNull() ?: 1.0f
            emitter.maxPitch = maxPitchField.text.toFloatOrNull() ?: 1.0f

            // --- NEW: Linking Logic ---
            val newParentId = parentIdField.text.trim().ifBlank { null }
            emitter.parentId = newParentId

            if (newParentId != null) {
                val parentNPC = audioEmitterSystem.game.sceneManager.activeNPCs.find { it.id == newParentId }
                if (parentNPC != null) {
                    emitter.offsetFromParent = emitter.position.cpy().sub(parentNPC.position)
                    println("Linked emitter ${emitter.id} to NPC ${parentNPC.id}. Offset: ${emitter.offsetFromParent}")
                } else {
                    emitter.parentId = null
                    emitter.offsetFromParent = null
                    println("Warning: NPC with ID '$newParentId' not found. Link cleared.")
                }
            } else {
                emitter.offsetFromParent = null
            }
            // --- END NEW ---

            emitter.soundInstanceId?.let { id -> soundManager.stopLoopingSound(id) }
            emitter.soundInstanceId = null
            emitter.isDepleted = false
            emitter.currentPlaylistIndex = 0
            emitter.timer = 0f
        }
    }

    private fun updateFieldVisibility() {
        val playbackMode = EmitterPlaybackMode.valueOf(playbackModeSelect.selected)
        val playlistMode = EmitterPlaylistMode.valueOf(playlistModeSelect.selected)
        val lengthMode = PlaybackLengthMode.valueOf(playbackLengthSelect.selected)
        val isLoop = playbackMode != EmitterPlaybackMode.ONE_SHOT

        intervalTable.isVisible = true
        val intervalLabel = intervalTable.children.find { it is Label } as? Label
        intervalLabel?.setText(if (isLoop) "Delay (s):" else "Interval (s):")

        playlistTable.isVisible = isLoop
        reactivationTable.isVisible = isLoop && playlistMode == EmitterPlaylistMode.SEQUENTIAL

        playbackLengthTable.isVisible = playbackMode != EmitterPlaybackMode.LOOP_INFINITE
        customLengthTable.isVisible = lengthMode == PlaybackLengthMode.CUSTOM_DURATION && playbackMode != EmitterPlaybackMode.LOOP_INFINITE
        timedLoopTable.isVisible = lengthMode == PlaybackLengthMode.FULL_DURATION && playbackMode != EmitterPlaybackMode.LOOP_INFINITE

        window.pack()
    }
}
