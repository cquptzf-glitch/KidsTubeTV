package dev.kidstubetv.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Filesystem-only transactional profile installer, testable on a JVM.
 *
 * Do not rename the current config out of the way before saving the new one:
 * a crash in between would leave the application with no usable profile.
 */
internal class ProfileFileStore(private val profile: File) {
    private val dir: Path get() = profile.toPath().parent
    private val target: Path get() = profile.toPath()
    private val backup: Path get() = dir.resolve("config-backup.yaml")
    private val newFile: Path get() = dir.resolve("config-new.tmp")
    private val newBackup: Path get() = dir.resolve("config-backup-new.tmp")

    fun install(content: ByteArray) {
        require(content.isNotEmpty()) { "Clash 配置不能为空" }
        Files.createDirectories(dir)
        try {
            writeSync(newFile.toFile(), content)
            if (Files.isRegularFile(target)) {
                Files.copy(target, newBackup, REPLACE_EXISTING)
                replace(newBackup, backup)
            }
            replace(newFile, target)
        } finally {
            Files.deleteIfExists(newFile)
            Files.deleteIfExists(newBackup)
        }
    }

    /** A failed restore never removes the current or backup configuration. */
    fun restore(): Boolean {
        if (!Files.isRegularFile(backup)) return false
        Files.createDirectories(dir)
        return try {
            Files.copy(backup, newFile, REPLACE_EXISTING)
            replace(newFile, target)
            Files.deleteIfExists(backup)
            true
        } catch (_: Exception) {
            false
        } finally {
            Files.deleteIfExists(newFile)
        }
    }

    private fun writeSync(destination: File, contents: ByteArray) {
        FileOutputStream(destination).use { output ->
            output.write(contents)
            output.fd.sync()
        }
    }

    private fun replace(from: Path, to: Path) {
        try {
            Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            // On a filesystem without rename support, still keep the old
            // config in place until the replacement file is ready.
            Files.move(from, to, REPLACE_EXISTING)
        }
    }
}
