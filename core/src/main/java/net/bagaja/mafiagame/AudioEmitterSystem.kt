package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Timer
import java.util.*
import kotlin.math.floor
import kotlin.random.Random

// --- NEW ENUMS FOR PLAYLIST BEHAVIOR ---
enum class EmitterPlaylistMode {
    SEQUENTIAL, // Plays sounds in the order they are listed
    RANDOM      // Plays a random sound from the list
}

enum class EmitterReactivationMode {
    AUTO_RESET, // Restarts the playlist automatically after a delay
    RE_ENTER    // Player must leave and re-enter the range to restart
}

enum class EmitterPlaybackMode {
    ONE_SHOT,
    LOOP_INFINITE,
    LOOP_TIMED
}

data class AudioEmitter(
    val id: String = UUID.randomUUID().toString(),
    var position: Vector3,
    val debugInstance: ModelInstance,

    // --- MODIFIED & NEW PROPERTIES ---
    var soundIds: MutableList<String> = mutableListOf("FOOTSTEP_V1"), // Now a list
    var volume: Float = 1.0f,
    var range: Float = 100f,
    var playbackMode: EmitterPlaybackMode = EmitterPlaybackMode.LOOP_INFINITE,
    var playlistMode: EmitterPlaylistMode = EmitterPlaylistMode.SEQUENTIAL, // NEW
    var reactivationMode: EmitterReactivationMode = EmitterReactivationMode.AUTO_RESET, // NEW
    var interval: Float = 1.0f, // Time between sounds in playlist or one-shots
    var timedLoopDuration: Float = 30f,
    var minPitch: Float = 1.0f,
    var maxPitch: Float = 1.0f,
    var falloffMode: EmitterFalloffMode = EmitterFalloffMode.LINEAR,

    // --- STATE ---
    @Transient var soundInstanceId: Long? = null,
    @Transient var timer: Float = interval,
    @Transient var currentPlaylistIndex: Int = 0, // NEW
    @Transient var isDepleted: Boolean = false,   // NEW: For RE_ENTER logic
    @Transient var wasInRange: Boolean = false,     // NEW: For RE_ENTER logic
    var sceneId: String = "WORLD",
    var missionId: String? = null
)

enum class EmitterFalloffMode {
    LINEAR,  // Sound fades with distance (current behavior)
    NONE     // Sound is at constant volume within its range
}

class AudioEmitterSystem : Disposable {
    lateinit var game: MafiaGame
    private val modelBuilder = ModelBuilder()
    private val modelBatch = ModelBatch()
    private val debugModel: Model
    val activeEmitters = Array<AudioEmitter>()
    var isVisible = false
    var isGloballyDisabled: Boolean = false
    private val mutedSoundIds = mutableSetOf<String>()

    init {
        val debugMaterial = Material(ColorAttribute.createDiffuse(Color.CYAN))
        debugModel = modelBuilder.createBox(1.5f, 1.5f, 0.2f, debugMaterial, (VertexAttributes.Usage.Position).toLong())
    }

    fun toggleVisibility() {
        isVisible = !isVisible
        val status = if (isVisible) "ON" else "OFF"
        game.uiManager.updatePlacementInfo("Audio Emitter Visibility: $status")
    }

    fun addEmitterFromData(data: AudioEmitterData): AudioEmitter {
        val emitter = AudioEmitter(
            id = data.id,
            position = data.position,
            debugInstance = ModelInstance(debugModel),
            soundIds = data.soundIds,
            volume = data.volume,
            range = data.range,
            playbackMode = data.playbackMode,
            playlistMode = data.playlistMode,
            reactivationMode = data.reactivationMode,
            interval = data.interval,
            timedLoopDuration = data.timedLoopDuration,
            minPitch = data.minPitch,
            maxPitch = data.maxPitch,
            falloffMode = data.falloffMode,
            sceneId = data.sceneId,
            missionId = data.missionId
        )
        activeEmitters.add(emitter)
        return emitter
    }

    fun handlePlaceAction(ray: Ray) {
        val groundPlane = Plane(Vector3.Y, 0f)
        val intersection = Vector3()
        if (Intersector.intersectRayPlane(ray, groundPlane, intersection)) {
            val gridX = floor(intersection.x / game.blockSize) * game.blockSize + game.blockSize / 2
            val surfaceY = game.sceneManager.findHighestSupportY(gridX, intersection.z, 1000f, 0.1f, game.blockSize)
            val gridZ = floor(intersection.z / game.blockSize) * game.blockSize + game.blockSize / 2

            val emitter = AudioEmitter(
                position = Vector3(gridX, surfaceY + 1f, gridZ),
                debugInstance = ModelInstance(debugModel),
                sceneId = game.sceneManager.getCurrentSceneId()
            )
            activeEmitters.add(emitter)
            game.uiManager.showAudioEmitterUI(emitter) // Immediately open UI for configuration
        }
    }

    fun removeEmitter(emitter: AudioEmitter) {
        emitter.soundInstanceId?.let { game.soundManager.stopLoopingSound(it) }
        activeEmitters.removeValue(emitter, true)
    }

    fun setMutedSounds(soundIds: List<String>) {
        mutedSoundIds.clear()
        mutedSoundIds.addAll(soundIds)
    }

    fun clearMutedSounds() {
        mutedSoundIds.clear()
    }

    fun update(deltaTime: Float) {
        // --- ADD THIS GLOBAL CHECK AT THE TOP ---
        if (isGloballyDisabled) {
            // If the system is disabled, make sure no emitters are playing.
            for (emitter in activeEmitters) {
                if (emitter.soundInstanceId != null) {
                    game.soundManager.stopLoopingSound(emitter.soundInstanceId!!)
                    emitter.soundInstanceId = null
                }
            }
            return // Stop all further processing for this frame
        }

        val playerPos = game.playerSystem.getControlledEntityPosition()
        val currentSceneId = game.sceneManager.getCurrentSceneId()

        for (emitter in activeEmitters) {
            // If emitter is in wrong scene and playing, stop it.
            val isMuted = emitter.soundIds.any { it in mutedSoundIds }
            if (isMuted) {
                if (emitter.soundInstanceId != null) {
                    game.soundManager.stopLoopingSound(emitter.soundInstanceId!!)
                    emitter.soundInstanceId = null
                }
                continue
            }

            val distance = playerPos.dst(emitter.position)
            val isInRange = distance < emitter.range

            // --- RE-ENTER LOGIC ---
            if (!isInRange && emitter.wasInRange && emitter.reactivationMode == EmitterReactivationMode.RE_ENTER) {
                emitter.isDepleted = false // Player has left, so the emitter can be reset
                emitter.currentPlaylistIndex = 0 // Reset playlist progress
            }
            emitter.wasInRange = isInRange

            // If depleted or out of range, stop sounds and skip
            if (emitter.isDepleted || !isInRange) {
                if (emitter.soundInstanceId != null) {
                    game.soundManager.stopLoopingSound(emitter.soundInstanceId!!)
                    emitter.soundInstanceId = null
                }
                continue
            }

            // --- PLAYBACK LOGIC ---
            emitter.timer -= deltaTime
            if (emitter.timer <= 0f) {
                playNextSoundInPlaylist(emitter, playerPos)
                emitter.timer = emitter.interval
            }
        }
    }

    private fun playNextSoundInPlaylist(emitter: AudioEmitter, playerPos: Vector3) {
        if (emitter.soundIds.isEmpty()) return

        // Stop any currently playing sound from this emitter (for sequential one-shots)
        emitter.soundInstanceId?.let { game.soundManager.stopLoopingSound(it) }
        emitter.soundInstanceId = null

        // Determine which sound to play
        val soundIdToPlay: String
        if (emitter.playlistMode == EmitterPlaylistMode.RANDOM) {
            soundIdToPlay = emitter.soundIds.random()
        } else { // SEQUENTIAL
            soundIdToPlay = emitter.soundIds[emitter.currentPlaylistIndex]
            emitter.currentPlaylistIndex++
        }

        val pitch = Random.nextFloat() * (emitter.maxPitch - emitter.minPitch) + emitter.minPitch
        val volume = emitter.volume * (1.0f - (Random.nextFloat() * 0.4f))

        val instanceId = when (emitter.playbackMode) {
            EmitterPlaybackMode.ONE_SHOT -> game.soundManager.playSound(
                id = soundIdToPlay, position = emitter.position, pitch = pitch, maxRange = emitter.range,
                volumeMultiplier = volume, falloffMode = emitter.falloffMode
            )
            EmitterPlaybackMode.LOOP_INFINITE, EmitterPlaybackMode.LOOP_TIMED -> game.soundManager.playSound(
                id = soundIdToPlay, position = emitter.position, loop = true, pitch = pitch, maxRange = emitter.range,
                volumeMultiplier = volume, falloffMode = emitter.falloffMode
            )
        }

        emitter.soundInstanceId = instanceId

        // Handle timed loops
        if (emitter.playbackMode == EmitterPlaybackMode.LOOP_TIMED && instanceId != null) {
            Timer.schedule(object : Timer.Task() {
                override fun run() {
                    game.soundManager.stopLoopingSound(instanceId)
                    if (emitter.soundInstanceId == instanceId) emitter.soundInstanceId = null
                }
            }, emitter.timedLoopDuration)
        }

        // --- PLAYLIST END LOGIC ---
        if (emitter.playlistMode == EmitterPlaylistMode.SEQUENTIAL && emitter.currentPlaylistIndex >= emitter.soundIds.size) {
            if (emitter.reactivationMode == EmitterReactivationMode.AUTO_RESET) {
                emitter.currentPlaylistIndex = 0 // Loop back to the start
            } else { // RE_ENTER
                emitter.isDepleted = true // Mark as finished until player leaves the area
            }
        }
    }


    fun render(camera: Camera) {
        if (!game.isEditorMode || !isVisible) return
        val currentSceneId = game.sceneManager.getCurrentSceneId()

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        modelBatch.begin(camera)
        for (emitter in activeEmitters) {
            if (emitter.sceneId == currentSceneId) {
                emitter.debugInstance.transform.setToTranslation(emitter.position)
                modelBatch.render(emitter.debugInstance)
            }
        }
        modelBatch.end()
    }

    override fun dispose() {
        modelBatch.dispose()
        debugModel.dispose()
    }
}
