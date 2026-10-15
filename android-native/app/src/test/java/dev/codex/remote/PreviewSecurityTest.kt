package dev.codex.remote

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class PreviewSecurityTest {
    companion object {
        private val key = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        private val now = 1800000000L
        private val source = URI("https://example.invalid:9443/desktop-remote-previews/test/latest.json")
        private val publicPem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(key.public.encoded) + "\n-----END PUBLIC KEY-----"
        private val trust = JSONObject().put("schema",1).put("product","codex-desktop-remote").put("channel","test")
            .put("keys",JSONObject().put("fixture",publicPem)).put("sources",JSONObject().put("android", "https://example.invalid:9443/desktop-remote-updates/test/android/latest.json")).toString().toByteArray()
    }
    private fun payload(sequence: Long = 1) = JSONObject().put("schema",1).put("product","desktop-remote-ui-preview")
        .put("source",source.toString()).put("renderer",1).put("sequence",sequence).put("id","quota-motion")
        .put("title","额度动效").put("issuedAt",now-1).put("expiresAt",now+10000)
        .put("htmlSha256",previewSha("<div>fixture</div>".toByteArray())).put("html",Base64.getEncoder().encodeToString("<div>fixture</div>".toByteArray()))
    private fun envelope(text: String): ByteArray {
        val raw=text.toByteArray(); val signature=Signature.getInstance("SHA256withRSA").run { initSign(key.private);update(raw);sign() }
        return JSONObject().put("schema",1).put("keyId","fixture").put("payload",Base64.getEncoder().encodeToString(raw))
            .put("signature",Base64.getEncoder().encodeToString(signature)).toString().toByteArray()
    }
    private fun rejected(code: String, block: () -> Unit) {
        try { block(); fail("preview accepted") } catch(e: PreviewFailure) { assertEquals(code,e.code) }
    }
    @Test fun signedRevisionCanChangeWithoutChangingAppVersion() {
        val first=verifyPreview(envelope(payload().toString()),trust,source,PreviewLedger(),now)
        val second=verifyPreview(envelope(payload(2).put("title","第二次预览").toString()),trust,source,PreviewLedger(first.sequence,first.binding),now)
        assertEquals(2L,second.sequence);assertEquals("第二次预览",second.title)
        assertEquals("<div>fixture</div>",second.html)
        verifyPreview(envelope(payload(2).put("title","第二次预览").toString()),trust,source,PreviewLedger(second.sequence,second.binding),now)
    }
    @Test fun lowerRevisionAndSameRevisionConflictAreRejected() {
        val first=verifyPreview(envelope(payload(2).toString()),trust,source,PreviewLedger(),now)
        val ledger=PreviewLedger(first.sequence,first.binding)
        rejected("preview_replayed") { verifyPreview(envelope(payload(1).toString()),trust,source,ledger,now) }
        rejected("preview_sequence_conflict") { verifyPreview(envelope(payload(2).put("title","changed").toString()),trust,source,ledger,now) }
    }
    @Test fun signatureAndHtmlHashMustBothMatch() {
        val outer=JSONObject(String(envelope(payload().toString())));outer.put("signature",Base64.getEncoder().encodeToString(ByteArray(384)))
        rejected("preview_signature_invalid") { verifyPreview(outer.toString().toByteArray(),trust,source,PreviewLedger(),now) }
        rejected("preview_hash_invalid") { verifyPreview(envelope(payload().put("htmlSha256","0".repeat(64)).toString()),trust,source,PreviewLedger(),now) }
    }
    @Test fun credentialsCannotFollowAnotherOriginOrUserInfo() {
        rejected("preview_source_mismatch") { previewSource("https://other.invalid:9443",trust) }
        rejected("preview_source_mismatch") { previewSource("https://user@example.invalid:9443",trust) }
        assertEquals(source,previewSource("https://example.invalid:9443/",trust))
    }
    @Test fun expiryAndFutureIssueAreRejected() {
        rejected("preview_expired") { verifyPreview(envelope(payload().put("expiresAt",now).toString()),trust,source,PreviewLedger(),now) }
        rejected("preview_expired") { verifyPreview(envelope(payload().put("issuedAt",now+301).toString()),trust,source,PreviewLedger(),now) }
    }
    @Test fun incompatibleRendererAndOtherProductNeverExecute() {
        rejected("preview_renderer_unsupported") { verifyPreview(envelope(payload().put("renderer",2).toString()),trust,source,PreviewLedger(),now) }
        rejected("preview_target_invalid") { verifyPreview(envelope(payload().put("product","codex-desktop-remote").toString()),trust,source,PreviewLedger(),now) }
    }
    @Test fun oversizedAndDuplicateSignedDocumentsAreRejected() {
        rejected("preview_size_invalid") { verifyPreview(ByteArray(PREVIEW_MAX_ENVELOPE+1),trust,source,PreviewLedger(),now) }
        rejected("preview_document_invalid") { verifyPreview(("[".repeat(1000)+"]".repeat(1000)).toByteArray(),trust,source,PreviewLedger(),now) }
        val duplicate=payload().toString().dropLast(1)+",\"id\":\"duplicate\"}"
        rejected("preview_document_invalid") { verifyPreview(envelope(duplicate),trust,source,PreviewLedger(),now) }
    }
    @Test fun previewWrapperKeepsNetworkAndFramesDisabled() {
        val page=isolatedPreviewHtml("<div>preview</div>")
        assertTrue(page.contains("connect-src 'none'"));assertTrue(page.contains("frame-src 'none'"))
        assertTrue(page.contains("form-action 'none'"));assertTrue(page.contains("base-uri 'none'"))
        assertFalse(page.contains("Bearer"));assertFalse(page.contains("addJavascriptInterface"))
    }
}
