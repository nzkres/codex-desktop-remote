package dev.codex.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject

internal data class QuestionDraft(val answers: Map<String, String> = emptyMap(), val other: Set<String> = emptySet(),
    val state: String = "pending", val notice: String = "")

/** The scope includes the task endpoint and account. A question revision is a separate draft. */
class QuestionDraftStore internal constructor(private val prefs: SharedPreferences) {
    private var records by mutableStateOf(prefs.all.map { (key, raw) ->
        require(raw is String) { "问题草稿记录损坏，请检查手机存储。" }
        val value = JSONObject(raw)
        val answers = value.getJSONObject("answers")
        val other = value.getJSONArray("other")
        val state = value.getString("state")
        val notice = value.getString("notice")
        require(state in setOf("pending", "submitting", "unknown", "accepted", "closed", "desktop_required", "expired")) {
            "问题草稿状态损坏，请检查手机存储。"
        }
        val savedAnswers = answers.keys().asSequence().associateWith { id ->
            require(answers.get(id) is String) { "问题草稿答案损坏，请检查手机存储。" }
            answers.getString(id)
        }
        val savedOther = (0 until other.length()).map { index ->
            require(other.get(index) is String) { "问题草稿选项损坏，请检查手机存储。" }
            other.getString(index)
        }.toSet()
        key to QuestionDraft(savedAnswers, savedOther, if (state == "submitting") "unknown" else state, notice)
    }.toMap())

    private fun key(threadId: String, request: QuestionRequest): String = "$threadId:${request.id}:${request.revision}"
    internal fun read(threadId: String, request: QuestionRequest): QuestionDraft = records[key(threadId, request)] ?: QuestionDraft()
    internal fun save(threadId: String, request: QuestionRequest, draft: QuestionDraft) {
        val key = key(threadId, request)
        val json = JSONObject().put("answers", JSONObject(draft.answers)).put("other", org.json.JSONArray(draft.other.toList()))
            .put("state", draft.state).put("notice", draft.notice)
        check(prefs.edit().putString(key, json.toString()).commit()) { "无法加密保存问题答案，请稍后重试" }
        records = records + (key to draft)
    }
    internal fun reconcile(threadId: String, requests: List<QuestionRequest>) {
        requests.forEach { request ->
            val draft = read(threadId, request)
            if (request.status in setOf("accepted", "closed", "expired") && draft.state != request.status)
                save(threadId, request, draft.copy(state = request.status, notice = request.notice))
        }
    }
    companion object {
        internal fun open(context: Context, scope: String): QuestionDraftStore {
            require(scope.matches(Regex("[a-f0-9]{64}")))
            return QuestionDraftStore(EncryptedSharedPreferences.create("question-drafts-$scope",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM))
        }
    }
}
