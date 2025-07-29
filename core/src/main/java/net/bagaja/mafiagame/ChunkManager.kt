package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import kotlin.math.floor

class ChunkManager(private val faceCullingSystem: FaceCullingSystem, private val blockSize: Float) {
    val chunks = mutableMapOf<ChunkPosition, Chunk>()
    private val dirtyChunks = mutableSetOf<Chunk>()

    fun addBlock(block: GameBlock) {
        val chunkPos = worldToChunkPosition(block.position)
        val chunk = chunks.getOrPut(chunkPos) { Chunk(chunkPos, blockSize) }
        chunk.addBlock(block)
        markDirty(chunk, block.position)
    }

    fun removeBlock(block: GameBlock): GameBlock? {
        val chunkPos = worldToChunkPosition(block.position)
        val chunk = chunks[chunkPos] ?: return null
        val removedBlock = chunk.removeBlock(block)
        if (removedBlock != null) {
            markDirty(chunk, block.position)
        }
        return removedBlock
    }

    fun getBlockAtWorld(worldPos: Vector3): GameBlock? {
        val chunkPos = worldToChunkPosition(worldPos)
        return chunks[chunkPos]?.getBlockAtWorld(worldPos)
    }

    fun getBlocksInColumn(x: Float, z: Float): List<GameBlock> {
        val results = mutableListOf<GameBlock>()
        val chunkX = floor(x / (Chunk.CHUNK_SIZE * blockSize)).toInt()
        val chunkZ = floor(z / (Chunk.CHUNK_SIZE * blockSize)).toInt()
        val halfBlock = blockSize / 2f

        for ((pos, chunk) in chunks) {
            if (pos.x == chunkX && pos.z == chunkZ) {
                for (block in chunk.blocks.values) {
                    val blockMinX = block.position.x - halfBlock
                    val blockMaxX = block.position.x + halfBlock
                    val blockMinZ = block.position.z - halfBlock
                    val blockMaxZ = block.position.z + halfBlock
                    if (x in blockMinX..blockMaxX && z in blockMinZ..blockMaxZ) {
                        results.add(block)
                    }
                }
            }
        }
        return results
    }

    fun getAllBlocks(): Array<GameBlock> {
        val allBlocks = Array<GameBlock>()
        chunks.values.forEach { chunk ->
            allBlocks.addAll(*chunk.blocks.values.toTypedArray())
        }
        return allBlocks
    }

    fun processDirtyChunks() {
        if (dirtyChunks.isEmpty()) return

        val chunksToRebuild = dirtyChunks.toSet()
        dirtyChunks.clear()

        for (chunk in chunksToRebuild) {
            rebuildChunkMesh(chunk)
        }
    }

    private fun rebuildChunkMesh(chunk: Chunk) {
        chunk.dispose()
        if (chunk.blocks.isEmpty()) return

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val meshPartBuilders = mutableMapOf<Material, com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder>()

        faceCullingSystem.updateFacesForChunk(chunk, this)

        for (block in chunk.blocks.values) {
            if (!block.blockType.isVisible) continue

            val instancesToProcess = mutableListOf<ModelInstance>()
            if (block.shape == BlockShape.FULL_BLOCK && block.faceInstances != null) {
                for (face in block.visibleFaces) {
                    block.faceInstances[face]?.let { instancesToProcess.add(it) }
                }
            } else if (block.modelInstance != null) {
                instancesToProcess.add(block.modelInstance)
            }

            for (instance in instancesToProcess) {
                val mesh = instance.model.meshes.first()
                val material = instance.materials.first()

                val builder = meshPartBuilders.getOrPut(material) {
                    modelBuilder.part(
                        "chunk_part_${material.hashCode()}",
                        GL20.GL_TRIANGLES,
                        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong(),
                        material
                    )
                }

                // Copy the mesh to avoid modifying the original cached model
                val meshCopy = mesh.copy(true)

                val transform = instance.transform // This already contains the block's world position and rotation
                meshCopy.transform(transform)

                // 4. Add the entire transformed mesh copy to the builder
                builder.addMesh(meshCopy)
            }
        }

        if (meshPartBuilders.isNotEmpty()) {
            chunk.model = modelBuilder.end()
            chunk.modelInstance = ModelInstance(chunk.model)
            chunk.modelInstance?.transform?.idt()
        }
    }

    fun render(modelBatch: ModelBatch, environment: Environment, camera: Camera) {
        for (chunk in chunks.values) {
            chunk.modelInstance?.let { instance ->
                if (camera.frustum.boundsInFrustum(chunk.boundingBox)) {
                    modelBatch.render(instance, environment)
                }
            }
        }
    }

    private fun markDirty(chunk: Chunk, blockWorldPos: Vector3) {
        dirtyChunks.add(chunk)
        val local = chunk.worldToLocal(blockWorldPos)
        if (local.x == 0f) markNeighborDirty(chunk.position.x - 1, chunk.position.y, chunk.position.z)
        if (local.x == Chunk.CHUNK_SIZE - 1f) markNeighborDirty(chunk.position.x + 1, chunk.position.y, chunk.position.z)
        if (local.y == 0f) markNeighborDirty(chunk.position.x, chunk.position.y - 1, chunk.position.z)
        if (local.y == Chunk.CHUNK_SIZE - 1f) markNeighborDirty(chunk.position.x, chunk.position.y + 1, chunk.position.z)
        if (local.z == 0f) markNeighborDirty(chunk.position.x, chunk.position.y, chunk.position.z - 1)
        if (local.z == Chunk.CHUNK_SIZE - 1f) markNeighborDirty(chunk.position.x, chunk.position.y, chunk.position.z + 1)
    }

    private fun markNeighborDirty(cx: Int, cy: Int, cz: Int) {
        chunks[ChunkPosition(cx, cy, cz)]?.let { dirtyChunks.add(it) }
    }

    private fun worldToChunkPosition(worldPos: Vector3): ChunkPosition {
        val cx = floor(worldPos.x / (Chunk.CHUNK_SIZE * blockSize)).toInt()
        val cy = floor(worldPos.y / (Chunk.CHUNK_SIZE * blockSize)).toInt()
        val cz = floor(worldPos.z / (Chunk.CHUNK_SIZE * blockSize)).toInt()
        return ChunkPosition(cx, cy, cz)
    }

    fun loadInitialBlocks(blocks: Array<GameBlock>) {
        if (blocks.isEmpty) return
        for (block in blocks) {
            val chunkPos = worldToChunkPosition(block.position)
            val chunk = chunks.getOrPut(chunkPos) { Chunk(chunkPos, blockSize) }
            chunk.addBlock(block)
        }
        // Mark all chunks as dirty so they build their mesh on the first frame
        dirtyChunks.addAll(chunks.values)
        faceCullingSystem.recalculateAllFaces(getAllBlocks())
    }

    fun dispose() {
        chunks.values.forEach { it.dispose() }
        chunks.clear()
        dirtyChunks.clear()
    }
}
