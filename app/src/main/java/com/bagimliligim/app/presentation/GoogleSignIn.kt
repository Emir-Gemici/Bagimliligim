package com.bagimliligim.app.presentation

import android.app.Activity
import android.content.MutableContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.bagimliligim.app.BuildConfig
import com.bagimliligim.app.domain.SignInNonce
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** Tokens must never appear in generated toString output. */
class GoogleIdentity(val idToken: String, val nonce: String)
class GoogleSignInFailure(message: String) : Exception(message)

/** Called only from an explicit button in a composition-owned coroutine. */
suspend fun requestGoogleIdentity(activity: Activity): GoogleIdentity? {
    if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
        throw GoogleSignInFailure("Bu sürümde Google girişi henüz yapılandırılmadı.")
    }
    val nonce = SignInNonce.create()
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setNonce(nonce.sha256).build()
    try {
        val result = CredentialManager.create(activity.applicationContext).getCredential(
            context = MutableContextWrapper(activity),
            request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        )
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw GoogleSignInFailure("Google hesabı doğrulanamadı. Yeniden dene.")
        }
        return GoogleIdentity(GoogleIdTokenCredential.createFrom(credential.data).idToken, nonce.raw)
    } catch (_: GetCredentialCancellationException) {
        return null
    } catch (_: NoCredentialException) {
        throw GoogleSignInFailure("Google hesabı bulunamadı. Telefonuna bir Google hesabı ekleyip yeniden dene.")
    } catch (_: GetCredentialProviderConfigurationException) {
        throw GoogleSignInFailure("Google Play Hizmetleri kullanılamıyor. Güncelleyip yeniden dene.")
    } catch (_: GoogleIdTokenParsingException) {
        throw GoogleSignInFailure("Google yanıtı okunamadı. Uygulamayı güncelleyip yeniden dene.")
    } catch (_: GetCredentialException) {
        throw GoogleSignInFailure("Google ile bağlantı kurulamadı. İnternet bağlantını kontrol edip yeniden dene.")
    }
}
