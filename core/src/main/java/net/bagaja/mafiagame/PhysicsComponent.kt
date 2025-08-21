package net.bagaja.mafiagame

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

// A data class to hold all physics-related state for a character.
data class PhysicsComponent(
    var position: Vector3,
    val size: Vector3,
    var speed: Float,

    // Internal state managed by the physics system
    var velocity: Vector3 = Vector3(),
    var isGrounded: Boolean = false,
    var isMoving: Boolean = false,

    // Animation-related state tied to movement
    var walkAnimationTimer: Float = 0f,
    var wobbleAngle: Float = 0f,

    // This can be controlled by AI or player input
    var facingRotationY: Float = 0f
) {
    val bounds = BoundingBox()

    fun updateBounds() {
        bounds.set(
            Vector3(position.x - size.x / 2, position.y - size.y / 2, position.z - size.z / 2),
            Vector3(position.x + size.x / 2, position.y + size.y / 2, position.z + size.z / 2)
        )
    }
}
