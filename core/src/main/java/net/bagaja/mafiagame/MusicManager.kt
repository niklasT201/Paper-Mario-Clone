package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.utils.Disposable
import kotlin.random.Random

/**
 * Represents a source of music, which can be a pre-existing file or a procedurally generated piece.
 */
sealed class MusicSource {
    abstract val id: String
    data class File(override val id: String, val filePath: String) : MusicSource()
    data class Procedural(override val id: String, val generatorFactory: () -> ProceduralMusicGenerator) : MusicSource()
}

/**
 * Manages playback of both file-based and procedural background music.
 * Supports single track playback, random shuffling, and seamless transitions.
 */
class MusicManager : Disposable {
    private enum class PlaybackMode { NONE, SINGLE, RANDOM }
    private enum class PlaybackState { STOPPED, PLAYING, FADING_OUT }

    private val songs = mutableMapOf<String, MusicSource>()
    private var activeSong: ActiveSong? = null
    private var nextSong: ActiveSong? = null

    private var currentMode = PlaybackMode.NONE
    private var playbackState = PlaybackState.STOPPED
    private var randomPlaylist = mutableListOf<String>()

    private var volume = 0.5f
    private val fadeDuration = 1.5f
    private var fadeTimer = 0f
    private var masterVolume = 1.0f // NEW property
    private var musicVolume = 1.0f

    /**
     * Registers a new song with the manager so it can be played.
     * @param source The MusicSource to add (either File or Procedural).
     */
    fun registerSong(source: MusicSource) {
        if (songs.containsKey(source.id)) {
            println("MusicManager WARN: A song with ID '${source.id}' is already registered. Overwriting.")
        }
        songs[source.id] = source
        println("MusicManager: Registered song '${source.id}'.")
    }

    /**
     * Plays a specific song by its unique ID.
     * @param id The ID of the song to play.
     */
    fun playSong(id: String) {
        if (activeSong?.id == id && playbackState != PlaybackState.STOPPED) return // Already playing this song
        val source = songs[id]
        if (source == null) {
            println("MusicManager ERROR: Cannot play song. ID not found: '$id'")
            return
        }
        currentMode = PlaybackMode.SINGLE
        startNewSong(createActiveSong(source))
    }

    /**
     * Starts playing songs from the registered list in a random order.
     * When a song finishes, another will be chosen automatically.
     */
    fun playRandom() {
        if (songs.isEmpty()) {
            println("MusicManager ERROR: Cannot play random. No songs registered.")
            return
        }
        currentMode = PlaybackMode.RANDOM
        if (randomPlaylist.isEmpty()) {
            reshufflePlaylist()
        }
        val nextSongId = randomPlaylist.removeFirst()
        val source = songs[nextSongId]!! // Should not be null if playlist is correct
        startNewSong(createActiveSong(source))
    }

    /**
     * Stops the currently playing music with a fade-out.
     */
    fun stop() {
        currentMode = PlaybackMode.NONE
        if (playbackState == PlaybackState.PLAYING) {
            playbackState = PlaybackState.FADING_OUT
            fadeTimer = fadeDuration
        }
    }

    /**
     * Sets the master volume for the music.
     * @param newVolume A value between 0.0 (silent) and 1.0 (full volume).
     */
    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        applyVolume()
    }

    fun setMusicVolume(volume: Float) {
        musicVolume = volume.coerceIn(0f, 1f)
        applyVolume()
    }

    private fun applyVolume() {
        val finalVolume = masterVolume * musicVolume
        // Apply to the currently playing song
        activeSong?.setVolume(finalVolume)
    }

    /**
     * Must be called every frame in your game loop to handle transitions and random playback.
     */
    fun update(deltaTime: Float) {
        when (playbackState) {
            PlaybackState.FADING_OUT -> {
                fadeTimer -= deltaTime
                val fadeProgress = (fadeTimer / fadeDuration).coerceIn(0f, 1f)
                // MODIFIED LINE: Use the calculated final volume
                activeSong?.setVolume(masterVolume * musicVolume * fadeProgress)

                if (fadeTimer <= 0f) {
                    activeSong?.dispose()
                    activeSong = null
                    playbackState = PlaybackState.STOPPED

                    if (nextSong != null) {
                        activeSong = nextSong
                        nextSong = null
                        applyVolume() // MODIFIED: Use applyVolume()
                        activeSong?.play()
                        playbackState = PlaybackState.PLAYING
                    }
                }
            }
            PlaybackState.PLAYING -> {
                activeSong?.update(deltaTime)
                if (activeSong?.isPlaying() == false) {
                    // Current song finished, what's next?
                    if (currentMode == PlaybackMode.RANDOM) {
                        playRandom()
                    } else {
                        stop()
                    }
                }
            }
            PlaybackState.STOPPED -> {
                // Do nothing
            }
        }
    }

    override fun dispose() {
        println("Disposing MusicManager...")
        activeSong?.dispose()
        nextSong?.dispose()
        songs.clear()
    }

    private fun startNewSong(newSong: ActiveSong) {
        if (playbackState == PlaybackState.PLAYING && activeSong != null) {
            // A song is already playing, so crossfade
            nextSong = newSong
            playbackState = PlaybackState.FADING_OUT
            fadeTimer = fadeDuration
        } else {
            // No song is playing, just start the new one
            activeSong?.dispose()
            activeSong = newSong
            applyVolume()
            activeSong?.play()
            playbackState = PlaybackState.PLAYING
        }
    }

    private fun reshufflePlaylist() {
        randomPlaylist = songs.keys.toMutableList()
        randomPlaylist.shuffle()
    }

    private fun createActiveSong(source: MusicSource): ActiveSong {
        return when (source) {
            is MusicSource.File -> {
                val music = Gdx.audio.newMusic(Gdx.files.internal(source.filePath))
                music.isLooping = false // We handle looping via the update() method
                ActiveSong.FileSong(source.id, music)
            }
            is MusicSource.Procedural -> {
                val generator = source.generatorFactory()
                ActiveSong.ProceduralSong(source.id, generator)
            }
        }
    }

    /**
     * An internal wrapper for a currently playing song, abstracting away the source.
     */
    private sealed class ActiveSong(val id: String) : Disposable {
        abstract fun play()
        abstract fun isPlaying(): Boolean
        abstract fun setVolume(volume: Float)
        abstract fun update(deltaTime: Float)

        class FileSong(id: String, private val music: Music) : ActiveSong(id) {
            override fun play() { music.play() }
            override fun isPlaying(): Boolean = music.isPlaying
            override fun setVolume(volume: Float) { music.volume = volume }
            override fun update(deltaTime: Float) { /* LibGDX Music handles its own updates */ }
            override fun dispose() { music.dispose() }
        }

        class ProceduralSong(id: String, private val generator: ProceduralMusicGenerator) : ActiveSong(id) {
            override fun play() { generator.start() }
            override fun isPlaying(): Boolean = generator.isPlaying()
            override fun setVolume(volume: Float) { generator.setVolume(volume) }
            override fun update(deltaTime: Float) { /* Generator thread handles its own updates */ }
            override fun dispose() { generator.dispose() }
        }
    }
}
