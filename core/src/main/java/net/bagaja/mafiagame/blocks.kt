package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import java.util.*

enum class BlockFace {
    TOP, BOTTOM, FRONT, BACK, RIGHT, LEFT;

    fun getOpposite(): BlockFace {
        return when (this) {
            TOP -> BOTTOM
            BOTTOM -> TOP
            FRONT -> BACK
            BACK -> FRONT
            RIGHT -> LEFT
            LEFT -> RIGHT
        }
    }
}

enum class BlockRotationMode {
    GEOMETRY, // The entire block model rotates
    TEXTURE_SIDES, // Only the textures on the side faces rotate
    TEXTURE_TOP;

    fun getDisplayName(): String {
        return when (this) {
            GEOMETRY -> "Geometry"
            TEXTURE_SIDES -> "Texture (Sides)"
            TEXTURE_TOP -> "Texture (Top)" // ADDED: Display name for the new mode
        }
    }
}

enum class BlockShape {
    FULL_BLOCK,
    SLAB_BOTTOM,
    SLAB_TOP,
    WEDGE,
    CORNER_WEDGE,
    VERTICAL_SLAB,
    PILLAR;

    fun getDisplayName(): String {
        return this.name.replace('_', ' ').lowercase(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

// Game block class to store block data
data class GameBlock(
    val blockType: BlockType,
    val shape: BlockShape,
    val position: Vector3,
    var rotationY: Float = 0f,
    var textureRotationY: Float = 0f,
    var topTextureRotationY: Float = 0f,
    val faceInstances: Map<BlockFace, ModelInstance>? = null, // For FULL_BLOCK
    val modelInstance: ModelInstance? = null,                // For all other shapes
) {
    val visibleFaces: MutableSet<BlockFace> = if (shape == BlockShape.FULL_BLOCK) BlockFace.entries.toMutableSet() else mutableSetOf()

    private val mesh = modelInstance?.model?.meshes?.firstOrNull()
    private val vertexFloats: FloatArray?
    private val indexShorts: ShortArray?
    private val vertexSize: Int

    // Helper vectors to avoid re-allocation
    private val v0 = Vector3()
    private val v1 = Vector3()
    private val v2 = Vector3()

    init {
        if (shape != BlockShape.FULL_BLOCK && mesh != null) {
            vertexSize = mesh.vertexAttributes.vertexSize / 4
            vertexFloats = FloatArray(mesh.numVertices * vertexSize)
            indexShorts = ShortArray(mesh.numIndices)
            mesh.getVertices(vertexFloats)
            mesh.getIndices(indexShorts)
        } else {
            vertexFloats = null
            indexShorts = null
            vertexSize = 0
        }
        updateTransform()
    }

    fun getBoundingBox(blockSize: Float, out: BoundingBox): BoundingBox {
        if (shape == BlockShape.FULL_BLOCK) {
            val halfSize = blockSize / 2f
            val blockHeight = blockSize * this.blockType.height / 2f
            out.set(
                Vector3(position.x - halfSize, position.y - blockHeight, position.z - halfSize),
                Vector3(position.x + halfSize, position.y + blockHeight, position.z + halfSize)
            )
            return out
        }

        val instance = modelInstance ?: faceInstances?.values?.firstOrNull()
        if (instance == null) {
            return out.set(Vector3.Zero, Vector3.Zero)
        }
        // Apply the instance's transform to the bounding box
        return instance.calculateBoundingBox(out).mul(instance.transform)
    }

    private fun updateTransform() {
        if (shape == BlockShape.FULL_BLOCK) {
            faceInstances?.values?.forEach {
                it.transform.setToTranslation(position).rotate(Vector3.Y, rotationY)
            }
        } else {
            modelInstance?.transform?.setToTranslation(position)?.rotate(Vector3.Y, rotationY)
        }
    }

    fun collidesWith(otherBounds: BoundingBox): Boolean {
        if (shape == BlockShape.FULL_BLOCK) {
            // Fsimple AABB check
            val thisBounds = getBoundingBox(4f, BoundingBox()) // Assuming 4f is blockSize
            return thisBounds.intersects(otherBounds)
        }

        // For custom shapes, perform per-triangle collision.
        if (vertexFloats == null || indexShorts == null || modelInstance == null) {
            return false // No mesh data
        }

        val thisBounds = modelInstance.calculateBoundingBox(BoundingBox()).mul(modelInstance.transform)
        if (!thisBounds.intersects(otherBounds)) {
            return false // Broad-phase check failed
        }

        for (i in indexShorts.indices step 3) {
            val i1 = indexShorts[i] * vertexSize
            val i2 = indexShorts[i + 1] * vertexSize
            val i3 = indexShorts[i + 2] * vertexSize

            v0.set(vertexFloats[i1], vertexFloats[i1 + 1], vertexFloats[i1 + 2]).mul(modelInstance.transform)
            v1.set(vertexFloats[i2], vertexFloats[i2 + 1], vertexFloats[i2 + 2]).mul(modelInstance.transform)
            v2.set(vertexFloats[i3], vertexFloats[i3 + 1], vertexFloats[i3 + 2]).mul(modelInstance.transform)

            if (intersectTriangleBounds(otherBounds, v0, v1, v2)) {
                return true
            }
        }
        return false
    }

    private fun intersectTriangleBounds(box: BoundingBox, v0: Vector3, v1: Vector3, v2: Vector3): Boolean {
        // fast triangle-AABB intersection test
        val minX = v0.x.coerceAtMost(v1.x.coerceAtMost(v2.x))
        val minY = v0.y.coerceAtMost(v1.y.coerceAtMost(v2.y))
        val minZ = v0.z.coerceAtMost(v1.z.coerceAtMost(v2.z))
        val maxX = v0.x.coerceAtLeast(v1.x.coerceAtLeast(v2.x))
        val maxY = v0.y.coerceAtLeast(v1.y.coerceAtLeast(v2.y))
        val maxZ = v0.z.coerceAtLeast(v1.z.coerceAtLeast(v2.z))

        return box.max.x >= minX && box.min.x <= maxX &&
            box.max.y >= minY && box.min.y <= maxY &&
            box.max.z >= minZ && box.min.z <= maxZ
    }

    fun isFaceSolid(worldFace: BlockFace): Boolean {
        if (!this.blockType.isVisible) return false

        val localFace = getLocalFace(worldFace, this.rotationY)

        return when (this.shape) {
            BlockShape.FULL_BLOCK -> true  // All faces are solid
            BlockShape.SLAB_BOTTOM -> localFace != BlockFace.TOP
            BlockShape.SLAB_TOP -> localFace != BlockFace.BOTTOM
            BlockShape.WEDGE -> localFace == BlockFace.BOTTOM || localFace == BlockFace.FRONT
            BlockShape.CORNER_WEDGE -> localFace == BlockFace.LEFT || localFace == BlockFace.BACK
            BlockShape.VERTICAL_SLAB -> false
            BlockShape.PILLAR -> false
        }
    }

    private fun getLocalFace(worldFace: BlockFace, rotationY: Float): BlockFace {
        val angle = (rotationY % 360).toInt()
        if (angle == 0) return worldFace

        // Map world face to local face based on rotation
        return when (angle) {
            90, -270 -> when (worldFace) {
                BlockFace.FRONT -> BlockFace.LEFT
                BlockFace.BACK -> BlockFace.RIGHT
                BlockFace.RIGHT -> BlockFace.FRONT
                BlockFace.LEFT -> BlockFace.BACK
                else -> worldFace // Top and Bottom are unaffected by Y rotation
            }
            180, -180 -> worldFace.getOpposite() // 180-degree rotation just flips everything
            270, -90 -> when (worldFace) {
                BlockFace.FRONT -> BlockFace.RIGHT
                BlockFace.BACK -> BlockFace.LEFT
                BlockFace.RIGHT -> BlockFace.BACK
                BlockFace.LEFT -> BlockFace.FRONT
                else -> worldFace
            }
            else -> worldFace
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
    val category: BlockCategory,
    val hasCollision: Boolean = true,
    val isVisible: Boolean = true
) {
    // NATURAL
    GRASS("Grass", "textures/objects/grass.png", 1.0f, BlockCategory.NATURAL),
    DIRTY_GROUND("Dirty Ground", "textures/objects/dirty_ground.png", 1.0f, BlockCategory.NATURAL),
    WATER("Water", "textures/objects/water_tile.png", 0.8f, BlockCategory.NATURAL, hasCollision = false),

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
    INVISIBLE("Invisible", "textures/objects/debug_invisible.png", 1.0f, BlockCategory.WALLS, hasCollision = true, isVisible = false),
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
    HANGING("Umhang", "textures/objects/hanging.png", 1.0f, BlockCategory.WALLS, hasCollision = false, isVisible = true),
    HANGING_MIDDLE("Umhang Mitte", "textures/objects/hanging_middle.png", 1.0f, BlockCategory.WALLS, hasCollision = false, isVisible = true),
    HANGING_BOTTOM("Umhang Boden", "textures/objects/hanging_bottom.png", 1.0f, BlockCategory.WALLS, hasCollision = false, isVisible = true),
    HANGING_BOTTOM_MIDDLE("Umhang Boden Mitte", "textures/objects/hanging_bottom_middle.png", 1.0f, BlockCategory.WALLS, hasCollision = false, isVisible = true),

    // CEILINGS
    CEILING("Ceiling", "textures/objects/celiling.png", 1.0f, BlockCategory.CEILINGS),
    CEILING_WITH_LAMP("Ceiling with Lamp", "textures/objects/ceiling_with_lamp.png", 1.0f, BlockCategory.CEILINGS),
    LIGHT_CEILING("Light Ceiling", "textures/objects/light_ceiling.png", 1.0f, BlockCategory.CEILINGS),
    BROKEN_CEILING("Broken Ceiling", "textures/objects/broken_ceiling.png", 1.0f, BlockCategory.CEILINGS),

    // STREET & OUTDOOR
    COBBLESTONE("Cobblestone", "textures/objects/cobblestone_tile.png", 1.0f, BlockCategory.STREET),
    STONE("Stone", "textures/objects/stone_tile.png", 1.0f, BlockCategory.STREET),
    STREET_DIRTY_PATH("Street (Low)", "textures/objects/street_dirty_path.png", 0.8f, BlockCategory.STREET),
    STREET_LOW("Street (Low)", "textures/objects/street_cheap.png", 0.8f, BlockCategory.STREET),
    SIDEWALK_POOR("Sidewalk (Poor)", "textures/objects/sidewalk_poor.png", 1.0f, BlockCategory.STREET),
    STREET_INDUSTRY("Street (Industry)", "textures/objects/street_industry.png", 0.8f, BlockCategory.STREET),
    SIDEWALK("Sidewalk", "textures/objects/sidewalk.png", 1.0f, BlockCategory.STREET),
    SIDEWALK_START("Sidewalk Start", "textures/objects/sidewalk_start.png", 1.0f, BlockCategory.STREET),
    SIDEWALK_INDUSTRY("Sidewalk (Industry)", "textures/objects/sidewalk_indutry.png", 1.0f, BlockCategory.STREET),
    STREET_TILE("Street Tile", "textures/objects/street_tile.png", 0.8f, BlockCategory.STREET),
    DARK_STREET("Street Tile", "textures/objects/street_beton.png", 0.8f, BlockCategory.STREET),
    DARK_STREET_MIDDLE("Street Tile", "textures/objects/street_beton_middle.png", 0.8f, BlockCategory.STREET),
    DARK_STREET_MIXED("Street Tile", "textures/objects/street_beton_mixed.png", 0.8f, BlockCategory.STREET),
    OLD_STREET("Street Tile", "textures/objects/street_poor_area.png", 0.8f, BlockCategory.STREET),

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
