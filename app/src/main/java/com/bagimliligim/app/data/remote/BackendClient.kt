package com.bagimliligim.app.data.remote

import com.bagimliligim.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection

class BackendException(val status: Int, message: String) : IOException(message)

interface BackendTransport {
    val configured: Boolean
    suspend fun request(path: String, body: JSONObject? = null, token: String? = null, method: String = if (body == null) "GET" else "POST"): String
}

class BackendClient : BackendTransport {
    override val configured get() = BuildConfig.BACKEND_URL.isNotBlank() && BuildConfig.PUBLIC_KEY.isNotBlank()

    override suspend fun request(path: String, body: JSONObject?, token: String?, method: String): String = withContext(Dispatchers.IO) {
        check(configured) { "Çevrimiçi servis henüz bağlanmadı." }
        require(path.startsWith('/') && !path.contains(".."))
        val connection = URI(BuildConfig.BACKEND_URL + path).toURL().openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("apikey", BuildConfig.PUBLIC_KEY)
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw BackendException(status, when (status) {
                401, 403 -> "Oturum doğrulanamadı. Yeniden giriş yap."
                409 -> "Bu işlem mevcut kayıtla çakışıyor."
                429 -> "Çok sık deneme yapıldı. Biraz sonra yeniden dene."
                else -> "Bağlantı tamamlanamadı. Kayıtların cihazında güvende."
            })
            connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 2_000_000) { "Yanıt beklenenden büyük." }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}
