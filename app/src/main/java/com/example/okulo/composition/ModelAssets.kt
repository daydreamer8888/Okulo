package com.example.okulo.composition

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal class ModelAssets(private val context: Context) {
    fun file(name: String): File {
        val expected = checkNotNull(HASHES[name])
        val directory = File(context.noBackupFilesDir, "models").apply { mkdirs() }
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
            "detector-800.onnx" to "c71ccb64fd0221b9308d10f45e5b66182de09e96f214ffcfacf4e2a42578bdef",
            "detector-320.onnx" to "d5c15586b8c10bfd2b1f729766af1a754eb6b3fe1475d12ed5efd8b87439f339"
        )
    }
}

private const val COPY_BUFFER_SIZE = 65536
