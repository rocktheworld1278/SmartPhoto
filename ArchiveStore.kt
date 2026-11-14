package com.smartphoto

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

data class ArchiveInfo(
    val id: String,
    val originalName: String,
    val originalBytes: Long,
    val archiveBytes: Long,
    val sha256: String
)

class ArchiveStore(private val context: Context) {
    private val dir = context.filesDir.resolve("archives").apply { mkdirs() }
    private val magic = byteArrayOf(0x53, 0x50, 0x48, 0x31) // SPH1

    fun list(): List<ArchiveInfo> = dir.listFiles()
        ?.filter { it.isFile && it.extension == "sph" }
        ?.mapNotNull { file ->
            runCatching {
                DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                    require(input.readNBytes(4).contentEquals(magic))
                    val name = input.readUTF()
                    val size = input.readLong()
                    val hash = input.readUTF()
                    ArchiveInfo(file.nameWithoutExtension, name, size, file.length(), hash)
                }
            }.getOrNull()
        }?.sortedByDescending { it.id } ?: emptyList()

    fun import(uri: Uri, originalName: String): ArchiveInfo {
        val id = UUID.randomUUID().toString()
        val target = dir.resolve("$id.sph")
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        try {
            context.contentResolver.openInputStream(uri).use { raw ->
                requireNotNull(raw) { "Cannot open selected file" }
                DataOutputStream(BufferedOutputStream(target.outputStream())).use { out ->
                    out.write(magic)
                    out.writeUTF(originalName.take(60000))
                    // Reserve size and digest in a sidecar after streaming; header is rewritten below.
                    out.writeLong(0L)
                    out.writeUTF("PENDING")
                    val compressed = DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION), 64 * 1024, true)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = raw.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        compressed.write(buffer, 0, n)
                        count += n
                    }
                    compressed.finish()
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            // Rewrite fixed-size metadata header, preserving compressed payload.
            val bytes = target.readBytes()
            val header = java.io.ByteArrayOutputStream()
            DataOutputStream(header).use { h ->
                h.write(magic); h.writeUTF(originalName.take(60000)); h.writeLong(count); h.writeUTF(hash)
            }
            val oldHeaderSize = 4 + 2 + originalName.take(60000).toByteArray(Charsets.UTF_8).size + 8 + 2 + "PENDING".length
            val newHeader = header.toByteArray()
            val payloadOffset = oldHeaderSize
            require(payloadOffset <= bytes.size)
            target.outputStream().use { it.write(newHeader); it.write(bytes, payloadOffset, bytes.size - payloadOffset) }
            val info = ArchiveInfo(id, originalName, count, target.length(), hash)
            // Verify the archive immediately before exposing it in the gallery.
            verify(target, info)
            return info
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    fun restore(info: ArchiveInfo, destination: java.io.OutputStream) {
        val file = dir.resolve("${info.id}.sph")
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            require(input.readNBytes(4).contentEquals(magic)) { "Invalid SmartPhoto archive" }
            input.readUTF()
            input.readLong()
            input.readUTF()
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            val inflater = InflaterInputStream(input)
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = inflater.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
                destination.write(buffer, 0, n)
                count += n
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            require(count == info.originalBytes && hash == info.sha256) { "Integrity check failed" }
        }
    }

    private fun verify(file: java.io.File, info: ArchiveInfo) {
        val temp = java.io.ByteArrayOutputStream()
        restore(info, temp)
    }

    fun delete(info: ArchiveInfo): Boolean = dir.resolve("${info.id}.sph").delete()
}
