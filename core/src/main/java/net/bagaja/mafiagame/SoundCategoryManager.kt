package net.bagaja.mafiagame

enum class SoundCategory(val displayName: String) {
    MUSIC("Music"),
    AMBIENCE("Ambience"), // City, traffic, wind
    WEATHER("Weather"),   // Rain, thunder
    WEAPONS("Weapons"),   // Gunshots, reloads, melee
    VEHICLES("Vehicles"), // Car doors, engines, crashes
    CHARACTER("Character"), // Footsteps, player hurt
    UI_GENERAL("UI & General") // Item pickups, menu clicks
}

/**
 * A static manager to map sound IDs to their respective categories for audio settings.
 */
object SoundCategoryManager {

    private val categoryMap = mutableMapOf<SoundCategory, List<String>>()

    fun initialize() {
        categoryMap[SoundCategory.MUSIC] = listOf(
            "PIANO_1", "PIANO_LOOP_1", "PIANO_LOOP_2", "SAXOPHONE_1", "SAXOPHONE_LOOP_1",
            "SAXOPHONE_2", "SAXOPHONE_3", "SPOOKY_LOOP", "VIOLIN_1", "VOICE_1", "VOICE_2",
            "VOICE_3", "VOICE_4", "VOICE_5", "VOICE_6"
        )
        categoryMap[SoundCategory.AMBIENCE] = listOf(
            "CITY_AMBIENCE_V1", "CITY_AMBIENCE_V2", "CITY_AMBIENCE_V3", "CITY_AMBIENCE_V4",
            "CITY_AMBIENCE_V5", "CITY_AMBIENCE_V6", "TRAFFIC_AMBIENCE_V1", "TRAFFIC_AMBIENCE_V2",
            "TRAFFIC_AMBIENCE_V3", "TRAFFIC_AMBIENCE_V4", "VEHICLE_CAR_V1", "VEHICLE_CAR_V2",
            "VEHICLE_CAR_V3", "VEHICLE_CAR_V4", "VEHICLE_BIKE_V1", "VEHICLE_BIKE_V2",
            "VEHICLE_BIKE_V3", "WIND_LOOP", "WIND_MEDIUM_V1", "WIND_MEDIUM_V2", "WIND_HEAVY_V1",
            "WIND_HEAVY_V2", "WIND_HEAVY_V3", "WIND_HEAVY_V4", "WIND_HEAVY_V5", "FIRE_BURNING_V1",
            "FIRE_BURNING_V2", "FIRE_BURNING_V3", "FIRE_BURNING_V4", "FIRE_BURNING_V5",
            "FIRE_CRACKLE_V1", "FIRE_CRACKLE_V2", "FIRE_CRACKLE_V3"
        )
        categoryMap[SoundCategory.WEATHER] = listOf(
            "RAIN_LIGHT", "RAIN_MEDIUM", "HEAVY_RAIN_V1", "HEAVY_RAIN_V2", "HEAVY_RAIN_V3",
            "HEAVY_RAIN_V4", "HEAVY_RAIN_V6", "HEAVY_RAIN_V7", "HEAVY_RAIN_V9", "HEAVY_RAIN_V10",
            "STORM_AMBIENCE", "STORM_END", "LIGHTNING_V1", "LIGHTNING_V2", "LIGHTNING_V3"
        )
        categoryMap[SoundCategory.WEAPONS] = listOf(
            "PUNCH_FIST", "PUNCH_BOXING", "PUNCH_GENERIC", "BASEBALL_BAT_HIT", "GUNSHOT_REVOLVER_V1",
            "GUNSHOT_REVOLVER_V2", "GUNSHOT_TOMMYGUN_V1", "GUNSHOT_TOMMYGUN_V2", "TOMMY_GUN_SINGLE",
            "TOMMY_GUN_AUTO", "GUNSHOT_SHOTGUN_V1", "GUNSHOT_SHOTGUN_V2", "SHOTGUN_PUMP_V1",
            "SHOTGUN_PUMP_V2", "SHOTGUN_RELOAD", "MACHINE_GUN_SINGLE", "MACHINE_GUN_AUTO",
            "EXPLOSION_HIGH", SoundManager.Effect.GUNSHOT_REVOLVER.name, SoundManager.Effect.RELOAD_CLICK.name,
            SoundManager.Effect.MELEE_SWOOSH.name, SoundManager.Effect.PUNCH_HIT.name,
            SoundManager.Effect.WOOD_SPLINTER.name
        )
        categoryMap[SoundCategory.VEHICLES] = listOf(
            "CAR_DRIVING_LOOP", "CAR_DOOR_OPEN_V1", "CAR_DOOR_OPEN_V2", "CAR_DOOR_OPEN_V3",
            "CAR_DOOR_CLOSE_V1", "CAR_DOOR_CLOSE_V2", "CAR_DOOR_CLOSE_V3", "CAR_DOOR_CLOSE_V4",
            "CAR_LOCKED_V1", "CAR_LOCKED_V2", "CAR_LOCKED_V3", "CAR_LOCKED_V4", "CAR_LOCKED_V5",
            "CAR_LOCKED_V6", "CAR_LOCKED_V7", "CAR_LOCKED_V8", "CAR_LOCKED_V9", "POLICE_CAR_LOCKED",
            SoundManager.Effect.CAR_CRASH_HEAVY.name, SoundManager.Effect.DOOR_LOCKED.name,
            SoundManager.Effect.CAR_DOOR_OPEN.name, SoundManager.Effect.CAR_DOOR_CLOSE.name
        )
        categoryMap[SoundCategory.CHARACTER] = listOf(
            "FOOTSTEP_V1", "FOOTSTEP_LOOP", "FOOTSTEP_V2", "FOOTSTEP_V3", "FOOTSTEP_V4", "FOOTSTEP_V5",
            "FOOTSTEP_V6", "PLAYER_HURT", SoundManager.Effect.PLAYER_HURT.name,
            SoundManager.Effect.FOOTSTEP.name, "WATER_SPLASH_V1", "WATER_SPLASH_V2", "WATER_SPLASH_V3",
            "WATER_SPLASH_V4", "WATER_SPLASH_V5", "WATER_SPLASH_V6", "WATER_SPLASH_V7", "WATER_SPLASH_V8",
            "WATER_SPLASH_V9", "WATER_SPLASH_V10", SoundManager.Effect.WATER_SPLASH.name,
            SoundManager.Effect.BLOOD_SQUISH.name
        )
        categoryMap[SoundCategory.UI_GENERAL] = listOf(
            SoundManager.Effect.ITEM_PICKUP.name, SoundManager.Effect.GLASS_BREAK.name,
            SoundManager.Effect.TELEPORT.name, SoundManager.Effect.MISSION_START.name,
            SoundManager.Effect.OBJECTIVE_COMPLETE.name
        )
        println("SoundCategoryManager initialized with ${categoryMap.size} categories.")
    }

    fun getSoundIdsForCategory(category: SoundCategory): List<String> {
        return categoryMap[category] ?: emptyList()
    }
}
