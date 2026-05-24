package com.verdantgem.ledger.data.remote

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavClient @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun backup(
        url: String,
        user: String,
        pass: String,
        file: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url(url)
                .put(file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .header("Authorization", credential)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Upload failed: ${response.code}")
            }
        }
    }

    suspend fun mkdir(
        dirUrl: String,
        user: String,
        pass: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url(dirUrl)
                .method("MKCOL", null)
                .header("Authorization", credential)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 405) {
                    throw Exception("MKCOL failed: ${response.code}")
                }
            }
        }
    }

    suspend fun testConnection(
        url: String,
        user: String,
        pass: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val credential = Credentials.basic(user, pass)
            val baseDirUrl = if (url.endsWith("/")) "$url${Uri.encode("简记账")}/" else "$url/${Uri.encode("简记账")}/"

            val mkcolRequest = Request.Builder()
                .url(baseDirUrl)
                .method("MKCOL", null)
                .header("Authorization", credential)
                .build()
            client.newCall(mkcolRequest).execute().close()

            val propfindRequest = Request.Builder()
                .url(baseDirUrl)
                .method("PROPFIND", null)
                .header("Authorization", credential)
                .build()
            client.newCall(propfindRequest).execute().use { response ->
                val success = response.isSuccessful || response.code == 207
                if (!success) throw Exception("HTTP ${response.code}")
            }
        }
    }

    suspend fun restore(
        url: String,
        user: String,
        pass: String,
        targetFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", credential)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
                response.body?.byteStream()?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Empty response body")
            }
            Unit
        }
    }

    suspend fun listFiles(
        baseUrl: String,
        user: String,
        pass: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url(baseUrl)
                .method("PROPFIND", null)
                .header("Authorization", credential)
                .header("Depth", "1")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 207) {
                    throw Exception("List files failed: ${response.code}")
                }
                val body = response.body?.string() ?: throw Exception("Empty response")

                val hrefPattern = Pattern.compile("<D:href>([^<]+)</D:href>")
                val matcher = hrefPattern.matcher(body)
                val files = mutableListOf<String>()
                while (matcher.find()) {
                    matcher.group(1)?.let { files.add(it) }
                }
                files
            }
        }
    }

    suspend fun deleteFile(
        url: String,
        user: String,
        pass: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", credential)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Delete failed: ${response.code}")
            }
        }
    }

    /**
     * 发送 HEAD 请求获取远程文件响应头，用于 ETag 比对判断文件是否变更
     */
    suspend fun head(
        url: String,
        user: String,
        pass: String
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val credential = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url(url)
                .head()
                .header("Authorization", credential)
                .build()

            client.newCall(request).execute().use { response ->
                val headers = mutableMapOf<String, String>()
                response.headers.forEach { (name, value) ->
                    headers[name.lowercase()] = value
                }
                headers
            }
        }
    }
}
