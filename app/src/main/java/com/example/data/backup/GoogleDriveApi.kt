package com.example.data.backup

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real Google Drive REST API v3 client using the dedicated appDataFolder scope
 * (https://www.googleapis.com/auth/drive.appdata).
 * Requires a valid OAuth 2.0 access token obtained via Google Play Services AuthorizationClient.
 */
class GoogleDriveApi(private val moshi: Moshi) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val listAdapter = moshi.adapter(DriveFileListResponse::class.java)
    private val fileAdapter = moshi.adapter(DriveFileItem::class.java)

    suspend fun uploadToAppDataFolder(
        accessToken: String,
        fileName: String,
        encryptedJsonBytes: ByteArray
    ): DriveFileItem = withContext(Dispatchers.IO) {
        val boundary = "hesabaty_boundary_${System.currentTimeMillis()}"
        val metadataJson = """{"name":"$fileName","parents":["appDataFolder"],"mimeType":"application/json"}"""

        val multipartBody = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson)
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/octet-stream\r\n\r\n")
        }.toByteArray(Charsets.UTF_8) + encryptedJsonBytes + "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,modifiedTime,size")
            .header("Authorization", "Bearer $accessToken")
            .post(multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("فشل الرفع إلى Google Drive (HTTP ${resp.code}): ${bodyStr.take(180)}")
            }
            fileAdapter.fromJson(bodyStr)
                ?: throw IOException("استجابة غير متوقعة من Google Drive")
        }
    }

    suspend fun listAppDataBackups(accessToken: String, tenantId: String): List<DriveFileItem> = withContext(Dispatchers.IO) {
        val query = "name contains 'hesabaty_${tenantId.take(16)}' and trashed = false"
        val encodedQ = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$encodedQ&orderBy=modifiedTime%20desc&fields=files(id,name,modifiedTime,size)"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("فشل جلب قائمة النسخ من Google Drive (HTTP ${resp.code})")
            }
            listAdapter.fromJson(bodyStr)?.files ?: emptyList()
        }
    }

    suspend fun downloadFileBytes(accessToken: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("فشل تنزيل النسخة الاحتياطية من Google Drive (HTTP ${resp.code})")
            }
            resp.body?.bytes() ?: throw IOException("الملف فارغ")
        }
    }
}
