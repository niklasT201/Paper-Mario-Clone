package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap

class AnimationSystem {
    private val animations = ObjectMap<String, Animation>()
    var currentAnimation: Animation? = null
    private var currentAnimationTime = 0f
    private var currentTexture: Texture? = null

    fun addAnimation(animation: Animation) {
        animations.put(animation.name, animation)
    }

    fun createAnimation(name: String, texturePaths: kotlin.Array<String>, frameDuration: Float, isLooping: Boolean = true): Animation {
        val frames = Array<AnimationFrame>()

        for (path in texturePaths) {
            try {
                val texture = Texture(Gdx.files.internal(path))
                frames.add(AnimationFrame(texture, frameDuration))
            } catch (e: Exception) {
                println("Warning: Could not load texture: $path")
                // Continue with other frames
            }
        }

        if (frames.isEmpty) {
            println("Error: No valid frames found for animation: $name")
            // Create a dummy animation with a fallback texture
            val fallbackTexture = Texture(Gdx.files.internal("textures/player/pig_character.png"))
            frames.add(AnimationFrame(fallbackTexture, frameDuration))
        }

        val animation = Animation(name, frames, isLooping)
        addAnimation(animation)
        return animation
    }

    fun playAnimation(animationName: String, restart: Boolean = false) {
        val animation = animations.get(animationName)
        if (animation != null) {
            if (currentAnimation != animation || restart) {
                currentAnimation = animation
                currentAnimationTime = 0f
            }
        } else {
            println("Warning: Animation '$animationName' not found")
        }
    }

    fun update(deltaTime: Float) {
        currentAnimation?.let { animation ->
            currentAnimationTime += deltaTime
            currentTexture = animation.getFrameAtTime(currentAnimationTime)
        }
    }

    fun getCurrentTexture(): Texture? {
        return currentTexture
    }

    fun isAnimationFinished(): Boolean {
        val animation = currentAnimation ?: return true
        if (animation.isLooping) return false
        return currentAnimationTime >= animation.getTotalDuration()
    }

    fun getCurrentAnimationName(): String? {
        return currentAnimation?.name
    }

    fun dispose() {
        animations.values().forEach { animation ->
            animation.frames.forEach { frame ->
                frame.texture.dispose()
            }
        }
        animations.clear()
    }
}
