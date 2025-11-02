package net.bagaja.mafiagame

object AssetLoader {

    fun loadGameSounds(soundManager: SoundManager) {
        println("--- Loading All Game Sounds ---")

        // Load Revolver sounds (3 variations, with a procedural fallback)
        soundManager.loadWeaponSound("GUNSHOT_REVOLVER", 3, SoundManager.Effect.GUNSHOT_REVOLVER)

        // Load Tommy Gun sounds (2 variations, with the same fallback)
        soundManager.loadWeaponSound("GUNSHOT_TOMMYGUN", 2, SoundManager.Effect.GUNSHOT_REVOLVER)

        // Load Shotgun sounds (2 variations, with the same fallback)
        soundManager.loadWeaponSound("GUNSHOT_SHOTGUN", 2, SoundManager.Effect.GUNSHOT_REVOLVER)

        // You can also load procedural-only sounds like before for other effects
        soundManager.load(SoundManager.Effect.FIRE_LOOP)

        println("--- Finished Loading Sounds ---")
    }
}
