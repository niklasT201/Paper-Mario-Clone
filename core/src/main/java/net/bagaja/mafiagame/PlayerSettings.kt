package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json

data class PlayerSettingsData(
    var violenceLevel: ViolenceLevel = ViolenceLevel.FULL_VIOLENCE,
    var hudStyle: HudStyle = HudStyle.MINIMALIST,
    var selectedShader: ShaderEffect = ShaderEffect.NONE,
    var fullscreen: Boolean = false,
    var letterbox: Boolean = false,
    var cinematicBars: Boolean = false,
    var targetingIndicator: Boolean = true,
    var trajectoryArc: Boolean = true,
    var meleeRangeIndicator: Boolean = true,
    var meleeIndicatorStyle: IndicatorStyle = IndicatorStyle.SOLID_CIRCLE,
    var muzzleFlashLight: Boolean = true,
    var masterVolume: Float = 0.8f, // Default to 80%
    var musicVolume: Float = 0.7f,  // Default to 70%
    var sfxVolume: Float = 0.9f,     // Default to 90%
    var playMusic: Boolean = true,
    var playAmbience: Boolean = true,
    var playWeather: Boolean = true,
    var playWeapons: Boolean = true,
    var playVehicles: Boolean = true,
    var playCharacter: Boolean = true,
    var playUiGeneral: Boolean = true
)

// A manager class to handle loading and saving the settings
object PlayerSettingsManager {
    private const val SETTINGS_FILE = ".mafiagame/settings.json"
    private val json = Json()
    var current: PlayerSettingsData = PlayerSettingsData()
        private set

    fun load() {
        val file = Gdx.files.external(SETTINGS_FILE)
        if (file.exists()) {
            try {
                current = json.fromJson(PlayerSettingsData::class.java, file)
                println("Player settings loaded successfully.")
            } catch (e: Exception) {
                println("Could not parse settings file, using defaults. Error: ${e.message}")
                current = PlayerSettingsData()
            }
        } else {
            println("No settings file found, creating with default values.")
            current = PlayerSettingsData()
            save() // Create the file for the first time
        }
    }

    fun save() {
        try {
            val file = Gdx.files.external(SETTINGS_FILE)
            file.writeString(json.prettyPrint(current), false)
            println("Player settings saved.")
        } catch (e: Exception) {
            println("Error saving player settings: ${e.message}")
        }
    }
}
