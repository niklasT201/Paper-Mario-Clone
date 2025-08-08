package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import kotlin.math.floor

class ChunkManager(private val faceCullingSystem: FaceCullingSystem, private val blockSize: Float, private val game: MafiaGame ) {
    val chunks = mutableMapOf<ChunkPosition, Chunk>()
    private val dirtyChunks = mutableSetOf<Chunk>()
    private val searchBounds = BoundingBox()

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

    fun getBlocksAround(position: Vector3, radius: Float, out: Array<GameBlock>): Array<GameBlock> {
        out.clear()
        val radiusSq = radius * radius // Use squared distance for efficiency

        // Determine the search area and set our reusable bounding box
        val minX = position.x - radius
        val maxX = position.x + radius
        val minY = position.y - radius
        val maxY = position.y + radius
        val minZ = position.z - radius
        val maxZ = position.z + radius
        searchBounds.set(Vector3(minX, minY, minZ), Vector3(maxX, maxY, maxZ))

        for (chunk in chunks.values) {
            if (!chunk.boundingBox.intersects(searchBounds)) {
                continue // Skip this entire chunk if it's not in the search area.
            }

            for (block in chunk.blocks.values) {
                // Check squared distance to avoid expensive square root calculations.
                if (block.position.dst2(position) <= radiusSq) {
                    out.add(block)
                }
            }
        }
        return out
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

        var hasGeometry = false

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

                val builder = modelBuilder.part(
                    "chunk_part_${instance.hashCode()}",
                    GL20.GL_TRIANGLES,
                    (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong(),
                    Material(material) // Create a COPY of the material to be safe
                )

                // Copy the mesh to avoid modifying the original cached model
                val meshCopy = mesh.copy(true)
                val transform = instance.transform
                meshCopy.transform(transform)

                // 4. Add the entire transformed mesh copy to the builder
                builder.addMesh(meshCopy)
                hasGeometry = true
            }
        }

        if (hasGeometry) {
            chunk.model = modelBuilder.end()
            chunk.modelInstance = ModelInstance(chunk.model)
            chunk.modelInstance?.transform?.idt()
        }
    }

    fun render(modelBatch: ModelBatch, environment: Environment, camera: Camera) {
        // 1. Calculate the chunk coordinates of the camera's current position.
        val cameraChunkX = floor(camera.position.x / (Chunk.CHUNK_SIZE * blockSize)).toInt()
        val cameraChunkZ = floor(camera.position.z / (Chunk.CHUNK_SIZE * blockSize)).toInt()

        // 2. Get the render distance from the main game class.
        val renderDistance = game.renderDistanceInChunks

        // 3. Define the square area of chunks to check, centered on the camera.
        val minChunkX = cameraChunkX - renderDistance
        val maxChunkX = cameraChunkX + renderDistance
        val minChunkZ = cameraChunkZ - renderDistance
        val maxChunkZ = cameraChunkZ + renderDistance

        for (chunk in chunks.values) {
            val cx = chunk.position.x
            val cz = chunk.position.z

            // 4. If the chunk is outside our defined render distance, skip it entirely.
            if (cx < minChunkX || cx > maxChunkX || cz < minChunkZ || cz > maxChunkZ) {
                continue // Skip this chunk
            }

            // 5. If the chunk is nearby, perform the original frustum culling and render it.
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
