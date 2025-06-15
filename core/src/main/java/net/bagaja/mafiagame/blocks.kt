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
enum class BlockType(val displayName: String, val texturePath: String, val height: Float = 1.0f) {
    GRASS("Grass", "textures/objects/grass.png"),
    COBBLESTONE("Cobblestone", "textures/objects/cobblestone_tile.png"),
    ROOM_FLOOR("Room Floor", "textures/objects/room_floor_tile.png"),
    STONE("Stone", "textures/objects/stone_tile.png"),
    WINDOW_OPENED("Window Opened", "textures/objects/window.png"),
    WINDOW_CLOSE("Window Closed", "textures/objects/window_closed.png"),
    RESTAURANT_FLOOR("Restaurant Floor", "textures/objects/floor_tile.png"),
    CARGO_FLOOR("Cargo Floor", "textures/objects/cargo_tile.png"),
    BRICK_WALL("Brick Wall", "textures/objects/wall_brick.png"),
    STREET_LOW("Street (Low)", "textures/objects/street_cheap.png", 0.8f),

    BETON_TILE("Beton Tile", "textures/objects/beton_tile.png"),
    BRICK_WALL_PNG("Brick Wall Alt", "textures/objects/brick_wall.png"),
    BROKEN_CEILING("Broken Ceiling", "textures/objects/broken_ceiling.png"),
    BROKEN_WALL("Broken Wall", "textures/objects/broken_wall.png"),
    BROWN_BRICK_WALL("Brown Brick Wall", "textures/objects/brown_brick_wall.png"),
    BROWN_CLEAR_FLOOR("Brown Clear Floor", "textures/objects/brown_clear_floor.png"),
    BROWN_FLOOR("Brown Floor", "textures/objects/brown_floor.png"),
    CARD_FLOOR("Card Floor", "textures/objects/card_floor.png"),
    CARPET("Carpet", "textures/objects/carpet.png"),
    CEILING_WITH_LAMP("Ceiling with Lamp", "textures/objects/ceiling_with_lamp.png"),
    CEILING("Ceiling", "textures/objects/ceiling.png"),
    CLUSTER_FLOOR("Cluster Floor", "textures/objects/cluster_floor.png"),
    CRACKED_WALL("Cracked Wall", "textures/objects/cracked_wall.png"),
    DARK_WALL("Dark Wall", "textures/objects/dark_wall.png"),
    DARK_YELLOW_FLOOR("Dark Yellow Floor", "textures/objects/dark_yellow_floor.png"),
    DIRTY_GROUND("Dirty Ground", "textures/objects/dirty_ground.png"),
    FLIESSEN("Fliessen", "textures/objects/fliessen.png"),
    FLOOR("Floor", "textures/objects/floor.png"),
    GRAY_FLOOR("Gray Floor", "textures/objects/gray_floor.png"),
    LIGHT_CEILING("Light Ceiling", "textures/objects/light_ceiling.png"),
    OFFICE_WALL("Office Wall", "textures/objects/office_wall.png"),
    SIDEWALK("Sidewalk", "textures/objects/sidewalk.png"),
    SIDEWALK_START("Sidewalk Start", "textures/objects/sidewalk_start.png"),
    SPRAYED_WALL("Sprayed Wall", "textures/objects/sprayed_wall.png"),
    STREET_TILE("Street Tile", "textures/objects/street_tile.png"),
    STRIPED_FLOOR("Striped Floor", "textures/objects/striped_floor.png"),
    STRIPED_TAPETE("Striped Tapete", "textures/objects/striped_tapete.png"),
    TAPETE("Tapete", "textures/objects/tapete.png"),
    TAPETE_WALL("Tapete Wall", "textures/objects/tapete_wall.png"),
    TRANS_WALL("Trans Wall", "textures/objects/trans_wall.png"),
    WALL("Wall", "textures/objects/wall.png"),
    WOOD_WALL("Wood Wall", "textures/objects/wood_wall.png"),
    WOODEN_FLOOR("Wooden Floor", "textures/objects/wooden_floor.png");
}
