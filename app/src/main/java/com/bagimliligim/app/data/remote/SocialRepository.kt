package com.bagimliligim.app.data.remote

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.bagimliligim.app.BuildConfig
import com.bagimliligim.app.data.local.SwipeDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class SocialProfile(val name: String, val code: String, val shareReels: Boolean, val shareSmoking: Boolean)
data class FriendRow(val id: String, val name: String, val status: String, val incoming: Boolean)
data class LeaderRow(val id: String, val name: String, val cigarettes: Int?, val costMicros: Long?,
    val swipes: Int?, val distanceMeters: Float?, val reduction: Int?, val trackedDays: Int)
data class SocialSnapshot(val profile: SocialProfile, val friends: List<FriendRow>, val leaders: List<LeaderRow>)

class SocialRepository(
    context: Context,
    private val database: SwipeDatabase,
    private val client: BackendTransport = BackendClient(),
    private val vault: SessionStore = SessionVault(context),
    private val clearProvider: suspend () -> Unit = {
        CredentialManager.create(context.applicationContext).clearCredentialState(ClearCredentialStateRequest())
    }
) {
    private var session = vault.load()
    val signedIn = MutableStateFlow(session != null)
    val configured get() = client.configured
    val googleConfigured get() = configured && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    private val mutex = Mutex()

    suspend fun authenticateGoogle(idToken: String, rawNonce: String) = mutex.withLock {
        require(idToken.length in 1..16_384 && rawNonce.matches(Regex("[A-Za-z0-9_-]{43}")))
        // Supabase verifies Google's signature, audience and the nonce before issuing a session.
        val response = JSONObject(client.request("/auth/v1/token?grant_type=id_token",
            JSONObject().put("provider", "google").put("id_token", idToken).put("nonce", rawNonce)))
        store(response)
    }
    private fun store(json: JSONObject) {
        val newSession = AccountSession(json.getString("access_token"), json.getString("refresh_token"),
            System.currentTimeMillis() + json.getLong("expires_in") * 1000, json.getJSONObject("user").getString("id"))
        require(newSession.accessToken.isNotBlank() && newSession.refreshToken.isNotBlank() &&
            newSession.userId.isNotBlank() && json.getLong("expires_in") in 1..604_800)
        vault.save(newSession)
        session = newSession
        signedIn.value = true
    }
    private suspend fun token(force: Boolean = false): String {
        val current = checkNotNull(session) { "Önce hesabına giriş yap." }
        if (force || current.expiresAt < System.currentTimeMillis() + 60_000) {
            store(JSONObject(client.request("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", current.refreshToken))))
        }
        return checkNotNull(session).accessToken
    }
    private suspend fun rpc(action: String, payload: JSONObject = JSONObject()): JSONObject {
        val request = JSONObject().put("action", action).put("payload", payload)
        return try { JSONObject(client.request("/rest/v1/rpc/social_action", request, token())) }
        catch (failure: BackendException) {
            if (failure.status != 401) throw failure
            JSONObject(client.request("/rest/v1/rpc/social_action", request, token(true)))
        }
    }

    suspend fun snapshot(brandId: String?, metric: String): SocialSnapshot = mutex.withLock {
        val json = rpc("snapshot", JSONObject().put("brand", brandId ?: JSONObject.NULL).put("metric", metric))
        val p = json.getJSONObject("profile")
        val friends = json.getJSONArray("friends").let { rows -> (0 until rows.length()).map { i ->
            val f = rows.getJSONObject(i)
            FriendRow(f.getString("id"), f.getString("name"), f.getString("status"), f.getBoolean("incoming"))
        } }
        val leaders = json.getJSONArray("leaders").let { rows -> (0 until rows.length()).map { i ->
            val l = rows.getJSONObject(i)
            LeaderRow(l.getString("id"), l.getString("name"), l.intOrNull("cigarettes"), l.longOrNull("cost_micros"),
                l.intOrNull("swipes"), if (l.isNull("distance_meters")) null else l.getDouble("distance_meters").toFloat(),
                l.intOrNull("reduction"), l.getInt("tracked_days"))
        } }
        SocialSnapshot(SocialProfile(p.getString("name"), p.getString("code"), p.getBoolean("share_reels"), p.getBoolean("share_smoking")), friends, leaders)
    }
    suspend fun profile(name: String, shareReels: Boolean, shareSmoking: Boolean) = mutex.withLock {
        rpc("profile", JSONObject().put("name", name.trim()).put("share_reels", shareReels).put("share_smoking", shareSmoking))
    }
    suspend fun friend(action: String, idOrCode: String) = mutex.withLock {
        require(action in setOf("request", "accept", "remove", "block"))
        rpc(action, JSONObject().put(if (action == "request") "code" else "id", idOrCode.trim()))
    }
    suspend fun upload(profile: SocialProfile) = mutex.withLock {
        if (!profile.shareReels && !profile.shareSmoking) return@withLock
        val today = LocalDate.now()
        val start = today.minusDays(13).toString()
        val end = today.toString()
        val swipes = database.swipeDao().observeDaily(start, end).first().associateBy { it.bucket }
        val smokeDays = database.smokingDao().buckets(start, end).first().associateBy { it.date }
        val smokeLogs = database.smokingDao().logs(start, end).first().groupBy { it.date }
        val days = JSONArray()
        for (offset in 0L..13L) {
            val date = today.minusDays(offset).toString()
            val swipe = swipes[date]
            val brands = JSONArray()
            (if (profile.shareSmoking) smokeLogs[date].orEmpty() else emptyList()).groupBy { it.brandId }.forEach { (id, rows) ->
                brands.put(JSONObject().put("id", id).put("name", rows.first().brandName)
                    .put("count", rows.sumOf { it.count }).put("cost_micros", rows.sumOf { it.costMicros }))
            }
            days.put(JSONObject().put("date", date).put("swipes", if (profile.shareReels) swipe?.swipes ?: 0 else 0)
                .put("distance_meters", if (profile.shareReels) swipe?.distanceMeters ?: 0f else 0f)
                .put("smoking_tracked", profile.shareSmoking && date in smokeDays).put("brands", brands))
        }
        // Server independently applies per-metric consent and replaces these dates transactionally.
        rpc("sync", JSONObject().put("days", days))
    }
    suspend fun deleteAccount() = mutex.withLock { rpc("delete_account"); clearSession() }
    suspend fun signOut() = mutex.withLock {
        // Clear local credentials even while offline. Public profile visibility is separately controlled.
        clearSession()
    }
    private suspend fun clearSession() {
        vault.clear(); session = null; signedIn.value = false
        try { withTimeoutOrNull(5_000) { clearProvider() } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* Local sign-out succeeds even if the device's provider is unavailable. */ }
    }
    private fun JSONObject.intOrNull(key: String) = if (isNull(key)) null else getInt(key)
    private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)
}
