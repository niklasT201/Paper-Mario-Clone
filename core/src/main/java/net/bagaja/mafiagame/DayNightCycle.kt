package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class DayNightCycle {
    // Timing configuration (in seconds)
    val dayDuration = 30f * 60f      // 20 minutes for full day
    private val nightDuration = 20f * 60f    // 10 minutes for full night
    private val totalCycleDuration = dayDuration + nightDuration

    // Transition periods (in seconds)
    val sunriseTime = 2f * 60f  // Sunrise colors are strongest at this time
    val sunsetTime = dayDuration - (2f * 60f) // Sunset colors are strongest at this time

    // Start at full daylight
    var currentTime = 7f * 60f  // Start 7 minutes into the day (well after sunrise)

    enum class TimeOfDay {
        SUNRISE,
        DAY,
        SUNSET,
        NIGHT
    }

    data class SkyInterpolationInfo(
        val from: TimeOfDay,
        val to: TimeOfDay,
        val progress: Float
    )

    fun update(deltaTime: Float, timeMultiplier: Float = 1.0f) {
        currentTime += deltaTime * timeMultiplier
        currentTime %= totalCycleDuration // Use modulo for clean wrapping
    }

    fun getCurrentTimeOfDay(): TimeOfDay {
        return when {
            currentTime < sunriseTime -> TimeOfDay.SUNRISE
            currentTime < sunsetTime -> TimeOfDay.DAY
            currentTime < dayDuration -> TimeOfDay.SUNSET
            else -> TimeOfDay.NIGHT
        }
    }

    fun getSkyColorInterpolation(): SkyInterpolationInfo {
        return when {
            // 1. Transition: Night -> Sunrise
            currentTime < sunriseTime -> {
                val progress = currentTime / sunriseTime
                SkyInterpolationInfo(TimeOfDay.NIGHT, TimeOfDay.SUNRISE, smoothStep(progress))
            }
            // 2. Transition: Sunrise -> Day
            currentTime < sunsetTime -> {
                val segmentDuration = sunsetTime - sunriseTime
                val timeInSegment = currentTime - sunriseTime
                val progress = timeInSegment / segmentDuration
                SkyInterpolationInfo(TimeOfDay.SUNRISE, TimeOfDay.DAY, smoothStep(progress))
            }
            // 3. Transition: Day -> Sunset
            currentTime < dayDuration -> {
                val segmentDuration = dayDuration - sunsetTime
                val timeInSegment = currentTime - sunsetTime
                val progress = timeInSegment / segmentDuration
                SkyInterpolationInfo(TimeOfDay.DAY, TimeOfDay.SUNSET, smoothStep(progress))
            }
            // 4. Transition: Sunset -> Night
            else -> {
                val segmentDuration = totalCycleDuration - dayDuration
                val timeInSegment = currentTime - dayDuration
                val progress = timeInSegment / segmentDuration
                SkyInterpolationInfo(TimeOfDay.SUNSET, TimeOfDay.NIGHT, smoothStep(progress))
            }
        }
    }

    fun getSunIntensity(): Float {
        // This logic remains similar, as it controls light intensity, not just color.
        val sunriseTransition = 2f * 60f
        val sunsetTransition = 2f * 60f

        return when {
            // Rising
            currentTime < sunriseTransition -> currentTime / sunriseTransition
            // Full Day
            currentTime < dayDuration - sunsetTransition -> 1f
            // Setting
            currentTime < dayDuration -> {
                val sunsetStart = dayDuration - sunsetTransition
                1.0f - ((currentTime - sunsetStart) / sunsetTransition)
            }
            // Night
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    fun getAmbientIntensity(): Float {
        // We can also smooth this for better transitions
        val dayAmbient = 0.4f
        val nightAmbient = 0.05f
        val transitionAmbient = 0.25f

        val sunIntensity = getSunIntensity()

        return when (getCurrentTimeOfDay()) {
            TimeOfDay.DAY -> dayAmbient
            TimeOfDay.NIGHT -> nightAmbient
            TimeOfDay.SUNRISE -> lerp(nightAmbient, transitionAmbient, sunIntensity)
            TimeOfDay.SUNSET -> lerp(nightAmbient, transitionAmbient, sunIntensity)
        }
    }

    fun getSunColor(): Triple<Float, Float, Float> {
        // Can also be interpolated for smoother color changes on objects
        return when (getCurrentTimeOfDay()) {
            TimeOfDay.SUNRISE -> Triple(1f, 0.7f, 0.4f)      // Orange sunrise
            TimeOfDay.DAY -> Triple(1f, 1f, 0.9f)            // Bright white/yellow
            TimeOfDay.SUNSET -> Triple(1f, 0.5f, 0.2f)       // Red/orange sunset
            TimeOfDay.NIGHT -> Triple(0f, 0f, 0f)            // No sun
        }
    }

    private fun smoothStep(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // Linear interpolation helper
    private fun lerp(a: Float, b: Float, f: Float): Float {
        return a + f * (b - a)
    }

    fun getDayProgress(): Float {
        return currentTime / totalCycleDuration
    }

    fun setDayProgress(progress: Float) {
        currentTime = progress.coerceIn(0f, 1f) * totalCycleDuration
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
        currentTime = sunriseTime + 1f
    }

    fun getCurrentTimeInfo(): String {
        return "${getTimeString()} - ${getCurrentTimeOfDay()}"
    }

    fun getSunDirection(): Vector3 {
        val dayProgress = (currentTime / dayDuration).coerceIn(0f, 1f)

        // Convert progress to an angle (0 to PI radians, or 0 to 180 degrees)
        val angle = dayProgress * PI.toFloat()
        return Vector3(cos(angle), -sin(angle), -0.3f).nor()
    }
}
