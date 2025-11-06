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
    var carPathState: CarPathData = CarPathData(),
    var characterPathState: CharacterPathData = CharacterPathData()
)

// --- INDIVIDUAL DATA SNAPSHOTS ---

data class PlayerStateData(
    var position: Vector3 = Vector3(),
    var money: Int = 0,
    var weapons: ObjectMap<WeaponType, Int> = ObjectMap(),
    var equippedWeapon: WeaponType = WeaponType.UNARMED,
    var currentMagazineCounts: ObjectMap<WeaponType, Int> = ObjectMap()
)

data class EntryPointData(
    var id: String = "",
    var houseId: String = "",
    var position: Vector3 = Vector3()
)

data class BackgroundData(
    var backgroundType: BackgroundType = BackgroundType.SMALL_HOUSE,
    var position: Vector3 = Vector3()
)

data class ParallaxImageData(
    var imageType: ParallaxBackgroundSystem.ParallaxImageType = ParallaxBackgroundSystem.ParallaxImageType.MOUNTAINS,
    var basePositionX: Float = 0f,
    var layerIndex: Int = 0
)

data class TeleporterData(
    var id: String = "",
    var name: String = "Teleporter",
    var linkedTeleporterId: String? = null,
    var position: Vector3 = Vector3()
)

data class FireData(
    var id: String = "",
    var position: Vector3 = Vector3(),
    var isLooping: Boolean = true,
    var fadesOut: Boolean = false,
    var lifetime: Float = 20f,
    var canBeExtinguished: Boolean = true,
    var dealsDamage: Boolean = true,
    var damagePerSecond: Float = 10f,
    var damageRadius: Float = 5f,
    var initialScale: Float = 1f,
    var canSpread: Boolean = false,
    var generation: Int = 0,
    var associatedLightId: Int? = null
)

data class AudioEmitterData(
    var id: String = "",
    var position: Vector3 = Vector3(),
    var soundIds: MutableList<String> = mutableListOf(), // Was soundId: String
    var volume: Float = 1.0f,
    var range: Float = 100f,
    var playbackMode: EmitterPlaybackMode = EmitterPlaybackMode.LOOP_INFINITE,
    var playlistMode: EmitterPlaylistMode = EmitterPlaylistMode.SEQUENTIAL, // NEW
    var reactivationMode: EmitterReactivationMode = EmitterReactivationMode.AUTO_RESET, // NEW
    var interval: Float = 1.0f, // Was oneShotInterval
    var timedLoopDuration: Float = 30f,
    var minPitch: Float = 1.0f,
    var maxPitch: Float = 1.0f,
    var sceneId: String = "WORLD",
    var missionId: String? = null
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
    var spawners: GdxArray<SpawnerData> = GdxArray(),
    var entryPoints: GdxArray<EntryPointData> = GdxArray(),
    var backgrounds: GdxArray<BackgroundData> = GdxArray(),
    var parallaxImages: GdxArray<ParallaxImageData> = GdxArray(),
    var dayNightCycleTime: Float = 0f,
    var teleporters: GdxArray<TeleporterData> = GdxArray(),
    var fires: GdxArray<FireData> = GdxArray(),
    var audioEmitters: GdxArray<AudioEmitterData> = GdxArray()
)

data class CarPathData(
    var nodes: GdxArray<CarPathNodeData> = GdxArray()
)

data class MissionProgressData(
    var activeMissionId: String? = null,
    var activeMissionObjectiveIndex: Int = 0,
    var completedMissionIds: MutableSet<String> = mutableSetOf(),
    var missionVariables: ObjectMap<String, Any> = ObjectMap()
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
    var driverId: String? = null,
    var state: CarState = CarState.DRIVABLE,
    var wreckedTimer: Float = 0f,
    var fadeOutTimer: Float = 0f,
    var visualRotationY: Float = 0f,
    var areHeadlightsOn: Boolean = false,
    var assignedLockedSoundId: String? = null,
    var assignedOpenSoundId: String? = null,
    var assignedCloseSoundId: String? = null
)

data class EnemyData(
    var id: String = "",
    var enemyType: EnemyType = EnemyType.MOUSE_THUG,
    var behaviorType: EnemyBehavior = EnemyBehavior.STATIONARY_SHOOTER,
    var position: Vector3 = Vector3(),
    var health: Float = 100f,
    var assignedPathId: String? = null,
    var inventory: GdxArray<ItemData> = GdxArray(),
    var weapons: ObjectMap<WeaponType, Int> = ObjectMap(),
    var equippedWeapon: WeaponType = WeaponType.UNARMED,
    var currentState: AIState = AIState.IDLE,
    var currentMagazineCount: Int = 0,
    var provocationLevel: Float = 0f,
    var standaloneDialog: StandaloneDialog? = null,
    var standaloneDialogCompleted: Boolean = false
)

data class NpcData(
    var id: String = "",
    var npcType: NPCType = NPCType.GEORGE_MELES,
    var behaviorType: NPCBehavior = NPCBehavior.WANDER,
    var position: Vector3 = Vector3(),
    var health: Float = 100f,
    var assignedPathId: String? = null,
    var currentState: NPCState = NPCState.IDLE,
    var provocationLevel: Float = 0f,
    var inventory: GdxArray<ItemData> = GdxArray(),
    var isHonest: Boolean = true,
    var canCollectItems: Boolean = true,
    var pathFollowingStyle: PathFollowingStyle = PathFollowingStyle.CONTINUOUS,
    var standaloneDialog: StandaloneDialog? = null,
    var standaloneDialogCompleted: Boolean = false
)

data class ItemData(
    var id: String = "",
    var itemType: ItemType = ItemType.MONEY_STACK,
    var position: Vector3 = Vector3(),
    var ammo: Int = 0,
    var value: Int = 0
)

data class ObjectData(
    var id: String = "",
    var objectType: ObjectType = ObjectType.TREE,
    var position: Vector3 = Vector3(),
    var associatedLightId: Int? = null,
    var isBroken: Boolean = false
)

data class HouseData(
    var id: String = "",
    var houseType: HouseType = HouseType.HOUSE_1,
    var position: Vector3 = Vector3(),
    var isLocked: Boolean = false,
    var rotationY: Float = 0f,
    var entryPointId: String? = null,
    var assignedRoomTemplateId: String? = null,
    var exitDoorId: String? = null
)

data class LightData(
    var id: Int = 0,
    var position: Vector3 = Vector3(),
    var color: Color = Color.WHITE,
    var intensity: Float = 50f,
    var range: Float = 50f,
    var isEnabled: Boolean = true,
    var flickerMode: FlickerMode = FlickerMode.NONE,
    var loopOnDuration: Float = 0.1f,
    var loopOffDuration: Float = 0.1f,
    var timedFlickerLifetime: Float = 10f,
    var rotationX: Float = 0f,
    var rotationY: Float = 0f,
    var rotationZ: Float = 0f,
    var missionId: String? = null,
    var parentObjectId: String? = null
)

data class SpawnerData(
    var id: String = "",
    var position: Vector3 = Vector3(),
    var sceneId: String = "WORLD",
    var spawnerType: SpawnerType = SpawnerType.PARTICLE,
    var spawnInterval: Float = 5.0f,
    var minSpawnRange: Float = 0f,
    var maxSpawnRange: Float = 100f,
    var spawnerMode: SpawnerMode = SpawnerMode.CONTINUOUS,
    var isDepleted: Boolean = false,
    var spawnOnlyWhenPreviousIsGone: Boolean = false,
    var spawnedEntityId: String? = null,

    // Particle-specific
    var particleEffectType: ParticleEffectType = ParticleEffectType.SMOKE_FRAME_1,
    var minParticles: Int = 1,
    var maxParticles: Int = 3,

    // Item-specific
    var itemType: ItemType = ItemType.MONEY_STACK,
    var minItems: Int = 1,
    var maxItems: Int = 1,

    // Weapon-specific
    var weaponItemType: ItemType = ItemType.REVOLVER,
    var ammoSpawnMode: AmmoSpawnMode = AmmoSpawnMode.FIXED,
    var setAmmoValue: Int = 12,
    var randomMinAmmo: Int = 6,
    var randomMaxAmmo: Int = 18,

    // Enemy-specific
    var enemyType: EnemyType = EnemyType.MOUSE_THUG,
    var enemyBehavior: EnemyBehavior = EnemyBehavior.STATIONARY_SHOOTER,
    var enemyHealthSetting: HealthSetting = HealthSetting.FIXED_DEFAULT,
    var enemyCustomHealth: Float = 100f,
    var enemyMinHealth: Float = 80f,
    var enemyMaxHealth: Float = 120f,
    var enemyInitialWeapon: WeaponType = WeaponType.UNARMED,
    var enemyWeaponCollectionPolicy: WeaponCollectionPolicy = WeaponCollectionPolicy.CANNOT_COLLECT,
    var enemyCanCollectItems: Boolean = true,
    var enemyInitialMoney: Int = 0,

    // NPC-specific
    var npcType: NPCType = NPCType.GEORGE_MELES,
    var npcBehavior: NPCBehavior = NPCBehavior.WANDER,
    var npcIsHonest: Boolean = true,
    var npcCanCollectItems: Boolean = true,

    // Car-specific
    var carType: CarType = CarType.DEFAULT,
    var carIsLocked: Boolean = false,
    var carDriverType: String = "None",
    var carEnemyDriverType: EnemyType = EnemyType.MOUSE_THUG,
    var carNpcDriverType: NPCType = NPCType.GEORGE_MELES,
    var carSpawnDirection: CarSpawnDirection = CarSpawnDirection.LEFT,
    var upgradedWeapon: WeaponType? = null
)

data class CarPathNodeData(
    var id: String = "",
    var position: Vector3 = Vector3(),
    var nextNodeId: String? = null,
    var previousNodeId: String? = null,
    var isOneWay: Boolean = false,
    var sceneId: String = "WORLD",
    var missionId: String? = null
)

data class CharacterPathNodeData(
    var id: String = "",
    var position: Vector3 = Vector3(),
    var nextNodeId: String? = null,
    var previousNodeId: String? = null,
    var isOneWay: Boolean = false,
    var isMissionOnly: Boolean = false,
    var missionId: String? = null,
    var sceneId: String = "WORLD"
)

data class CharacterPathData(
    var nodes: GdxArray<CharacterPathNodeData> = GdxArray()
)
