package dev.codex.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class UpdateAuthTest {
    private val source = URI("https://relay.example.test/desktop-remote-updates/test/android/latest.json")
    private val apk = URI("https://relay.example.test/desktop-remote-updates/test/android/android-0.1.0-alpha.5.apk")
    private val token = "a".repeat(64)

    private fun rejected(code: String, action: () -> Unit) {
        try { action(); fail("expected $code") }
        catch (error: UpdateFailure) { assertEquals(code, error.code) }
    }

    @Test fun sameHttpsOriginAndComponentDirectoryAreRequired() {
        val identity = requireUpdateScope("https://relay.example.test:443/", "admin", token, source, apk, true)
        assertTrue(identity.matches("https://relay.example.test:443/", "admin", token))
        requireUpdateScope("https://relay.example.test", "admin", token, source, source)
        rejected("ota_source_mismatch") {
            requireUpdateScope("https://relay.example.test:8443", "admin", token, source, apk, true)
        }
        rejected("ota_source_mismatch") {
            requireUpdateScope("http://relay.example.test", "admin", token, source, apk, true)
        }
        rejected("ota_source_mismatch") {
            requireUpdateScope("https://other.example.test", "admin", token, source, apk, true)
        }
        rejected("ota_source_mismatch") {
            requireUpdateScope("https://relay.example.test", "admin", token, source,
                URI("https://relay.example.test/desktop-remote-updates/test/windows/package.zip"), true)
        }
    }

    @Test fun emptyOrMalformedLoginCannotAuthorizeRequest() {
        rejected("ota_login_required") { requireUpdateScope("", "admin", token, source, source) }
        rejected("ota_login_required") { requireUpdateScope("https://relay.example.test", "", token, source, source) }
        rejected("ota_login_required") { requireUpdateScope("https://relay.example.test", "admin", "", source, source) }
        rejected("ota_login_required") {
            requireUpdateScope("https://relay.example.test", "admin\r\nInjected: yes", token, source, source)
        }
    }

    @Test fun trustCannotPointAtAnotherComponentOrOrigin() {
        rejected("ota_source_mismatch") {
            requireUpdateScope("https://relay.example.test", "admin", token,
                URI("https://relay.example.test/desktop-remote-updates/test/windows/latest.json"), apk, true)
        }
        rejected("ota_source_mismatch") {
            requireUpdateScope("https://relay.example.test", "admin", token,
                URI("https://updates.example.test/desktop-remote-updates/test/android/latest.json"), apk, true)
        }
        rejected("ota_source_mismatch") {
            requireUpdateScope("https://relay.example.test", "admin", token,
                URI("http://relay.example.test/desktop-remote-updates/test/android/latest.json"), apk, true)
        }
    }

    @Test fun accountOrTokenSwitchInvalidatesOldOfferBindingWithoutExposingToken() {
        val old = requireUpdateScope("https://relay.example.test", "admin", token, source, apk, true)
        val next = requireUpdateScope("https://relay.example.test", "other", "b".repeat(64), source, apk, true)
        assertFalse(old.matches("https://relay.example.test", "other", "b".repeat(64)))
        assertNotEquals(old.binding(), next.binding())
        assertFalse(old.toString().contains(token))
        assertEquals("UpdateIdentity(redacted)", old.toString())
    }

    @Test fun authorizedRequestUsesOnlyHeadersAndNeverUrlCredentials() {
        val identity = requireUpdateScope("https://relay.example.test", "admin", token, source, apk, true)
        val connection = object : HttpURLConnection(URL(apk.toString())) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
        }
        identity.authorize(connection)
        assertEquals("Bearer $token", connection.getRequestProperty("Authorization"))
        assertEquals("account", connection.getRequestProperty("X-Desktop-Remote-Principal"))
        assertFalse(connection.url.toString().contains(token))
    }

    @Test fun sourceAndPackagePathsMatchPrivateRelayRouteExactly() {
        val stable = URI("https://relay.example.test/desktop-remote-updates/stable/android/latest.json")
        val stableApk = URI("https://relay.example.test/desktop-remote-updates/stable/android/android-123456789.1.2.apk")
        requireUpdateScope("https://relay.example.test", "admin", token, stable, stableApk, true)
        val invalidSources = listOf(
            "https://relay.example.test/prefix/desktop-remote-updates/test/android/latest.json",
            "https://relay.example.test/desktop-remote-updates/beta/android/latest.json",
            "https://relay.example.test/desktop-remote-updates/test/android/sub/latest.json",
            "https://relay.example.test/desktop-remote-updates/test/android/latest.json?x=1",
            "https://relay.example.test/desktop-remote-updates/test/android/%6catest.json",
        )
        invalidSources.forEach { value -> rejected("ota_source_mismatch") {
            requireUpdateScope("https://relay.example.test", "admin", token, URI(value), URI(value))
        } }
        val invalidPackages = listOf(
            "https://relay.example.test/desktop-remote-updates/stable/android/android-1.2.3.apk",
            "https://relay.example.test/desktop-remote-updates/test/android/sub/android-1.2.3.apk",
            "https://relay.example.test/desktop-remote-updates/test/android/android-1234567890.1.2.apk",
            "https://relay.example.test/desktop-remote-updates/test/android/android-1.2.3-beta.1.apk",
            "https://relay.example.test/desktop-remote-updates/test/android/android-1.2.3-alpha.1234567890.apk",
            "https://relay.example.test/desktop-remote-updates/test/android/android-1.2.3.apk?x=1",
            "https://relay.example.test/desktop-remote-updates/test/android/%61ndroid-1.2.3.apk",
            source.toString(),
        )
        invalidPackages.forEach { value -> rejected("ota_source_mismatch") {
            requireUpdateScope("https://relay.example.test", "admin", token, source, URI(value), true)
        } }
    }

    @Test fun relayLoginMustUseOriginRoot() {
        listOf("https://relay.example.test/relay", "https://relay.example.test/%2F",
            "https://relay.example.test/?x=1").forEach { value -> rejected("ota_source_mismatch") {
            requireUpdateScope(value, "admin", token, source, source)
        } }
    }
}
