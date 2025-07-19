package net.bagaja.mafiagame

import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array

class RaycastSystem(private val blockSize: Float) {

    fun getBlockAtRay(ray: Ray, gameBlocks: Array<GameBlock>): GameBlock? {
        var closestBlock: GameBlock? = null
        var closestDistance = Float.MAX_VALUE

        for (gameBlock in gameBlocks) {
            val blockBounds = BoundingBox()
            blockBounds.set(
                Vector3(gameBlock.position.x - blockSize / 2, gameBlock.position.y - blockSize / 2, gameBlock.position.z - blockSize / 2),
                Vector3(gameBlock.position.x + blockSize / 2, gameBlock.position.y + blockSize / 2, gameBlock.position.z + blockSize / 2)
            )

            // Check if ray intersects with this block's bounding box
            val intersection = Vector3()
            if (Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
                val distance = ray.origin.dst(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestBlock = gameBlock
                }
            }
        }

        return closestBlock
    }

    fun getObjectAtRay(ray: Ray, gameObjects: Array<GameObject>): GameObject? {
        var closestObject: GameObject? = null
        var closestDistance = Float.MAX_VALUE

        for (gameObject in gameObjects) {
            // Create a bounding box for the object (adjust size as needed)
            val objectBounds = BoundingBox()
            val objectSize = 2f // Adjust this based on your object sizes
            objectBounds.set(
                Vector3(gameObject.position.x - objectSize / 2, gameObject.position.y, gameObject.position.z - objectSize / 2),
                Vector3(gameObject.position.x + objectSize / 2, gameObject.position.y + objectSize, gameObject.position.z + objectSize / 2)
            )

            // Check if ray intersects with this object's bounding box
            val intersection = Vector3()
            if (Intersector.intersectRayBounds(ray, objectBounds, intersection)) {
                val distance = ray.origin.dst(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestObject = gameObject
                }
            }
        }

        return closestObject
    }

    fun getItemAtRay(ray: Ray, itemSystem: ItemSystem): GameItem? {
        var closestItem: GameItem? = null
        var closestDistance = Float.MAX_VALUE

        for (item in itemSystem.getAllItems()) {
            if (item.isCollected) continue

            // Use the item's bounding box for ray intersection
            val itemBounds = item.getBoundingBox()

            // Check if ray intersects with this item's bounding box
            val intersection = Vector3()
            if (Intersector.intersectRayBounds(ray, itemBounds, intersection)) {
                val distance = ray.origin.dst(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestItem = item
                }
            }
        }

        return closestItem
    }

    fun getCarAtRay(ray: Ray, gameCars: Array<GameCar>): GameCar? {
        var closestCar: GameCar? = null
        var closestDistance = Float.MAX_VALUE

        for (car in gameCars) {
            val carBounds = car.getBoundingBox()

            // Check if ray intersects with this car's bounding box
            val intersection = Vector3()
            if (Intersector.intersectRayBounds(ray, carBounds, intersection)) {
                val distance = ray.origin.dst(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestCar = car
                }
            }
        }

        return closestCar
    }

    fun getHouseAtRay(ray: Ray, gameHouses: Array<GameHouse>): GameHouse? {
        var closestHouse: GameHouse? = null
        var closestDistance = Float.MAX_VALUE

        for (house in gameHouses) {
            val intersection = Vector3()

            // Check against the actual model mesh
            if (house.intersectsRay(ray, intersection)) {
                val distance = ray.origin.dst2(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestHouse = house
                }
            }
        }

        return closestHouse
    }

    fun getBackgroundAtRay(ray: Ray, gameBackgrounds: Array<GameBackground>): GameBackground? {
        var closestBackground: GameBackground? = null
        var closestDistance = Float.MAX_VALUE

        for (background in gameBackgrounds) {
            val bounds = BoundingBox()
            val halfWidth = background.backgroundType.width / 2f
            val height = background.backgroundType.height
            val halfDepth = 0.25f // A small depth for the flat object

            bounds.set(
                Vector3(
                    background.position.x - halfWidth,
                    background.position.y, // The bottom of the object is at its y-position
                    background.position.z - halfDepth
                ),
                Vector3(
                    background.position.x + halfWidth,
                    background.position.y + height, // The top is at y + height
                    background.position.z + halfDepth
                )
            )

            val intersection = Vector3()
            if (Intersector.intersectRayBounds(ray, bounds, intersection)) {
                val distance = ray.origin.dst(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestBackground = background
                }
            }
        }

        return closestBackground
    }

    fun getParallaxImageAtRay(ray: Ray, parallaxSystem: ParallaxBackgroundSystem): ParallaxBackgroundSystem.ParallaxImage? {
        var closestImage: ParallaxBackgroundSystem.ParallaxImage? = null
        var closestDistance = Float.MAX_VALUE

        for (layer in parallaxSystem.getLayers()) {
            for (image in layer.images) {
                val bounds = BoundingBox()
                // Step 1: Calculate the local bounding box of the model
                image.modelInstance.calculateBoundingBox(bounds)

                // Step 2: Transform the box to its world position
                bounds.mul(image.modelInstance.transform)

                val intersection = Vector3()
                if (Intersector.intersectRayBounds(ray, bounds, intersection)) {
                    val distance = ray.origin.dst2(intersection)
                    if (distance < closestDistance) {
                        closestDistance = distance
                        closestImage = image
                    }
                }
            }
        }
        return closestImage
    }

    fun getLightSourceAtRay(ray: Ray, lightingManager: LightingManager): LightSource? {
        var closestLight: LightSource? = null
        var closestDistance = Float.MAX_VALUE

        val lightSources = lightingManager.getLightSources()

        for ((_, lightSource) in lightSources) {
            // Create a sphere around the light source position
            val lightCenter = Vector3(lightSource.position)
            val lightRadius = LightSource.LIGHT_SIZE // Use the light's size as radius

            // Check if ray intersects with sphere
            val intersection = Vector3()
            if (Intersector.intersectRaySphere(ray, lightCenter, lightRadius, intersection)) {
                val distance = ray.origin.dst(intersection)

                if (distance < closestDistance) {
                    closestDistance = distance
                    closestLight = lightSource
                }
            }
        }

        return closestLight
    }

    fun getInteriorAtRay(ray: Ray, gameInteriors: Array<GameInterior>): GameInterior? {
        var closestInterior: GameInterior? = null
        var closestDistance = Float.MAX_VALUE

        for (interior in gameInteriors) {
            val intersection = Vector3()
            var intersects = false

            if (interior.interiorType.is3D) {
                // Use the precise mesh intersection for 3D objects
                if (interior.intersectsRay(ray, intersection)) {
                    intersects = true
                }
            } else {
                // Use a generated bounding box for 2D objects
                val box = BoundingBox()
                val center = interior.position
                // Use the dimensions from the InteriorType enum
                val dimensions = Vector3(interior.interiorType.width, interior.interiorType.height, interior.interiorType.depth)
                // Center the box on the object's position
                val halfDim = Vector3(dimensions).scl(0.5f)
                box.set(center.cpy().sub(halfDim), center.cpy().add(halfDim))

                if (Intersector.intersectRayBounds(ray, box, intersection)) {
                    intersects = true
                }
            }

            if (intersects) {
                val distance = ray.origin.dst2(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestInterior = interior
                }
            }
        }
        return closestInterior
    }

    fun getParticleSpawnerAtRay(ray: Ray, spawners: Array<GameParticleSpawner>): GameParticleSpawner? {
        var closestSpawner: GameParticleSpawner? = null
        var closestDistance = Float.MAX_VALUE

        for (spawner in spawners) {
            val bounds = spawner.gameObject.getBoundingBox()

            val intersection = Vector3()
            if (Intersector.intersectRayBounds(ray, bounds, intersection)) {
                val distance = ray.origin.dst2(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestSpawner = spawner
                }
            }
        }
        return closestSpawner
    }

    fun getEnemyAtRay(ray: Ray, gameEnemies: Array<GameEnemy>): GameEnemy? {
        var closestEnemy: GameEnemy? = null
        var closestDistance = Float.MAX_VALUE

        for (enemy in gameEnemies) {
            val enemyBounds = enemy.getBoundingBox()
            val intersection = Vector3()
            if (Intersector.intersectRayBounds(ray, enemyBounds, intersection)) {
                val distance = ray.origin.dst2(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestEnemy = enemy
                }
            }
        }
        return closestEnemy
    }

    fun getNPCAtRay(ray: Ray, gameNPCs: Array<GameNPC>): GameNPC? {
        var closestNPC: GameNPC? = null
        var closestDistance = Float.MAX_VALUE

        for (npc in gameNPCs) {
            val npcBounds = npc.getBoundingBox()
            val intersection = Vector3()
            if (Intersector.intersectRayBounds(ray, npcBounds, intersection)) {
                val distance = ray.origin.dst2(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestNPC = npc
                }
            }
        }
        return closestNPC
    }
}
