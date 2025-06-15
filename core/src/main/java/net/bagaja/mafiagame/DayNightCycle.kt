package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class DayNightCycle {
    // Timing configuration (in seconds)
    val dayDuration = 20f * 60f      // 20 minutes for full day
    private val nightDuration = 10f * 60f    // 10 minutes for full night
    private val totalCycleDuration = dayDuration + nightDuration

    // Transition periods (in seconds)
    val sunriseTransition = 2f * 60f  // 2 minutes sunrise
    private val sunsetTransition = 2f * 60f   // 2 minutes sunset

    // Start at full daylight (after sunrise transition)
    var currentTime = sunriseTransition + 22f * 60f  // Start 5 minutes into the day

    enum class TimeOfDay {
        SUNRISE,
        DAY,
        SUNSET,
        NIGHT
    }

    fun update(deltaTime: Float, timeMultiplier: Float = 1.0f) {
        currentTime += deltaTime * timeMultiplier // Apply the multiplier here
        if (currentTime >= totalCycleDuration) {
            currentTime = 0f
        }
    }

    fun getCurrentTimeOfDay(): TimeOfDay {
        return when {
            currentTime < sunriseTransition -> TimeOfDay.SUNRISE
            currentTime < dayDuration - sunsetTransition -> TimeOfDay.DAY
            currentTime < dayDuration -> TimeOfDay.SUNSET
            else -> TimeOfDay.NIGHT
        }
    }

    fun getDayProgress(): Float {
        return currentTime / totalCycleDuration
    }

    fun getSunIntensity(): Float {
        return when (getCurrentTimeOfDay()) {
            TimeOfDay.SUNRISE -> {
                val progress = currentTime / sunriseTransition
                // Smooth transition from 0 to 1
                smoothStep(0f, 1f, progress)
            }
            TimeOfDay.DAY -> 1f
            TimeOfDay.SUNSET -> {
                val sunsetStart = dayDuration - sunsetTransition
                val progress = (currentTime - sunsetStart) / sunsetTransition
                // Smooth transition from 1 to 0
                smoothStep(1f, 0f, progress)
            }
            TimeOfDay.NIGHT -> 0f
        }
    }

    fun getAmbientIntensity(): Float {
        return when (getCurrentTimeOfDay()) {
            TimeOfDay.NIGHT -> 0.05f  // Very dark ambient for deep shadows
            TimeOfDay.SUNRISE -> 0.25f // Brighter during transitions
            TimeOfDay.SUNSET -> 0.25f
            TimeOfDay.DAY -> 0.4f     // A gentle ambient light for daytime shadows
        }
    }

    fun getSunColor(): Triple<Float, Float, Float> {
        return when (getCurrentTimeOfDay()) {
            TimeOfDay.SUNRISE -> Triple(1f, 0.7f, 0.4f)      // Orange sunrise
            TimeOfDay.DAY -> Triple(1f, 1f, 0.9f)            // Bright white/yellow
            TimeOfDay.SUNSET -> Triple(1f, 0.5f, 0.2f)       // Red/orange sunset
            TimeOfDay.NIGHT -> Triple(0f, 0f, 0f)            // No sun
        }
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = kotlin.math.max(0f, kotlin.math.min(1f, (x - edge0) / (edge1 - edge0)))
        return t * t * (3f - 2f * t)
    }

    fun getTimeString(): String {
        val hours = ((currentTime / totalCycleDuration) * 24f).toInt()
        val minutes = (((currentTime / totalCycleDuration) * 24f * 60f) % 60f).toInt()
        return String.format("%02d:%02d", hours, minutes)
    }

    // Utility functions for debugging/testing
    fun skipToNight() {
        currentTime = dayDuration + 1f
    }

    fun skipToDay() {
        currentTime = sunriseTransition + 1f
    }


    fun getCurrentTimeInfo(): String {
        return "${getTimeString()} - ${getCurrentTimeOfDay()}"
    }

    fun getSunDirection(): Vector3 {
        val dayProgress = (currentTime / dayDuration).coerceIn(0f, 1f)

        // Convert progress to an angle (0 to PI radians, or 0 to 180 degrees)
        val angle = dayProgress * PI.toFloat()

        // Create the direction vector
        val direction = Vector3(
            cos(angle),
            -sin(angle),
            -0.3f
        ).nor()

        return direction
    }
}
