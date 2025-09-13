package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.utils.ObjectMap // Use ObjectMap for JSON compatibility

// This is the top-level class that will be saved to the JSON file.
data class GameSaveState(
    var playerState: PlayerStateData = PlayerStateData(),
    var worldState: WorldStateData = WorldStateData(),
    var missionState: MissionProgressData = MissionProgressData(),
    var carPathState: CarPathData = CarPathData()
)

// --- INDIVIDUAL DATA SNAPSHOTS ---

data class PlayerStateData(
    var position: Vector3 = Vector3(),
    var money: Int = 0,
    var weapons: ObjectMap<WeaponType, Int> = ObjectMap(),
    var equippedWeapon: WeaponType = WeaponType.UNARMED
)

data class WorldStateData(
    var blocks: GdxArray<BlockData> = GdxArray(),
    var cars: GdxArray<CarData> = GdxArray(),
    var enemies: GdxArray<EnemyData> = GdxArray(),
    var npcs: GdxArray<NpcData> = GdxArray(),
    var items: GdxArray<ItemData> = GdxArray(),
    var objects: GdxArray<ObjectData> = GdxArray(),
    var houses: GdxArray<HouseData> = GdxArray(),
    var lights: GdxArray<LightData> = GdxArray(),
    var spawners: GdxArray<SpawnerData> = GdxArray()
)

data class CarPathData(
    var nodes: GdxArray<CarPathNodeData> = GdxArray()
)

data class MissionProgressData(
    var activeMissionId: String? = null,
    var activeMissionObjectiveIndex: Int = 0,
    var completedMissionIds: MutableSet<String> = mutableSetOf()
)

// --- ENTITY-SPECIFIC SNAPSHOTS ---

data class BlockData(
    var blockType: BlockType = BlockType.GRASS,
    var shape: BlockShape = BlockShape.FULL_BLOCK,
    var position: Vector3 = Vector3(),
    var rotationY: Float = 0f,
    var textureRotationY: Float = 0f,
    var topTextureRotationY: Float = 0f,
    var cameraVisibility: CameraVisibility = CameraVisibility.ALWAYS_VISIBLE
)

data class CarData(
    var id: String = "",
    var carType: CarType = CarType.DEFAULT,
    var position: Vector3 = Vector3(),
    var health: Float = 100f,
    var isLocked: Boolean = false,
    var driverId: String? = null // The ID of the NPC or Enemy driving it
)

data class EnemyData(
    var id: String = "",
    var enemyType: EnemyType = EnemyType.MOUSE_THUG,
    var behaviorType: EnemyBehavior = EnemyBehavior.STATIONARY_SHOOTER,
    var position: Vector3 = Vector3(),
    var health: Float = 100f,
    var inventory: GdxArray<ItemData> = GdxArray(),
    var weapons: ObjectMap<WeaponType, Int> = ObjectMap(),
    var equippedWeapon: WeaponType = WeaponType.UNARMED
)

data class NpcData(
    var id: String = "",
    var npcType: NPCType = NPCType.GEORGE_MELES,
    var behaviorType: NPCBehavior = NPCBehavior.WANDER,
    var position: Vector3 = Vector3(),
    var health: Float = 100f
)

data class ItemData(
    var itemType: ItemType = ItemType.MONEY_STACK,
    var position: Vector3 = Vector3(),
    var ammo: Int = 0,
    var value: Int = 0
)

data class ObjectData(
    var objectType: ObjectType = ObjectType.TREE,
    var position: Vector3 = Vector3()
)

data class HouseData(
    var houseType: HouseType = HouseType.HOUSE_1,
    var position: Vector3 = Vector3(),
    var isLocked: Boolean = false,
    var rotationY: Float = 0f
)

data class LightData(
    var position: Vector3 = Vector3(),
    var color: Color = Color.WHITE,
    var intensity: Float = 50f,
    var range: Float = 50f
)

data class SpawnerData(
    var position: Vector3 = Vector3(),
    var spawnerType: SpawnerType = SpawnerType.PARTICLE,
    // Add all spawner properties here to save/load them correctly
    var spawnInterval: Float = 5.0f,
    var minSpawnRange: Float = 0f,
    var maxSpawnRange: Float = 100f
    // ... etc. for all spawner settings if needed
)

data class CarPathNodeData(
    var id: String = "",
    var position: Vector3 = Vector3(),
    var nextNodeId: String? = null,
    var isOneWay: Boolean = false
)
