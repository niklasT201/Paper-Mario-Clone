package net.bagaja.mafiagame

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
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, blockBounds, intersection)) {
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
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, objectBounds, intersection)) {
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
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, itemBounds, intersection)) {
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
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, carBounds, intersection)) {
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
            val bounds = house.getBoundingBox()
            val intersection = Vector3()

            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, bounds, intersection)) {
                val distance = ray.origin.dst2(intersection)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestHouse = house
                }
            }
        }

        return closestHouse
    }
}
