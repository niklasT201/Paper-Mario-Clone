package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import kotlin.random.Random

// Enum to define the different bone types and their properties
enum class BoneType(
    val texturePath: String,
    val isStanding: Boolean, // True for skulls, false for bones that lay flat
    val width: Float,
    val height: Float
) {
    SKULL("textures/particles/bones/skull.png", true, 1.5f, 1.5f),
    BONE_ONE("textures/particles/bones/bone_one.png", false, 2f, 1f),
    BONE_TWO("textures/particles/bones/bone_two.png", false, 2f, 1f),
    BROKEN_BONE("textures/particles/bones/broken_bone.png", false, 1.8f, 1f)
}

// Data class to hold an active bone instance in the world
data class GameBone(
    val instance: ModelInstance,
    val position: Vector3
)

class BoneSystem {
    private val boneModels = mutableMapOf<BoneType, Model>()
    private val renderableInstances = Array<ModelInstance>()

    private lateinit var billboardModelBatch: ModelBatch
    private lateinit var billboardShaderProvider: BillboardShaderProvider

    fun initialize() {
        billboardShaderProvider = BillboardShaderProvider().apply {
            setBillboardLightingStrength(0.8f) // Bones should be affected by light
            setMinLightLevel(0.3f)             // Don't let them be pure black in shadows
        }
        billboardModelBatch = ModelBatch(billboardShaderProvider)
        val modelBuilder = ModelBuilder()

        for (type in BoneType.entries) {
            try {
                val texture = Texture(Gdx.files.internal(type.texturePath)).apply {
                    setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                }
                val material = Material(
                    TextureAttribute.createDiffuse(texture),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                    IntAttribute.createCullFace(GL20.GL_NONE)
                )

                // Create a different model based on whether it stands or lays flat
                val model = if (type.isStanding) {
                    // For skulls: Create a vertical billboard that faces the camera
                    modelBuilder.createRect(
                        -type.width / 2, -type.height / 2, 0f,
                        type.width / 2, -type.height / 2, 0f,
                        type.width / 2, type.height / 2, 0f,
                        -type.width / 2, type.height / 2, 0f,
                        0f, 0f, 1f,
                        material,
                        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                    )
                } else {
                    // For laying bones: Create a horizontal plane
                    modelBuilder.createRect(
                        -type.width / 2f, 0f, type.height / 2f, -type.width / 2f, 0f, -type.height / 2f,
                        type.width / 2f, 0f, -type.height / 2f, type.width / 2f, 0f, type.height / 2f,
                        0f, 1f, 0f,
                        material,
                        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                    )
                }
                boneModels[type] = model
            } catch (e: Exception) {
                println("ERROR: Could not load bone texture for ${type.name}: ${e.message}")
            }
        }
    }

    /**
     * The main function to call on enemy/NPC death. Handles all randomization.
     */
    fun spawnBones(deathPosition: Vector3, facingRotationY: Float, sceneManager: SceneManager) {
        val groundY = sceneManager.findHighestSupportY(deathPosition.x, deathPosition.z, deathPosition.y, 0.1f, 4f)
        val bonesToSpawn = mutableListOf<GameBone>()
        val layingBoneTypes = listOf(BoneType.BONE_ONE, BoneType.BONE_TWO, BoneType.BROKEN_BONE)

        // Rule: 60% chance to spawn one skull
        if (Random.nextFloat() < 0.6f) {
            createBone(BoneType.SKULL, groundY, deathPosition, facingRotationY)?.let { bonesToSpawn.add(it) }
        }

        // Rule: Spawn 0 to 3 additional laying bones
        val boneCount = Random.nextInt(0, 4)
        for (i in 0 until boneCount) {
            val randomBoneType = layingBoneTypes.random()
            createBone(randomBoneType, groundY, deathPosition)?.let { bonesToSpawn.add(it) }
        }

        // Add the created bones to the active scene
        if (bonesToSpawn.isNotEmpty()) {
            sceneManager.activeBones.addAll(*bonesToSpawn.toTypedArray())
        }
    }

    private fun createBone(type: BoneType, groundY: Float, centerPos: Vector3, rotationY: Float = 0f): GameBone? {
        val model = boneModels[type] ?: return null
        val instance = ModelInstance(model).apply { userData = "effect" }

        // Randomize position in a small radius around the death point
        val spawnRadius = 2.0f
        val offsetX = (Random.nextFloat() * 2f - 1f) * spawnRadius
        val offsetZ = (Random.nextFloat() * 2f - 1f) * spawnRadius

        val bonePosition = if (type.isStanding) {
            // Skulls are centered vertically
            Vector3(centerPos.x + offsetX, groundY + type.height / 2f, centerPos.z + offsetZ)
        } else {
            // Laying bones are flat on the ground
            Vector3(centerPos.x + offsetX, groundY + 0.07f, centerPos.z + offsetZ) // Tiny offset to prevent Z-fighting
        }

        instance.transform.setToTranslation(bonePosition)
        if (type.isStanding) {
            // This is a skull, apply the direction the character was facing.
            instance.transform.rotate(Vector3.Y, rotationY)
        } else {
            // Give laying bones a random rotation
            instance.transform.rotate(Vector3.Y, Random.nextFloat() * 360f)
        }

        return GameBone(instance, bonePosition)
    }

    fun render(camera: Camera, environment: Environment, activeBones: Array<GameBone>) {
        if (activeBones.isEmpty) return

        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)
        renderableInstances.clear()

        for (bone in activeBones) {
            renderableInstances.add(bone.instance)
        }
        billboardModelBatch.render(renderableInstances, environment)
        billboardModelBatch.end()
    }

    fun dispose() {
        boneModels.values.forEach { it.dispose() }
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
