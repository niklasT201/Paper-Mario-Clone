package net.bagaja.mafiagame

import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array

class RaycastSystem(private val blockSize: Float) {
    private val intersection = Vector3()
    private val blockBounds = BoundingBox()
    private val objectBounds = BoundingBox()
    private val tempVec = Vector3()

    fun getBlockAtRay(ray: Ray, gameBlocks: Array<GameBlock>): GameBlock? {
        var closestBlock: GameBlock? = null
        var closestDistance = Float.MAX_VALUE

        for (gameBlock in gameBlocks) {
            gameBlock.getBoundingBox(blockSize, blockBounds)

            // Check if ray intersects with this block's bounding box
            if (Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
                val distance = ray.origin.dst2(intersection)
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
            // Create a bounding box for the object
            val objectBounds = gameObject.getBoundingBox()

            // Check if ray intersects with this object's bounding box
            if (Intersector.intersectRayBounds(ray, objectBounds, intersection)) {
                val distance = ray.origin.dst2(intersection) // Use dst2 for efficiency
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestObject = gameObject
                }
            }
        }

        return closestObject
    }

    fun getItemAtRay(ray: Ray, gameItems: Array<GameItem>): GameItem? {
        var closestItem: GameItem? = null
        var closestDistance = Float.MAX_VALUE

        for (item in gameItems) {
            if (item.isCollected) continue

            // Use the item's bounding box for ray intersection
            val itemBounds = item.getBoundingBox()

            // Check if ray intersects with this item's bounding box
            if (Intersector.intersectRayBounds(ray, itemBounds, intersection)) {
                val distance = ray.origin.dst2(intersection)
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
            if (Intersector.intersectRayBounds(ray, carBounds, intersection)) {
                val distance = ray.origin.dst2(intersection)
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

    fun getEntryPointAtRay(ray: Ray, entryPoints: Array<GameEntryPoint>): GameEntryPoint? {
        var closestEntryPoint: GameEntryPoint? = null
        var closestDistance = Float.MAX_VALUE

        for (entryPoint in entryPoints) {
            val entryBounds = entryPoint.debugInstance.calculateBoundingBox(BoundingBox())
            entryBounds.mul(entryPoint.debugInstance.transform) // Apply world transform

            if (Intersector.intersectRayBounds(ray, entryBounds, intersection)) {
                val distance = ray.origin.dst2(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestEntryPoint = entryPoint
                }
            }
        }
        return closestEntryPoint
    }

    fun getBackgroundAtRay(ray: Ray, gameBackgrounds: Array<GameBackground>): GameBackground? {
        var closestBackground: GameBackground? = null
        var closestDistance = Float.MAX_VALUE

        for (background in gameBackgrounds) {
            val halfWidth = background.backgroundType.width / 2f
            val height = background.backgroundType.height
            val halfDepth = 0.25f // A small depth for the flat object

            blockBounds.set(
                tempVec.set(background.position).sub(halfWidth, 0f, halfDepth),
                tempVec.set(background.position).add(halfWidth, height, halfDepth)
            )

            if (Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
                val distance = ray.origin.dst2(intersection)
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
                // Step 1: Calculate the local bounding box of the model
                image.modelInstance.calculateBoundingBox(blockBounds)

                // Step 2: Transform the box to its world position
                blockBounds.mul(image.modelInstance.transform)

                if (Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
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
            // Check if ray intersects with sphere
            if (Intersector.intersectRaySphere(ray, lightSource.position, LightSource.LIGHT_SIZE, intersection)) {
                val distance = ray.origin.dst2(intersection)
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
            var intersects = false

            if (interior.interiorType.is3D) {
                // Use the precise mesh intersection for 3D objects
                if (interior.intersectsRay(ray, intersection)) {
                    intersects = true
                }
            } else {
                // Use a generated bounding box for 2D objects
                val center = interior.position
                // Use the dimensions from the InteriorType enum
                val dimensions = tempVec.set(interior.interiorType.width, interior.interiorType.height, interior.interiorType.depth)
                // Center the box on the object's position
                val halfDim = dimensions.scl(0.5f)
                objectBounds.set(center.cpy().sub(halfDim), center.cpy().add(halfDim))

                if (Intersector.intersectRayBounds(ray, objectBounds, intersection)) {
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

    fun getSpawnerAtRay(ray: Ray, spawners: Array<GameSpawner>): GameSpawner? {
        var closestSpawner: GameSpawner? = null
        var closestDistance = Float.MAX_VALUE

        for (spawner in spawners) {
            if (Intersector.intersectRayBounds(ray, spawner.gameObject.getBoundingBox(), intersection)) {
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
            if (Intersector.intersectRayBounds(ray, enemy.physics.bounds, intersection)) {
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
            // CORRECTED: Access the bounds through the physics component
            if (Intersector.intersectRayBounds(ray, npc.physics.bounds, intersection)) {
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
