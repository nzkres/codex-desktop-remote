package dev.codex.remote

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class UpdateSecurityTest {
    companion object {
        private lateinit var pair: KeyPair
        @JvmStatic @BeforeClass fun keys() {
            pair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        }
    }
    private val now = 1_800_000_000L
    private fun trust(): ByteArray = JSONObject()
        .put("schema", 1).put("product", "codex-desktop-remote").put("channel", "test")
        .put("keys", JSONObject().put("test", "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getEncoder().encodeToString(pair.public.encoded) + "\n-----END PUBLIC KEY-----"))
        .put("sources", JSONObject().put("android", "https://example.test/desktop-remote-updates/test/android/latest.json"))
        .toString().toByteArray()
    private fun payload(): JSONObject = JSONObject()
        .put("schema", 1).put("product", "codex-desktop-remote").put("component", "android")
        .put("platform", "android").put("channel", "test").put("version", "0.1.0-alpha.3")
        .put("sequence", 3).put("issuedAt", now - 60).put("expiresAt", now + 3600)
        .put("url", "https://example.test/desktop-remote-updates/test/android/update.apk")
        .put("bytes", 1024).put("sha256", "a".repeat(64)).put("notes", "测试版")
        .put("compatibility", JSONArray().put("desktop-remote-v1")).put("dataSchema", 1)
        .put("rollbackCompatible", true).put("versionCode", 3)
        .put("packageId", "dev.codex.desktopremote.lite").put("minSdk", 26)
    private fun envelope(payload: String): ByteArray {
        val raw = payload.toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(pair.private); update(raw); sign()
        }
        return JSONObject().put("schema", 1).put("keyId", "test")
            .put("payload", Base64.getEncoder().encodeToString(raw))
            .put("signature", Base64.getEncoder().encodeToString(signature))
            .toString().toByteArray()
    }
    private fun verify(bytes: ByteArray, ledger: UpdateLedger = UpdateLedger(), trust: ByteArray = trust()) =
        verifyUpdate(bytes, trust, "0.1.0-alpha.2", 2, 26, ledger, now)
    private fun rejects(expected: String, action: () -> Unit) {
        try { action(); fail("expected $expected") }
        catch (error: UpdateFailure) { assertEquals(expected, error.code) }
    }
    @Test fun signedOfferAndLedger() {
        val (offer, ledger) = verify(envelope(payload().toString()))
        assertEquals("0.1.0-alpha.3", offer.version)
        assertEquals(3, ledger.sequence)
        assertEquals("a".repeat(64), ledger.versions[offer.version])
        assertEquals(offer.binding, ledger.binding)
    }
    @Test fun emptyTrustFailsClosed() {
        val empty = JSONObject().put("schema", 1).put("product", "codex-desktop-remote")
            .put("channel", "test").put("keys", JSONObject()).put("sources", JSONObject())
        rejects("ota_unconfigured") { verify(envelope(payload().toString()), trust = empty.toString().toByteArray()) }
    }
    @Test fun badSignatureAndDuplicateKeyFail() {
        val raw = payload().toString()
        val signed = JSONObject(String(envelope(raw)))
        signed.put("signature", Base64.getEncoder().encodeToString(ByteArray(384)))
        rejects("metadata_signature_invalid") { verify(signed.toString().toByteArray()) }
        rejects("metadata_duplicate_key") {
            verify(envelope(raw.dropLast(1) + ",\"version\":\"0.1.0-alpha.4\"}"))
        }
    }
    @Test fun replayAndSameSequenceDifferentPayloadFail() {
        val first = verify(envelope(payload().toString())).second
        rejects("metadata_replayed") { verify(envelope(payload().put("sequence", 2).toString()), first) }
        rejects("metadata_sequence_conflict") {
            verify(envelope(payload().put("notes", "changed").toString()), first)
        }
    }
    @Test fun wrongTargetUrlAndSdkFail() {
        rejects("metadata_url_invalid") {
            verify(envelope(payload().put("url", "https://other.test/update.apk").toString()))
        }
        rejects("metadata_incompatible") {
            verify(envelope(payload().put("minSdk", 27).toString()))
        }
        rejects("metadata_target_invalid") {
            verify(envelope(payload().put("packageId", "dev.codex.remote").toString()))
        }
    }
    @Test fun sameVersionCannotInstall() {
        rejects("update_not_newer") {
            verify(envelope(payload().put("version", "0.1.0-alpha.2").put("versionCode", 2).toString()))
        }
    }
    @Test fun expiredAndWrongChannelFail() {
        rejects("metadata_expired") {
            verify(envelope(payload().put("expiresAt", now - 1).toString()))
        }
        rejects("metadata_target_invalid") {
            verify(envelope(payload().put("channel", "stable").toString()))
        }
    }
}
