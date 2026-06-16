package com.ai.phoneagent.net

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.provider.Settings
import com.ai.phoneagent.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.casdoor.Casdoor
import org.casdoor.CasdoorConfig
import java.util.UUID
import kotlin.coroutines.resume

/**
 * 维表统一登录入口。
 *
 * 类名暂时保留为 AriesOidcAuthManager，减少设置页和 Koin 的改动面；
 * 内部流程已经切到 Casdoor Android SDK + 维表 token-exchange。
 */
class AriesOidcAuthManager(
    private val application: Application,
) {
    data class AuthResult(
        val success: Boolean,
        val accessToken: String = "",
        val refreshToken: String = "",
        val teamIds: List<String> = emptyList(),
        val displayName: String = "",
        val message: String = "",
        val casdoorAccessToken: String = "",
        val casdoorIdToken: String = "",
    )

    suspend fun signIn(activity: Activity): AuthResult {
        val configError = validateCasdoorConfig()
        if (configError != null) return AuthResult(success = false, message = configError)

        val casdoor = Casdoor(casdoorConfig())
        val authUrl =
            runCatching {
                casdoor.getSignInUrl(BuildConfig.CASDOOR_SCOPE.ifBlank { "openid profile email" }, loginState())
            }.getOrElse { error ->
                return AuthResult(
                    success = false,
                    message = error.message?.trim().orEmpty().ifBlank { "Casdoor 登录地址生成失败" },
                )
            }

        val oauthResult =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    CasdoorOAuthActivity.pendingSession =
                        CasdoorOAuthActivity.PendingSession(
                            redirectUri = BuildConfig.CASDOOR_REDIRECT_URI,
                            completion = { result ->
                                if (continuation.isActive) {
                                    continuation.resume(result)
                                }
                            },
                        )
                    activity.startActivity(
                        Intent(activity, CasdoorOAuthActivity::class.java)
                            .putExtra(CasdoorOAuthActivity.EXTRA_AUTH_URL, authUrl),
                    )
                    continuation.invokeOnCancellation {
                        CasdoorOAuthActivity.pendingSession = null
                    }
                }
            }
        if (!oauthResult.success) {
            return AuthResult(success = false, message = oauthResult.message)
        }

        val accessTokenResponse =
            withContext(Dispatchers.IO) {
                runCatching { casdoor.requestOauthAccessToken(oauthResult.code) }
            }.getOrElse { error ->
                return AuthResult(
                    success = false,
                    message = error.message?.trim().orEmpty().ifBlank { "Casdoor token 获取失败" },
                )
            }
        val casdoorAccessToken = accessTokenResponse.accessToken.orEmpty()
        val casdoorIdToken = accessTokenResponse.idToken.orEmpty()
        if (casdoorAccessToken.isBlank() && casdoorIdToken.isBlank()) {
            return AuthResult(
                success = false,
                message = accessTokenResponse.error?.takeIf(String::isNotBlank)
                    ?: "Casdoor 未返回有效 token",
            )
        }

        val dimensResult =
            withContext(Dispatchers.IO) {
                DimensAuthClient.exchangeCasdoorToken(
                    accessToken = casdoorAccessToken,
                    idToken = casdoorIdToken,
                    deviceId = deviceId(),
                )
            }
        if (!dimensResult.success) {
            return AuthResult(
                success = false,
                message = dimensResult.message.ifBlank { "维表 token 换取失败" },
                casdoorAccessToken = casdoorAccessToken,
                casdoorIdToken = casdoorIdToken,
            )
        }

        val displayName =
            withContext(Dispatchers.IO) {
                runCatching {
                    casdoor.getUserInfo(casdoorAccessToken).let { user ->
                        user.nickname.orEmpty()
                            .ifBlank { user.name.orEmpty() }
                            .ifBlank { user.email.orEmpty() }
                    }
                }.getOrDefault("")
            }

        return AuthResult(
            success = true,
            accessToken = dimensResult.token,
            refreshToken = dimensResult.refreshToken,
            teamIds = dimensResult.teamIds,
            displayName = displayName.ifBlank { "维表账号" },
            casdoorAccessToken = casdoorAccessToken,
            casdoorIdToken = casdoorIdToken,
        )
    }

    suspend fun signOut(): String? = null

    suspend fun getApiAccessToken(): AuthResult =
        AuthResult(success = false, message = "维表登录已过期，请重新登录")

    private fun casdoorConfig(): CasdoorConfig =
        CasdoorConfig(
            BuildConfig.CASDOOR_CLIENT_ID,
            BuildConfig.CASDOOR_ORGANIZATION_NAME,
            BuildConfig.CASDOOR_REDIRECT_URI,
            BuildConfig.CASDOOR_ENDPOINT.trimEnd('/'),
            BuildConfig.CASDOOR_APP_NAME,
        )

    private fun validateCasdoorConfig(): String? {
        val missing =
            listOfNotNull(
                "casdoor.clientId".takeIf { BuildConfig.CASDOOR_CLIENT_ID.isBlank() },
                "casdoor.organizationName".takeIf { BuildConfig.CASDOOR_ORGANIZATION_NAME.isBlank() },
                "casdoor.appName".takeIf { BuildConfig.CASDOOR_APP_NAME.isBlank() },
                "casdoor.redirectUri".takeIf { BuildConfig.CASDOOR_REDIRECT_URI.isBlank() },
                "casdoor.endpoint".takeIf { BuildConfig.CASDOOR_ENDPOINT.isBlank() },
            )
        return if (missing.isEmpty()) {
            null
        } else {
            "Casdoor 配置不完整：${missing.joinToString(", ")}"
        }
    }

    private fun deviceId(): String {
        val androidId =
            runCatching {
                Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull().orEmpty()
        return androidId.ifBlank { "phone-ai-${UUID.randomUUID()}" }
    }

    private fun loginState(): String =
        "phone-ai-${System.currentTimeMillis()}"
}
