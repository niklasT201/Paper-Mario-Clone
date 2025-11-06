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
    private val playlistModeSelect: SelectBox<String>
    private val reactivationModeSelect: SelectBox<String>
    private val intervalField: TextField
    private val timedLoopDurationField: TextField
    private val minPitchField: TextField
    private val maxPitchField: TextField

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
        reactivationModeSelect = SelectBox<String>(skin).apply { items = GdxArray(EmitterReactivationMode.entries.map { it.name }.toTypedArray()) }
        intervalField = TextField("", skin)
        timedLoopDurationField = TextField("", skin)
        minPitchField = TextField("", skin)
        maxPitchField = TextField("", skin)

        content.add(Label("Sound IDs (one per line):", skin)).colspan(2).left().row()
        content.add(ScrollPane(soundIdArea, skin)).growX().height(80f).colspan(2).row()
        content.add(browseButton).colspan(2).left().row()

        // *** FIX: Wrap each row in its own container Table ***
        val playbackTable = Table(); playbackTable.add(Label("Playback Mode:", skin)); playbackTable.add(playbackModeSelect); content.add(playbackTable).colspan(2).left().row()
        val playlistTable = Table(); playlistTable.add(Label("Playlist Mode:", skin)); playlistTable.add(playlistModeSelect); content.add(playlistTable).colspan(2).left().row()
        val reactivationTable = Table(); reactivationTable.add(Label("Reactivation:", skin)); reactivationTable.add(reactivationModeSelect); content.add(reactivationTable).colspan(2).left().row()
        val intervalTable = Table(); intervalTable.add(Label("Interval/Delay (s):", skin)); intervalTable.add(intervalField).width(100f); content.add(intervalTable).colspan(2).left().row()
        val timedLoopTable = Table(); timedLoopTable.add(Label("Timed Loop Duration (s):", skin)); timedLoopTable.add(timedLoopDurationField).width(100f); content.add(timedLoopTable).colspan(2).left().row()
        val volumeTable = Table(); volumeTable.add(Label("Volume:", skin)); volumeTable.add(volumeSlider).growX(); content.add(volumeTable).colspan(2).left().row()
        val rangeTable = Table(); rangeTable.add(Label("Range:", skin)); rangeTable.add(rangeField).width(100f); content.add(rangeTable).colspan(2).left().row()
        val pitchTable = Table(); pitchTable.add(Label("Pitch (Min/Max):", skin)); pitchTable.add(minPitchField).width(80f).padLeft(10f); pitchTable.add(maxPitchField).width(80f).padLeft(5f); content.add(pitchTable).colspan(2).left().row()

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
            it.reactivationMode = EmitterReactivationMode.valueOf(reactivationModeSelect.selected)
            it.interval = intervalField.text.toFloatOrNull() ?: 1.0f
            it.timedLoopDuration = timedLoopDurationField.text.toFloatOrNull() ?: 30f
            it.minPitch = minPitchField.text.toFloatOrNull() ?: 1.0f
            it.maxPitch = maxPitchField.text.toFloatOrNull() ?: 1.0f

            it.soundInstanceId?.let { id -> soundManager.stopLoopingSound(id) }
            it.soundInstanceId = null
            it.isDepleted = false
            it.currentPlaylistIndex = 0
            it.timer = it.interval
        }
    }

    private fun updateFieldVisibility() {
        val playbackMode = EmitterPlaybackMode.valueOf(playbackModeSelect.selected)
        val playlistMode = EmitterPlaylistMode.valueOf(playlistModeSelect.selected)

        val isLoop = playbackMode == EmitterPlaybackMode.LOOP_INFINITE || playbackMode == EmitterPlaybackMode.LOOP_TIMED

        // *** FIX: Target the correct parent (the wrapper Table) ***
        intervalField.parent.isVisible = !isLoop || (playlistMode == EmitterPlaylistMode.SEQUENTIAL)

        val intervalLabel = (intervalField.parent as Table).children.find { it is Label } as? Label
        intervalLabel?.setText(if(isLoop) "Delay (s):" else "Interval (s):")

        timedLoopDurationField.parent.isVisible = playbackMode == EmitterPlaybackMode.LOOP_TIMED
        playlistModeSelect.parent.isVisible = isLoop
        reactivationModeSelect.parent.isVisible = isLoop && playlistMode == EmitterPlaylistMode.SEQUENTIAL

        window.pack()
    }
}
