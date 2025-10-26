package net.bagaja.mafiagame

/**
 * The state for an object being washed away by rain.
 */
enum class WashAwayState {
    IDLE,      // Currently paused, not shrinking.
    SHRINKING  // Actively shrinking.
}

/**
 * A component to hold the state for any visual effect that can be washed away.
 */
data class WashAwayComponent(
    var state: WashAwayState = WashAwayState.IDLE,
    var timer: Float = 0f // Countdown timer for the current state.
)
