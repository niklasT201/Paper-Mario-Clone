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

enum class PlaybackLengthMode {
    FULL_DURATION,   // Plays the entire sound file once
    CUSTOM_DURATION  // Plays for a specified duration
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

    // --- NEW PROPERTIES FOR PLAYBACK LENGTH ---
    var playbackLengthMode: PlaybackLengthMode = PlaybackLengthMode.FULL_DURATION,
    var customPlaybackLength: Float = 2.0f, // Default custom length of 2 seconds

    var playlistMode: EmitterPlaylistMode = EmitterPlaylistMode.SEQUENTIAL, // NEW
    var reactivationMode: EmitterReactivationMode = EmitterReactivationMode.AUTO_RESET, // NEW
    var interval: Float = 1.0f, // Time between sounds in playlist or one-shots
    var timedLoopDuration: Float = 30f,
    var minPitch: Float = 1.0f,
    var maxPitch: Float = 1.0f,
    var falloffMode: EmitterFalloffMode = EmitterFalloffMode.LINEAR,

    var parentId: String? = null,
    @Transient var offsetFromParent: Vector3? = null,

    // --- STATE ---
    @Transient var soundInstanceId: Long? = null,
    @Transient var timer: Float = 0f, // --- FIX: Timer now consistently means "time until next event"
    @Transient var currentPlaylistIndex: Int = 0, // NEW
    @Transient var isDepleted: Boolean = false,   // NEW: For RE_ENTER logic
    @Transient var wasInRange: Boolean = false,     // NEW: For RE_ENTER logic
    var sceneId: String = "WORLD",
    var missionId: String? = null
) : ISoundEmitter {

    override fun stopLoopingSound(soundManager: SoundManager) {
        soundInstanceId?.let {
            soundManager.stopLoopingSound(it)
            soundInstanceId = null
        }
    }

    override fun restartLoopingSound(soundManager: SoundManager) {
        if (this.playbackMode == EmitterPlaybackMode.LOOP_INFINITE && this.soundInstanceId == null) {
            val soundIdToPlay = if (this.playlistMode == EmitterPlaylistMode.RANDOM) {
                this.soundIds.randomOrNull()
            } else {
                this.soundIds.getOrNull(this.currentPlaylistIndex)
            }

            if (soundIdToPlay != null) {
                val pitch = Random.nextFloat() * (this.maxPitch - this.minPitch) + this.minPitch
                this.soundInstanceId = soundManager.playSound(
                    id = soundIdToPlay,
                    position = this.position,
                    loop = true,
                    pitch = pitch,
                    maxRange = this.range,
                    volumeMultiplier = this.volume,
                    falloffMode = this.falloffMode
                )
            }
        }
    }
}

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
        emitter.timer = emitter.interval
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
            // --- FIX: Initialize timer correctly on creation ---
            emitter.timer = emitter.interval
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

    // --- FIX: Completely rewritten update logic for correctness ---
    fun update(deltaTime: Float) {
        if (isGloballyDisabled) {
            for (emitter in activeEmitters) {
                if (emitter.soundInstanceId != null) {
                    game.soundManager.stopLoopingSound(emitter.soundInstanceId!!)
                    emitter.soundInstanceId = null
                }
            }
            return
        }

        val playerPos = game.playerSystem.getControlledEntityPosition()
        val currentSceneId = game.sceneManager.getCurrentSceneId()

        for (emitter in activeEmitters) {
            // --- ROTATION FIX: UPDATE POSITION FROM PARENT ---
            if (emitter.parentId != null && emitter.offsetFromParent != null) {
                val parentNPC = game.sceneManager.activeNPCs.find { it.id == emitter.parentId }
                if (parentNPC != null) {
                    // 1. Create a transform matrix from the NPC's current rotation
                    val transform = com.badlogic.gdx.math.Matrix4().setToRotation(Vector3.Y, parentNPC.facingRotationY)

                    // 2. Rotate the stored offset vector by the NPC's rotation
                    val rotatedOffset = emitter.offsetFromParent!!.cpy().mul(transform)

                    // 3. Add the rotated offset to the NPC's current position
                    emitter.position.set(parentNPC.position).add(rotatedOffset)
                } else {
                    // Parent is gone, break the link
                    emitter.parentId = null
                    emitter.offsetFromParent = null
                }
            }

            if (emitter.sceneId != currentSceneId || emitter.soundIds.any { it in mutedSoundIds }) {
                if (emitter.soundInstanceId != null) {
                    game.soundManager.stopLoopingSound(emitter.soundInstanceId!!)
                    emitter.soundInstanceId = null
                }
                continue
            }

            val distance = playerPos.dst(emitter.position)
            val isInRange = distance < emitter.range

            // --- RE-ENTER LOGIC ---
            if (!isInRange && emitter.wasInRange) {
                if (emitter.reactivationMode == EmitterReactivationMode.RE_ENTER) {
                    emitter.isDepleted = false
                    emitter.currentPlaylistIndex = 0
                }
                if (emitter.soundInstanceId != null) {
                    game.soundManager.stopLoopingSound(emitter.soundInstanceId!!)
                    emitter.soundInstanceId = null
                }
            }
            emitter.wasInRange = isInRange

            if (emitter.isDepleted || !isInRange) {
                continue
            }

            if (emitter.soundInstanceId == null) {
                emitter.timer -= deltaTime
                if (emitter.timer <= 0f) {
                    playNextSoundInPlaylist(emitter, playerPos)
                }
            }
        }
    }

    private fun playNextSoundInPlaylist(emitter: AudioEmitter, playerPos: Vector3) {
        if (emitter.soundIds.isEmpty() || emitter.isDepleted) return

        // Stop any sound that might be lingering from a previous state change
        emitter.soundInstanceId?.let {
            game.soundManager.stopLoopingSound(it)
            game.soundManager.stopOneShotSound(it)
        }
        emitter.soundInstanceId = null

        // Determine which sound to play
        val soundIdToPlay = if (emitter.playlistMode == EmitterPlaylistMode.RANDOM) {
            emitter.soundIds.random()
        } else { // SEQUENTIAL
            // Ensure the index is valid before trying to access it
            if (emitter.currentPlaylistIndex >= emitter.soundIds.size) {
                emitter.currentPlaylistIndex = 0 // Safety wrap-around
            }
            emitter.soundIds[emitter.currentPlaylistIndex]
        }

        val pitch = Random.nextFloat() * (emitter.maxPitch - emitter.minPitch) + emitter.minPitch
        val volume = emitter.volume

        if (emitter.playbackMode == EmitterPlaybackMode.LOOP_INFINITE) {
            // This mode is for single, infinitely looping sounds and does not advance playlists.
            val instanceId = game.soundManager.playSound(
                id = soundIdToPlay, position = emitter.position, loop = true, pitch = pitch, maxRange = emitter.range,
                volumeMultiplier = volume, falloffMode = emitter.falloffMode
            )
            emitter.soundInstanceId = instanceId
        } else {
            // --- CORRECTED LOGIC: ONE_SHOT and LOOP_TIMED are now handled identically ---
            // They both play a sound for a specific duration, then pause for the interval.
            val instanceId = game.soundManager.playSound(
                id = soundIdToPlay, position = emitter.position, loop = false, pitch = pitch, maxRange = emitter.range,
                volumeMultiplier = volume, falloffMode = emitter.falloffMode
            )

            if (instanceId == null) {
                // If the sound failed to play (e.g., not loaded), reset the interval to try again.
                emitter.timer = emitter.interval
                return
            }

            emitter.soundInstanceId = instanceId

            // Determine how long this sound should play for.
            val playDuration = when (emitter.playbackLengthMode) {
                PlaybackLengthMode.CUSTOM_DURATION -> emitter.customPlaybackLength
                // Use the repurposed timedLoopDuration field as the 'full' length
                PlaybackLengthMode.FULL_DURATION -> emitter.timedLoopDuration
            }

            // Schedule a task to run *after* the sound has finished playing.
            Timer.schedule(object : Timer.Task() {
                override fun run() {
                    // Stop the sound that was just playing.
                    game.soundManager.stopOneShotSound(instanceId)

                    // Ensure we are not overwriting a newer sound instance
                    if (emitter.soundInstanceId == instanceId) {
                        emitter.soundInstanceId = null
                        // Now that the sound is finished, start the timer for the pause/interval.
                        emitter.timer = emitter.interval

                        // --- THE FIX IS HERE ---
                        // Advance the playlist index *AFTER* the sound has finished playing.
                        if (emitter.playlistMode == EmitterPlaylistMode.SEQUENTIAL) {
                            emitter.currentPlaylistIndex++
                            if (emitter.currentPlaylistIndex >= emitter.soundIds.size) {
                                if (emitter.reactivationMode == EmitterReactivationMode.AUTO_RESET) {
                                    emitter.currentPlaylistIndex = 0 // Loop back to the start
                                } else { // RE_ENTER
                                    emitter.isDepleted = true // Mark as finished until player leaves
                                }
                            }
                        }
                    }
                }
            }, playDuration)
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
