package net.bagaja.mafiagame

/**
 * An interface for any game object that can produce a persistent, looping sound
 * that needs to be managed during scene transitions or cleanup.
 */
interface ISoundEmitter {
    /**
     * Instructs the object to find its active looping sound instance ID,
     * tell the SoundManager to stop it, and clear its internal reference.
     * @param soundManager The SoundManager instance to use for stopping the sound.
     */
    fun stopLoopingSound(soundManager: SoundManager)

    /**
     * --- NEW METHOD ---
     * Checks if the object should be making a looping sound and, if so,
     * starts the sound and stores the new instance ID. This is used
     * when loading a scene.
     * @param soundManager The SoundManager instance to use for starting the sound.
     */
    fun restartLoopingSound(soundManager: SoundManager)
}
