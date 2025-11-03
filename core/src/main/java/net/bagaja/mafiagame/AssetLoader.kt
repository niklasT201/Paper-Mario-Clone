package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound

object AssetLoader {

    // A helper function to reduce boilerplate code
    private fun loadSoundFile(soundManager: SoundManager, id: String, path: String) {
        try {
            soundManager.load(id, "sound_effects/$path")
        } catch (e: Exception) {
            println("ERROR loading sound '$id' from '$path': ${e.message}")
        }
    }

    fun loadGameSounds(soundManager: SoundManager) {
        println("--- Loading All Game Sounds ---")

        // Helper function to reduce boilerplate
        fun load(id: String, path: String) = soundManager.load(id, "sound_effects/$path")

        // Melee
        load("PUNCH_FIST", "weapons/punch/fist_punch.ogg")
        load("PUNCH_BOXING", "weapons/punch/boxing.ogg")
        load("PUNCH_GENERIC", "weapons/punch/punch.ogg")
        load("BASEBALL_BAT_HIT", "weapons/baseball_bat/baseball_bat_wall.ogg")

        // Revolver (Loaded as variations of a base ID)
        load("GUNSHOT_REVOLVER_V1", "weapons/revolver/revolver_shot.ogg")
        load("GUNSHOT_REVOLVER_V2", "weapons/revolver/shot_deep.ogg")

        // Tommy Gun (Separating single and auto sounds)
        load("GUNSHOT_TOMMYGUN_V1", "weapons/tommy_gun/tommy_gun_one.ogg")
        load("GUNSHOT_TOMMYGUN_V2", "weapons/tommy_gun/tommy_gun_two.ogg")
        load("TOMMY_GUN_SINGLE", "weapons/tommy_gun/one_shot.ogg")
        load("TOMMY_GUN_AUTO", "weapons/tommy_gun/multiple_shot.ogg")

        // Shotgun
        load("GUNSHOT_SHOTGUN_V1", "weapons/shotgun/shotgun_shot.ogg")
        load("GUNSHOT_SHOTGUN_V2", "weapons/shotgun/shotgun_shot_two.ogg")
        load("SHOTGUN_PUMP_V1", "weapons/shotgun/shotgun_before_shot.ogg")
        load("SHOTGUN_PUMP_V2", "weapons/shotgun/shotgun_before_shot_two.ogg")
        load("SHOTGUN_RELOAD", "weapons/shotgun/shotgun_reload.ogg")

        // Machine Gun
        load("MACHINE_GUN_SINGLE", "weapons/machine_gun/machine_gun_oneshot.ogg")
        load("MACHINE_GUN_AUTO", "weapons/machine_gun/machine_gun_shot_multiple.ogg")

        // Other
        load("EXPLOSION_HIGH", "explosion/explosion_high.ogg")

        // Fire Loop
        load("FIRE_BURNING_V1", "fire/burning_fire.ogg")
        load("FIRE_BURNING_V2", "fire/burning_fire_two.ogg")
        load("FIRE_BURNING_V3", "fire/burning_fire_three.ogg")
        load("FIRE_BURNING_V4", "fire/burning_fire_four.ogg")
        load("FIRE_BURNING_V5", "fire/burning_fire_five.ogg")
        load("FIRE_CRACKLE_V1", "fire/cracking_fire.ogg")
        load("FIRE_CRACKLE_V2", "fire/cracking_fire_two.ogg")
        load("FIRE_CRACKLE_V3", "fire/cracking_fire_three.ogg")

        // --- Load Procedural SFX ---
        println("--- Loading Procedural Fallbacks & Effects ---")
        soundManager.load(SoundManager.Effect.GUNSHOT_REVOLVER) // Keep as a final fallback
        soundManager.load(SoundManager.Effect.ITEM_PICKUP)
        soundManager.load(SoundManager.Effect.GLASS_BREAK)
        soundManager.load(SoundManager.Effect.CAR_CRASH_HEAVY)
        soundManager.load(SoundManager.Effect.RELOAD_CLICK)
        soundManager.load(SoundManager.Effect.DOOR_LOCKED)
        soundManager.load(SoundManager.Effect.CAR_DOOR_OPEN)
        soundManager.load(SoundManager.Effect.CAR_DOOR_CLOSE)
        soundManager.load(SoundManager.Effect.PLAYER_HURT)
        soundManager.load(SoundManager.Effect.MELEE_SWOOSH)
        soundManager.load(SoundManager.Effect.FOOTSTEP)
        soundManager.load(SoundManager.Effect.TELEPORT)
        soundManager.load(SoundManager.Effect.MISSION_START)
        soundManager.load(SoundManager.Effect.OBJECTIVE_COMPLETE)
        soundManager.load(SoundManager.Effect.WATER_SPLASH)
        soundManager.load(SoundManager.Effect.BLOOD_SQUISH)

        println("--- Finished Loading Sounds ---")
    }
}
