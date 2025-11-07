package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Array

data class AnimationFrame(
    val texture: Texture,
    val duration: Float, // Duration in seconds for this frame
    val offsetX: Float = 0f // Offset in world units for this frame
)

class Animation(
    val name: String,
    val frames: Array<AnimationFrame>,
    val isLooping: Boolean = true
) {
    fun getTotalDuration(): Float {
        return frames.sumOf { it.duration.toDouble() }.toFloat()
    }

    fun getFrameAtTime(time: Float): AnimationFrame {
        if (frames.isEmpty) return frames.first() // Should not happen if created correctly

        var currentTime = if (isLooping) {
            time % getTotalDuration()
        } else {
            time.coerceAtMost(getTotalDuration() - 0.001f) // Subtract a tiny amount to prevent index out of bounds
        }

        for (frame in frames) {
            if (currentTime < frame.duration) {
                return frame
            }
            currentTime -= frame.duration
        }

        // Fallback to last frame
        return frames.last()
    }
}
