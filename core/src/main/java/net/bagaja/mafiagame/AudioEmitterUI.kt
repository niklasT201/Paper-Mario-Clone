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

    // --- FIX: References to the parent tables for visibility toggling ---
    private val intervalTable: Table
    private val timedLoopTable: Table
    private val playlistTable: Table
    private val reactivationTable: Table

    init {
        window.isMovable = true
        val content = window.contentTable
        content.pad(10f).defaults().pad(5f).align(Align.left)

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

        content.add(Label("Sound IDs (one per line):", skin)).colspan(2).left().row()
        content.add(ScrollPane(soundIdArea, skin)).growX().height(80f).colspan(2).row()
        content.add(browseButton).colspan(2).left().row()

        // --- FIX: Wrap each row in its own container Table for proper layout and visibility control ---
        val playbackTable = Table(); playbackTable.add(Label("Playback Mode:", skin)).padRight(5f); playbackTable.add(playbackModeSelect); content.add(playbackTable).colspan(2).left().row()
        playlistTable = Table(); playlistTable.add(Label("Playlist Mode:", skin)).padRight(5f); playlistTable.add(playlistModeSelect); content.add(playlistTable).colspan(2).left().row()
        val falloffTable = Table(); falloffTable.add(Label("Volume Falloff:", skin)).padRight(5f); falloffTable.add(falloffModeSelect); content.add(falloffTable).colspan(2).left().row()
        reactivationTable = Table(); reactivationTable.add(Label("Reactivation:", skin)).padRight(5f); reactivationTable.add(reactivationModeSelect); content.add(reactivationTable).colspan(2).left().row()
        intervalTable = Table(); intervalTable.add(Label("Interval/Delay (s):", skin)).padRight(5f); intervalTable.add(intervalField).width(100f); content.add(intervalTable).colspan(2).left().row()
        timedLoopTable = Table(); timedLoopTable.add(Label("Timed Loop Duration (s):", skin)).padRight(5f); timedLoopTable.add(timedLoopDurationField).width(100f); content.add(timedLoopTable).colspan(2).left().row()
        val volumeTable = Table(); volumeTable.add(Label("Volume:", skin)).padRight(5f); volumeTable.add(volumeSlider).growX(); content.add(volumeTable).colspan(2).left().row()
        val rangeTable = Table(); rangeTable.add(Label("Range:", skin)).padRight(5f); rangeTable.add(rangeField).width(100f); content.add(rangeTable).colspan(2).left().row()
        val pitchTable = Table(); pitchTable.add(Label("Pitch (Min/Max):", skin)).padRight(5f); pitchTable.add(minPitchField).width(80f); pitchTable.add(maxPitchField).width(80f).padLeft(5f); content.add(pitchTable).colspan(2).left().row()

        val saveButton = TextButton("Save", skin)
        val removeButton = TextButton("Remove", skin)
        window.button(saveButton)
        window.button(removeButton)
        window.button("Close")

        saveButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { applyChanges(); window.hide() } })
        removeButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { currentEmitter?.let { audioEmitterSystem.removeEmitter(it) }; window.hide() } })
        browseButton.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { showSoundBrowser() } })
        playbackModeSelect.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updateFieldVisibility() } })
        playlistModeSelect.addListener(object : ChangeListener() { override fun changed(event: ChangeEvent?, actor: Actor?) { updateFieldVisibility() } })
    }

    private fun showSoundBrowser() {
        val dialog = Dialog("Select a Sound", skin, "dialog")
        // --- FIX: This now correctly gets ALL loaded sounds ---
        val soundIds = (soundManager.getAllSoundIds()).sorted()
        val list = List<String>(skin); list.setItems(*soundIds.toTypedArray())
        val scroll = ScrollPane(list, skin); scroll.setFadeScrollBars(false)
        dialog.contentTable.add(scroll).width(400f).height(300f)
        dialog.button("Add to Playlist").addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val currentText = soundIdArea.text
                val selected = list.selected
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
        volumeSlider.value = emitter.volume
        rangeField.text = emitter.range.toString()
        playbackModeSelect.selected = emitter.playbackMode.name
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
        currentEmitter?.let {
            it.soundIds = soundIdArea.text.split("\n").map { s -> s.trim() }.filter { s -> s.isNotBlank() }.toMutableList()
            if (it.soundIds.isEmpty()) it.soundIds.add("FOOTSTEP_V1")

            it.volume = volumeSlider.value
            it.range = rangeField.text.toFloatOrNull() ?: 100f
            it.playbackMode = EmitterPlaybackMode.valueOf(playbackModeSelect.selected)
            it.playlistMode = EmitterPlaylistMode.valueOf(playlistModeSelect.selected)
            it.falloffMode = EmitterFalloffMode.valueOf(falloffModeSelect.selected)
            it.reactivationMode = EmitterReactivationMode.valueOf(reactivationModeSelect.selected)
            it.interval = intervalField.text.toFloatOrNull() ?: 1.0f
            it.timedLoopDuration = timedLoopDurationField.text.toFloatOrNull() ?: 30f
            it.minPitch = minPitchField.text.toFloatOrNull() ?: 1.0f
            it.maxPitch = maxPitchField.text.toFloatOrNull() ?: 1.0f

            // --- FIX: Reset the emitter's state completely to apply new settings ---
            it.soundInstanceId?.let { id -> soundManager.stopLoopingSound(id) }
            it.soundInstanceId = null
            it.isDepleted = false
            it.currentPlaylistIndex = 0
            it.timer = 0f // Reset timer to 0 so it plays immediately if in range
        }
    }

    private fun updateFieldVisibility() {
        val playbackMode = EmitterPlaybackMode.valueOf(playbackModeSelect.selected)
        val playlistMode = EmitterPlaylistMode.valueOf(playlistModeSelect.selected)

        val isLoop = playbackMode == EmitterPlaybackMode.LOOP_INFINITE || playbackMode == EmitterPlaybackMode.LOOP_TIMED

        // --- FIX: Target the correct parent (the wrapper Table) and update label ---
        intervalTable.isVisible = true // The interval/delay field is now always relevant
        val intervalLabel = intervalTable.children.find { it is Label } as? Label
        intervalLabel?.setText(if(isLoop) "Delay (s):" else "Interval (s):")

        timedLoopTable.isVisible = playbackMode == EmitterPlaybackMode.LOOP_TIMED
        playlistTable.isVisible = isLoop
        reactivationTable.isVisible = isLoop && playlistMode == EmitterPlaylistMode.SEQUENTIAL && playbackMode == EmitterPlaybackMode.LOOP_TIMED

        window.pack()
    }
}
