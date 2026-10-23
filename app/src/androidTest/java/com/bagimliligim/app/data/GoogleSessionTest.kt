package com.bagimliligim.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bagimliligim.app.data.local.SwipeDatabase
import com.bagimliligim.app.data.remote.*
import com.bagimliligim.app.domain.SignInNonce
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleSessionTest {
    private class MemoryStore : SessionStore {
        var session: AccountSession? = null
        override fun load() = session
        override fun save(session: AccountSession) { this.session = session }
        override fun clear() { session = null }
    }

    private class Transport : BackendTransport {
        override val configured = true
        var failure: Exception? = null
        var calls = 0
        var path = ""
        var body = JSONObject()
        var response = """{"access_token":"server-access","refresh_token":"server-refresh","expires_in":3600,"user":{"id":"server-user"}}"""
        override suspend fun request(path: String, body: JSONObject?, token: String?, method: String): String {
            calls++
            this.path = path
            this.body = requireNotNull(body)
            assertNull(token)
            assertEquals("POST", method)
            failure?.let { throw it }
            return response
        }
    }

    @Test fun googleProofIsExchangedBeforePersistingAndSignOutClearsBothSessions() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SwipeDatabase::class.java).build()
        val store = MemoryStore()
        val transport = Transport()
        var providerCleared = false
        val repository = SocialRepository(context, database, transport, store, { providerCleared = true })
        try {
            val nonce = SignInNonce.create()
            repository.authenticateGoogle("google-proof-for-test", nonce.raw)
            assertEquals("/auth/v1/token?grant_type=id_token", transport.path)
            assertEquals("google", transport.body.getString("provider"))
            assertEquals(nonce.raw, transport.body.getString("nonce"))
            assertNotEquals(nonce.sha256, transport.body.getString("nonce"))
            assertEquals("google-proof-for-test", transport.body.getString("id_token"))
            assertEquals(3, transport.body.length())
            assertEquals("server-access", store.session?.accessToken)
            assertEquals("server-refresh", store.session?.refreshToken)
            assertTrue(repository.signedIn.value)
            repository.signOut()
            assertNull(store.session)
            assertFalse(repository.signedIn.value)
            assertTrue(providerCleared)
        } finally { database.close() }
    }

    @Test fun rejectedProofAndMalformedServerSessionsNeverSignIn() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SwipeDatabase::class.java).build()
        val store = MemoryStore()
        val transport = Transport()
        val repository = SocialRepository(context, database, transport, store, {})
        try {
            transport.failure = BackendException(400, "Rejected nonce")
            assertTrue(runCatching { repository.authenticateGoogle("invalid-proof", SignInNonce.create().raw) }.isFailure)
            assertFalse(repository.signedIn.value)
            assertNull(store.session)
            transport.failure = null
            transport.response = """{"access_token":"","refresh_token":"refresh","expires_in":3600,"user":{"id":"user"}}"""
            assertTrue(runCatching { repository.authenticateGoogle("proof", SignInNonce.create().raw) }.isFailure)
            assertFalse(repository.signedIn.value)
            assertNull(store.session)
            val previousCalls = transport.calls
            assertTrue(runCatching { repository.authenticateGoogle("proof", "") }.isFailure)
            assertEquals(previousCalls, transport.calls)
        } finally { database.close() }
    }

    @Test fun unavailableGoogleProviderDoesNotPreventOfflineSignOut() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, SwipeDatabase::class.java).build()
        val store = MemoryStore().apply { session = AccountSession("access", "refresh", Long.MAX_VALUE, "user") }
        val transport = Transport()
        val repository = SocialRepository(context, database, transport, store, { error("Provider unavailable") })
        try {
            repository.signOut()
            assertNull(store.session)
            assertFalse(repository.signedIn.value)
            assertEquals(0, transport.calls)
        } finally { database.close() }
    }
}
