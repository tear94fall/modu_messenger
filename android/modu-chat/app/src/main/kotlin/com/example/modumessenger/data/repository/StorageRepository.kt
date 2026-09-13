package com.example.modumessenger.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.modumessenger.core.di.IoDispatcher
import com.example.modumessenger.core.network.safeCall
import com.example.modumessenger.data.api.StorageApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface StorageRepository {

    /** content Uri 를 올리고 서버가 저장한 파일 이름을 돌려준다. */
    suspend fun upload(uri: Uri): Result<String>

    /** 카메라에 넘길 촬영 결과 Uri(`filesDir/temp_images/temp_image.jpg`). */
    fun takePictureUri(): Uri
}

@Singleton
class StorageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageApi: StorageApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StorageRepository {

    override suspend fun upload(uri: Uri): Result<String> = withContext(ioDispatcher) {
        safeCall {
            val file = copyToCache(uri)
            try {
                val body = file.asRequestBody(MULTIPART.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData(PART_NAME, file.name, body)
                storageApi.upload(part).use { it.string() }.trim()
            } finally {
                file.delete()
            }
        }
    }

    override fun takePictureUri(): Uri {
        val dir = File(context.filesDir, TEMP_DIR).apply { mkdirs() }
        val file = File(dir, TEMP_IMAGE)
        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    /** Android 10+ 에서는 content Uri 를 그대로 파일로 못 읽으므로 캐시로 복사해서 올린다. */
    private fun copyToCache(uri: Uri): File {
        val name = displayName(uri) ?: "${System.currentTimeMillis()}.${extensionOf(uri)}"
        val target = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$name")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "파일을 열 수 없다: $uri" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
    }.getOrNull()

    private fun extensionOf(uri: Uri): String {
        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "jpg"
    }

    companion object {
        const val PART_NAME = "file"
        const val AUTHORITY = "com.example.modumessenger"
        const val TEMP_DIR = "temp_images"
        const val TEMP_IMAGE = "temp_image.jpg"
        private const val MULTIPART = "multipart/form-data"
    }
}
