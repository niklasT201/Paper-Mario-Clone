package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Array

data class AnimationFrame(
    val texture: Texture,
    val duration: Float // Duration in seconds for this frame
)

class Animation(
    val name: String,
    val frames: Array<AnimationFrame>,
    val isLooping: Boolean = true
) {
    fun getTotalDuration(): Float {
        return frames.sumOf { it.duration.toDouble() }.toFloat()
    }

    fun getFrameAtTime(time: Float): Texture {
        if (frames.isEmpty) return frames.first().texture

        var currentTime = if (isLooping) {
            time % getTotalDuration()
        } else {
            time.coerceAtMost(getTotalDuration())
        }

        for (frame in frames) {
            if (currentTime <= frame.duration) {
                return frame.texture
            }
            currentTime -= frame.duration
        }

        // Fallback to last frame
        return frames.last().texture
    }
}
