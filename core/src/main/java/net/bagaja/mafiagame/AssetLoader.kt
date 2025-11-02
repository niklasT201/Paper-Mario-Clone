package net.bagaja.mafiagame

object AssetLoader {

    fun loadGameSounds(soundManager: SoundManager) {
        println("--- Loading All Game Sounds ---")

        // Load WEAPON sounds (file-based with procedural fallbacks)
        soundManager.loadWeaponSound("GUNSHOT_REVOLVER", 3, SoundManager.Effect.GUNSHOT_REVOLVER)
        soundManager.loadWeaponSound("GUNSHOT_TOMMYGUN", 2, SoundManager.Effect.GUNSHOT_REVOLVER)
        soundManager.loadWeaponSound("GUNSHOT_SHOTGUN", 2, SoundManager.Effect.GUNSHOT_REVOLVER)

        println("--- Loading Procedural SFX ---")
        soundManager.load(SoundManager.Effect.FIRE_LOOP)
        soundManager.load(SoundManager.Effect.EXPLOSION)
        soundManager.load(SoundManager.Effect.PUNCH_HIT)
        soundManager.load(SoundManager.Effect.ITEM_PICKUP)
        soundManager.load(SoundManager.Effect.RELOAD_CLICK)
        soundManager.load(SoundManager.Effect.GLASS_BREAK)
        soundManager.load(SoundManager.Effect.CAR_CRASH_HEAVY)

        println("--- Finished Loading Sounds ---")
    }
}
