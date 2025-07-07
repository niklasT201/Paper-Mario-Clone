package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array

/**
 * Interface for any game object that can be made transparent when it's between the camera and the player.
 */
interface Occludable {
    fun setOccluded(isOccluded: Boolean)
    fun getBoundingBox(out: BoundingBox): BoundingBox
}

/**
 * Manages making objects transparent when they occlude the view of the player.
 */
class OcclusionSystem {
    private val currentlyOccluded = mutableSetOf<Occludable>()
    private val previouslyOccluded = mutableSetOf<Occludable>()
    private val intersection = Vector3()
    private val ray = Ray()
    private val tempBounds = BoundingBox()

    /**
     * Updates which objects are occluded and applies/removes transparency.
     * This should be called once per frame, before rendering the objects.
     */
    fun update(camera: Camera, targetPosition: Vector3, potentialOccluders: Array<out Occludable>) {
        // Store the set from the last frame and clear the current set
        previouslyOccluded.clear()
        previouslyOccluded.addAll(currentlyOccluded)
        currentlyOccluded.clear()

        // Define a ray from the camera to the player
        ray.set(camera.position, targetPosition.cpy().sub(camera.position).nor())
        val targetDistSq = camera.position.dst2(targetPosition)

        // Find all objects that intersect the ray between the camera and player
        for (occluder in potentialOccluders) {
            val bounds = occluder.getBoundingBox(tempBounds)

            // Check if ray intersects the bounds AND the intersection point is between camera and player
            if (Intersector.intersectRayBounds(ray, bounds, intersection)) {
                if (camera.position.dst2(intersection) < targetDistSq) {
                    currentlyOccluded.add(occluder)
                }
            }
        }

        // Determine which objects need their state changed
        val becameOccluded = currentlyOccluded - previouslyOccluded
        val becameVisible = previouslyOccluded - currentlyOccluded

        // Apply transparency to newly occluded objects
        for (occluder in becameOccluded) {
            occluder.setOccluded(true)
        }

        // Remove transparency from objects that are no longer occluded
        for (occluder in becameVisible) {
            occluder.setOccluded(false)
        }
    }

    fun reset() {
        for (occluder in currentlyOccluded) {
            occluder.setOccluded(false)
        }
        currentlyOccluded.clear()
        previouslyOccluded.clear()
    }
}
