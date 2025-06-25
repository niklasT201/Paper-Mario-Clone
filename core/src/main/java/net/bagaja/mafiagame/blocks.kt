package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

enum class BlockFace {
    TOP,
    BOTTOM,
    FRONT, // +Z direction
    BACK,  // -Z direction
    RIGHT, // +X direction
    LEFT   // -X direction
}

// Game block class to store block data
data class GameBlock(
    val faceInstances: Map<BlockFace, ModelInstance>,
    val blockType: BlockType,
    val position: Vector3,
    var rotationY: Float = 0f
) {

    val visibleFaces: MutableSet<BlockFace> = BlockFace.entries.toMutableSet()

    fun getBoundingBox(blockSize: Float): BoundingBox {
        val bounds = BoundingBox()
        val blockHeight = blockSize * blockType.height
        val halfWidth = blockSize / 2f
        val halfHeight = blockHeight / 2f

        bounds.set(
            Vector3(position.x - halfWidth, position.y - halfHeight, position.z - halfWidth),
            Vector3(position.x + halfWidth, position.y + halfHeight, position.z + halfWidth)
        )
        return bounds
    }

    // Method to update the block's transform with current rotation
    fun updateTransform() {
        for (instance in faceInstances.values) {
            // Reset transform first to avoid accumulating rotations
            instance.transform.idt()
            // Apply translation
            instance.transform.setTranslation(position)
            // Apply rotation around Y axis
            if (rotationY != 0f) {
                instance.transform.rotate(Vector3.Y, rotationY)
            }
        }
    }

    // Helper method to get direction name
    private fun getDirectionName(): String {
        return when (rotationY.toInt()) {
            0 -> "North"
            90 -> "East"
            180 -> "South"
            270 -> "West"
            else -> "Custom (${rotationY}°)"
        }
    }
}

// Block categories for better organization
enum class BlockCategory(val displayName: String, val color: Int) {
    NATURAL("Natural", 0x4CAF50),        // Green
    FLOORS("Floors", 0x8D6E63),          // Brown
    WALLS("Walls", 0x757575),            // Gray
    CEILINGS("Ceilings", 0xF5F5F5),      // Light Gray
    STREET("Street & Outdoor", 0x424242), // Dark Gray
    WINDOWS("Windows & Doors", 0x2196F3), // Blue
}

// Block type definitions with categories
enum class BlockType(
    val displayName: String,
    val texturePath: String,
    val height: Float = 1.0f,
    val category: BlockCategory
) {
    // NATURAL
    GRASS("Grass", "textures/objects/grass.png", 1.0f, BlockCategory.NATURAL),
    DIRTY_GROUND("Dirty Ground", "textures/objects/dirty_ground.png", 1.0f, BlockCategory.NATURAL),

    // FLOORS
    ROOM_FLOOR("Room Floor", "textures/objects/room_floor_tile.png", 1.0f, BlockCategory.FLOORS),
    RESTAURANT_FLOOR("Restaurant Floor", "textures/objects/floor_tile.png", 1.0f, BlockCategory.FLOORS),
    CARGO_FLOOR("Cargo Floor", "textures/objects/cargo_tile.png", 1.0f, BlockCategory.FLOORS),
    BROWN_CLEAR_FLOOR("Brown Clear Floor", "textures/objects/brown_clear_floor.png", 1.0f, BlockCategory.FLOORS),
    BROWN_FLOOR("Brown Floor", "textures/objects/brown_floor.png", 1.0f, BlockCategory.FLOORS),
    CARD_FLOOR("Card Floor", "textures/objects/card_floor.png", 1.0f, BlockCategory.FLOORS),
    CLUSTER_FLOOR("Cluster Floor", "textures/objects/cluster_floor.png", 1.0f, BlockCategory.FLOORS),
    DARK_YELLOW_FLOOR("Dark Yellow Floor", "textures/objects/dark_yellow_floor.png", 1.0f, BlockCategory.FLOORS),
    FLOOR("Floor", "textures/objects/floor.png", 1.0f, BlockCategory.FLOORS),
    GRAY_FLOOR("Gray Floor", "textures/objects/gray_floor.png", 1.0f, BlockCategory.FLOORS),
    STRIPED_FLOOR("Striped Floor", "textures/objects/striped_floor.png", 1.0f, BlockCategory.FLOORS),
    WOODEN_FLOOR("Wooden Floor", "textures/objects/wooden_floor.png", 1.0f, BlockCategory.FLOORS),
    CARPET("Carpet", "textures/objects/carpet.png", 1.0f, BlockCategory.FLOORS),
    BETON_TILE("Beton Tile", "textures/objects/beton_tile.png", 1.0f, BlockCategory.FLOORS),
    FLIESSEN("Fliessen", "textures/objects/fliessen.png", 1.0f, BlockCategory.FLOORS),

    // WALLS
    BRICK_WALL("Brick Wall", "textures/objects/wall_brick.png", 1.0f, BlockCategory.WALLS),
    BRICK_WALL_PNG("Brick Wall Alt", "textures/objects/brick_wall.png", 1.0f, BlockCategory.WALLS),
    BROWN_BRICK_WALL("Brown Brick Wall", "textures/objects/brown_brick_wall.png", 1.0f, BlockCategory.WALLS),
    BROKEN_WALL("Broken Wall", "textures/objects/broken_wall.png", 1.0f, BlockCategory.WALLS),
    CRACKED_WALL("Cracked Wall", "textures/objects/cracked_wall.png", 1.0f, BlockCategory.WALLS),
    DARK_WALL("Dark Wall", "textures/objects/dark_wall.png", 1.0f, BlockCategory.WALLS),
    OFFICE_WALL("Office Wall", "textures/objects/office_wall.png", 1.0f, BlockCategory.WALLS),
    SPRAYED_WALL("Sprayed Wall", "textures/objects/sprayed_wall.png", 1.0f, BlockCategory.WALLS),
    TAPETE_WALL("Tapete Wall", "textures/objects/tapete_wall.png", 1.0f, BlockCategory.WALLS),
    TRANS_WALL("Trans Wall", "textures/objects/trans_wall.png", 1.0f, BlockCategory.WALLS),
    WALL("Wall", "textures/objects/wall.png", 1.0f, BlockCategory.WALLS),
    WOOD_WALL("Wood Wall", "textures/objects/wood_wall.png", 1.0f, BlockCategory.WALLS),
    CONCRETE("Concrete", "textures/objects/concrete.png", 1.0f, BlockCategory.WALLS),
    CONCRETE_WALL("Concrete Wall", "textures/objects/concrete_wall.png", 1.0f, BlockCategory.WALLS),
    STRIPED_TAPETE("Striped Tapete", "textures/objects/striped_tapete.png", 1.0f, BlockCategory.WALLS),
    TAPETE("Tapete", "textures/objects/tapete.png", 1.0f, BlockCategory.WALLS),
    WALL_EGG_BOTTOM("Beige Wall Bottom", "textures/objects/wall_bottom.png", 1.0f, BlockCategory.WALLS),
    WALL_EGG("Beige Wall", "textures/objects/wall_eggyellow.png", 1.0f, BlockCategory.WALLS),
    STONE_WALL("Stone Wall", "textures/objects/wall_stone.png", 1.0f, BlockCategory.WALLS),

    // CEILINGS
    CEILING("Ceiling", "textures/objects/celiling.png", 1.0f, BlockCategory.CEILINGS),
    CEILING_WITH_LAMP("Ceiling with Lamp", "textures/objects/ceiling_with_lamp.png", 1.0f, BlockCategory.CEILINGS),
    LIGHT_CEILING("Light Ceiling", "textures/objects/light_ceiling.png", 1.0f, BlockCategory.CEILINGS),
    BROKEN_CEILING("Broken Ceiling", "textures/objects/broken_ceiling.png", 1.0f, BlockCategory.CEILINGS),

    // STREET & OUTDOOR
    COBBLESTONE("Cobblestone", "textures/objects/cobblestone_tile.png", 1.0f, BlockCategory.STREET),
    STONE("Stone", "textures/objects/stone_tile.png", 1.0f, BlockCategory.STREET),
    STREET_LOW("Street (Low)", "textures/objects/street_cheap.png", 0.8f, BlockCategory.STREET),
    SIDEWALK_POOR("Sidewalk (Poor)", "textures/objects/sidewalk_poor.png", 1.0f, BlockCategory.STREET),
    STREET_INDUSTRY("Street (Industry)", "textures/objects/street_industry.png", 0.8f, BlockCategory.STREET),
    SIDEWALK("Sidewalk", "textures/objects/sidewalk.png", 1.0f, BlockCategory.STREET),
    SIDEWALK_START("Sidewalk Start", "textures/objects/sidewalk_start.png", 1.0f, BlockCategory.STREET),
    SIDEWALK_INDUSTRY("Sidewalk (Industry)", "textures/objects/sidewalk_indutry.png", 1.0f, BlockCategory.STREET),
    STREET_TILE("Street Tile", "textures/objects/street_tile.png", 1.0f, BlockCategory.STREET),

    // WINDOWS & DOORS
    WINDOW_OPENED("Window Opened", "textures/objects/window.png", 1.0f, BlockCategory.WINDOWS),
    WINDOW_CLOSE("Window Closed", "textures/objects/window_closed.png", 1.0f, BlockCategory.WINDOWS);

    companion object {
        // Get all block types for a specific category
        fun getByCategory(category: BlockCategory): List<BlockType> {
            return entries.filter { it.category == category }
        }

        // Get all categories that have blocks
        fun getUsedCategories(): List<BlockCategory> {
            return entries.map { it.category }.distinct().sortedBy { it.ordinal }
        }
    }
}
