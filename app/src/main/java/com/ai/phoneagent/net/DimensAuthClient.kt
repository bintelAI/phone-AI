package com.ai.phoneagent.net

import com.ai.phoneagent.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object DimensAuthClient {
    private const val TOKEN_EXCHANGE_PATH = "/app/user/login/casdoor/token-exchange"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class TokenExchangeResult(
        val success: Boolean,
        val token: String = "",
        val refreshToken: String = "",
        val teamIds: List<String> = emptyList(),
        val expire: Int = 0,
        val refreshExpire: Int = 0,
        val message: String = "",
    )

    fun parseTokenExchangeResponse(raw: String, httpCode: Int): TokenExchangeResult {
        if (raw.isBlank()) {
            return TokenExchangeResult(
                success = false,
                message = "维表登录响应为空 HTTP $httpCode",
            )
        }

        val root =
            runCatching { json.parseToJsonElement(raw).jsonObject }
                .getOrElse { error ->
                    return TokenExchangeResult(
                        success = false,
                        message = "维表登录响应解析失败 HTTP $httpCode: ${error.message.orEmpty()}",
                    )
                }
        val code = root["code"]?.jsonPrimitive?.intOrNull
        val message = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val data = root["data"] as? JsonObject
        val token = data?.get("token")?.jsonPrimitive?.contentOrNull.orEmpty()
        val okCode = code == null || code == 1000

        if (httpCode in 200..299 && okCode && token.isNotBlank()) {
            return TokenExchangeResult(
                success = true,
                token = token,
                refreshToken = data?.get("refreshToken")?.jsonPrimitive?.contentOrNull.orEmpty(),
                teamIds = data?.teamIds().orEmpty(),
                expire = data?.get("expire")?.jsonPrimitive?.intOrNull ?: 0,
                refreshExpire = data?.get("refreshExpire")?.jsonPrimitive?.intOrNull ?: 0,
                message = message,
            )
        }

        return TokenExchangeResult(
            success = false,
            message = message.ifBlank { "维表登录失败 HTTP $httpCode" },
        )
    }

    fun exchangeCasdoorToken(
        accessToken: String,
        idToken: String,
        deviceId: String,
        baseUrl: String = BuildConfig.DIMENS_BASE_URL,
        client: OkHttpClient = defaultClient(),
    ): TokenExchangeResult {
        val normalizedAccessToken = accessToken.trim()
        val normalizedIdToken = idToken.trim()
        if (normalizedAccessToken.isBlank() && normalizedIdToken.isBlank()) {
            return TokenExchangeResult(success = false, message = "Casdoor token 为空")
        }

        val bodyJson =
            buildJsonObject {
                put("accessToken", normalizedAccessToken)
                put("idToken", normalizedIdToken)
                put("tokenType", "access_token")
                put("platform", "android")
                put("deviceId", deviceId.trim())
                put("clientType", "phone_ai")
            }.toString()
        val request =
            Request.Builder()
                .url(normalizeBaseUrl(baseUrl) + TOKEN_EXCHANGE_PATH.removePrefix("/"))
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                parseTokenExchangeResponse(response.body?.string().orEmpty(), response.code)
            }
        }.getOrElse { error ->
            TokenExchangeResult(
                success = false,
                message = error.message?.trim().orEmpty().ifBlank { "维表登录请求失败" },
            )
        }
    }

    private fun JsonObject.teamIds(): List<String> =
        this["teamIds"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }
            .orEmpty()

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().ifBlank { "http://10.0.2.2:8001" }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun defaultClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
}
