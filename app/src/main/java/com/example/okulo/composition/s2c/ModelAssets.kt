package com.example.okulo.composition.s2c

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal class ModelAssets(private val context: Context) {
    fun file(name: String): File {
        val expected = checkNotNull(HASHES[name])
        val root = File(context.noBackupFilesDir, "models")
        return prepare(name, expected, root)
    }

    private fun prepare(name: String, expected: String, directory: File): File {
        directory.mkdirs()
        val target = File(directory, "$expected.onnx")
        if (!target.exists()) {
            val partial = File(directory, "$expected.partial")
            try {
                val actual = copyAndDigest(name, partial)
                check(actual == expected) { "模型文件校验失败，请重新安装验证版" }
                check(partial.renameTo(target)) { "模型准备失败，请检查剩余存储空间" }
            } finally {
                partial.delete()
            }
        }
        return target
    }

    private fun copyAndDigest(name: String, destination: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open("models/$name").use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var count = input.read(buffer)
                while (count != -1) {
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        val HASHES = mapOf(
            "s2c.onnx" to "e9d5b6518a878f6873f00482bd48c96c82980ef22e7715fb0cb73a066156348a",
            "yolo26n.onnx" to "b6e38fcd4bd123458e5f2d2e611107547b7587634f6ef46e328b9213ddad8e7d"
        )
    }
}

private const val COPY_BUFFER_SIZE = 65536
