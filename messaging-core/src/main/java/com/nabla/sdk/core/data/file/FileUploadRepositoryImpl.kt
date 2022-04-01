package com.nabla.sdk.core.data.file

import android.content.Context
import com.nabla.sdk.core.data.helper.toAndroidUri
import com.nabla.sdk.core.domain.boundary.FileUploadRepository
import com.nabla.sdk.core.domain.entity.Id
import com.nabla.sdk.core.domain.entity.Uri
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream

internal class FileUploadRepositoryImpl constructor(
    private val fileService: FileService,
    appContext: Context
) : FileUploadRepository {

    private val contentResolver = appContext.contentResolver

    override suspend fun uploadFile(localPath: Uri): Id {
        val fileInputStream = contentResolver.openInputStream(localPath.toAndroidUri())
            ?: throw IOException("Unable to open input stream from uri: $localPath")
        val mimeType = contentResolver.getType(localPath.toAndroidUri())
        fileInputStream.use { inputStream ->
            val response = fileService.upload(
                file = MultipartBody.Part.createFormData(
                    "file",
                    localPath.toAndroidUri().lastPathSegment,
                    buildUploadRequestBody(inputStream, mimeType)
                ),
            )
            return Id(response.first())
        }
    }

    private fun buildUploadRequestBody(
        inputStream: InputStream,
        mimeType: String?,
    ): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return mimeType?.toMediaTypeOrNull()
            }

            override fun writeTo(sink: BufferedSink) {
                sink.writeAll(inputStream.source())
            }
        }
    }
}