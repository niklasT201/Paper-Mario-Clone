package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3

// Game block class to store block data
data class GameBlock(
    val modelInstance: ModelInstance,
    val blockType: BlockType,
    val position: Vector3
)

// Block type definitions
enum class BlockType(val displayName: String, val texturePath: String) {
    GRASS("Grass", "textures/objects/grass.png"),
    COBBLESTONE("Cobblestone", "textures/objects/cobblestone_tile.png"),
    ROOM_FLOOR("Room Floor", "textures/objects/room_floor_tile.png"),
    STONE("Stone", "textures/objects/stone_tile.png"),
    WINDOW_OPENED("Window Opened","textures/objects/window.png"),
    WINDOW_CLOSE("Window Closed","textures/objects/window_closed.png"),
}
