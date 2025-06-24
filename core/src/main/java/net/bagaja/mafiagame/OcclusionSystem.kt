package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array as GdxArray

class OcclusionSystem {

    // A map to store the original materials of objects that are currently transparent.
    private val transparentInstances = mutableMapOf<ModelInstance, Material>()

    // The level of transparency to apply. 0.0 is fully transparent, 1.0 is fully opaque.
    private val transparencyLevel = 0.25f

    // Helper objects to avoid creating new ones in the loop (good for performance)
    private val ray = Ray()
    private val intersection = Vector3()
    private val boundingBox = BoundingBox()
    private val playerTargetPosition = Vector3()
    private val cameraToPlayer = Vector3()
    private val rayDirection = Vector3()

    // Debug flag
    var debugMode = false

    fun isInstanceTransparent(instance: ModelInstance): Boolean {
        return transparentInstances.containsKey(instance)
    }

    /**
     * Updates the occlusion system every frame.
     * It casts a ray from the camera to the player and finds any objects that intersect it.
     *
     * @param camera The game's main camera.
     * @param playerPosition The current world position of the player.
     * @param occludableLists A list of all object arrays that can be made transparent.
     */
    fun update(camera: Camera, playerPosition: Vector3, vararg occludableLists: GdxArray<out Occludable>) {
        // Adjust the ray's target to be the center of the player model for better accuracy
        playerTargetPosition.set(playerPosition).add(0f, 1.5f, 0f) // Player center height

        // Calculate direction from camera to player
        cameraToPlayer.set(playerTargetPosition).sub(camera.position)
        val distanceToPlayer = cameraToPlayer.len()
        rayDirection.set(cameraToPlayer).nor()

        // Create a ray from the camera towards the player
        ray.set(camera.position, rayDirection)

        val occludedThisFrame = mutableSetOf<ModelInstance>()

        if (debugMode) {
            println("Occlusion Update: Camera at ${camera.position}, Player at $playerTargetPosition, Distance: $distanceToPlayer")
        }

        // 1. Find all objects that are currently occluding the view
        for (list in occludableLists) {
            for (item in list) {
                try {
                    val instance = item.modelInstance
                    val bounds = item.getBoundingBox(boundingBox)

                    // Check if the ray intersects the object's bounding box
                    if (Intersector.intersectRayBounds(ray, bounds, intersection)) {
                        val distToIntersection = camera.position.dst(intersection)

                        // Only occlude if the intersection is closer than the player
                        // Add a small buffer to prevent flickering
                        if (distToIntersection < (distanceToPlayer - 0.5f)) {
                            occludedThisFrame.add(instance)

                            if (debugMode) {
                                println("Occluding object at distance: $distToIntersection (player at $distanceToPlayer)")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (debugMode) {
                        println("Error processing occludable item: ${e.message}")
                    }
                }
            }
        }

        if (debugMode) {
            println("Found ${occludedThisFrame.size} occluding objects this frame")
        }

        // 2. Make newly occluded objects transparent
        for (instance in occludedThisFrame) {
            if (!transparentInstances.containsKey(instance)) {
                makeTransparent(instance)
            }
        }

        // 3. Restore objects that are no longer occluded
        val instancesToRestore = transparentInstances.keys.filter { it !in occludedThisFrame }
        for (instance in instancesToRestore) {
            restoreOpaque(instance)
        }
    }

    /**
     * Alternative update method that checks if the player is visible from the camera.
     * If not visible, it makes all blocking objects transparent.
     */
    fun updateVisibilityBased(camera: Camera, playerPosition: Vector3, vararg occludableLists: GdxArray<out Occludable>) {
        playerTargetPosition.set(playerPosition).add(0f, 1.5f, 0f)

        // Check if player is visible by casting multiple rays
        val isPlayerVisible = isPlayerVisible(camera, playerTargetPosition, *occludableLists)

        if (!isPlayerVisible) {
            // Player is not visible, make all potentially blocking objects transparent
            val blockingInstances = findBlockingObjects(camera, playerTargetPosition, *occludableLists)

            for (instance in blockingInstances) {
                if (!transparentInstances.containsKey(instance)) {
                    makeTransparent(instance)
                }
            }

            // Restore objects that are not blocking
            val instancesToRestore = transparentInstances.keys.filter { it !in blockingInstances }
            for (instance in instancesToRestore) {
                restoreOpaque(instance)
            }
        } else {
            // Player is visible, restore all transparent objects
            val allTransparent = transparentInstances.keys.toList()
            for (instance in allTransparent) {
                restoreOpaque(instance)
            }
        }
    }

    private fun isPlayerVisible(camera: Camera, playerPos: Vector3, vararg occludableLists: GdxArray<out Occludable>): Boolean {
        cameraToPlayer.set(playerPos).sub(camera.position)
        val distanceToPlayer = cameraToPlayer.len()
        rayDirection.set(cameraToPlayer).nor()
        ray.set(camera.position, rayDirection)

        // Check multiple points around the player for better detection
        val checkPoints = arrayOf(
            Vector3(playerPos).add(0f, 0f, 0f),      // Center
            Vector3(playerPos).add(0.3f, 0f, 0f),    // Right
            Vector3(playerPos).add(-0.3f, 0f, 0f),   // Left
            Vector3(playerPos).add(0f, 0f, 0.3f),    // Front
            Vector3(playerPos).add(0f, 0f, -0.3f)    // Back
        )

        for (checkPoint in checkPoints) {
            rayDirection.set(checkPoint).sub(camera.position).nor()
            ray.set(camera.position, rayDirection)
            val distToCheck = camera.position.dst(checkPoint)

            var blocked = false

            for (list in occludableLists) {
                for (item in list) {
                    val bounds = item.getBoundingBox(boundingBox)
                    if (Intersector.intersectRayBounds(ray, bounds, intersection)) {
                        val distToIntersection = camera.position.dst(intersection)
                        if (distToIntersection < (distToCheck - 0.2f)) {
                            blocked = true
                            break
                        }
                    }
                }
                if (blocked) break
            }

            if (!blocked) {
                return true // At least one point is visible
            }
        }

        return false // All points are blocked
    }

    private fun findBlockingObjects(camera: Camera, playerPos: Vector3, vararg occludableLists: GdxArray<out Occludable>): Set<ModelInstance> {
        val blockingInstances = mutableSetOf<ModelInstance>()

        cameraToPlayer.set(playerPos).sub(camera.position)
        val distanceToPlayer = cameraToPlayer.len()
        rayDirection.set(cameraToPlayer).nor()
        ray.set(camera.position, rayDirection)

        for (list in occludableLists) {
            for (item in list) {
                val instance = item.modelInstance
                val bounds = item.getBoundingBox(boundingBox)

                if (Intersector.intersectRayBounds(ray, bounds, intersection)) {
                    val distToIntersection = camera.position.dst(intersection)
                    if (distToIntersection < (distanceToPlayer - 0.3f)) {
                        blockingInstances.add(instance)
                    }
                }
            }
        }

        return blockingInstances
    }

    /**
     * Makes a ModelInstance transparent by modifying its material.
     */
    private fun makeTransparent(instance: ModelInstance) {
        try {
            // Get all materials of the instance (some models have multiple materials)
            if (instance.materials.size == 0) return

            val originalMaterial = instance.materials.first()

            // Store a COPY of the original material
            val storedMaterial = Material()
            storedMaterial.set(originalMaterial)
            transparentInstances[instance] = storedMaterial

            // Create a new material to apply transparency
            val transparentMaterial = Material()
            transparentMaterial.set(originalMaterial)

            // Add or update the blending attribute for transparency
            var blendingAttribute = transparentMaterial.get(BlendingAttribute::class.java, BlendingAttribute.Type)
            if (blendingAttribute == null) {
                blendingAttribute = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
                transparentMaterial.set(blendingAttribute)
            }
            blendingAttribute.blended = true
            blendingAttribute.opacity = transparencyLevel

            // Apply the new transparent material to all materials of the instance
            instance.materials.clear()
            instance.materials.add(transparentMaterial)

            if (debugMode) {
                println("Made object transparent with opacity: $transparencyLevel")
            }
        } catch (e: Exception) {
            if (debugMode) {
                println("Error making object transparent: ${e.message}")
            }
        }
    }

    /**
     * Restores a ModelInstance to its original opaque state.
     */
    private fun restoreOpaque(instance: ModelInstance) {
        transparentInstances[instance]?.let { originalMaterial ->
            try {
                // Restore the original material
                instance.materials.clear()
                instance.materials.add(originalMaterial)

                // Remove from our tracking map
                transparentInstances.remove(instance)

                if (debugMode) {
                    println("Restored object to opaque state")
                }
            } catch (e: Exception) {
                if (debugMode) {
                    println("Error restoring object opacity: ${e.message}")
                }
            }
        }
    }

    /**
     * Force restore all transparent objects (useful for scene transitions)
     */
    fun restoreAllOpaque() {
        val allTransparent = transparentInstances.keys.toList()
        for (instance in allTransparent) {
            restoreOpaque(instance)
        }
    }

    /**
     * Get the number of currently transparent objects (for debugging)
     */
    fun getTransparentObjectCount(): Int = transparentInstances.size

    /**
     * An interface to generalize any game object that can be occluded.
     */
    interface Occludable {
        val modelInstance: ModelInstance
        fun getBoundingBox(out: BoundingBox): BoundingBox
    }
}
