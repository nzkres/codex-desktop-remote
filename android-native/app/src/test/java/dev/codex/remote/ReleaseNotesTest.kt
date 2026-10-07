package dev.codex.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseNotesTest {
    private val history = """{
        "schema": 1, "component": "android", "name": "Android 手机端",
        "releases": [
            {"version":"0.1.0-alpha.2","versionCode":2,"date":"2026-10-07",
             "summary":"当前测试版","changes":[
               {"title":"版本说明","experience":"我的 → 版本与更新说明","verify":"打开并阅读"}],
             "limitations":["需手动安装"]},
            {"version":"0.1.0-alpha.1","versionCode":1,"date":"2026-10-06",
             "summary":"首版","changes":[],"limitations":[]}
        ]
    }""".trimIndent()

    @Test fun parsesCurrentAndOlderVersionsWithExperienceAndVerification() {
        val parsed = parseReleaseHistory(history, "0.1.0-alpha.2", 2)
        assertEquals("Android 手机端", parsed.name)
        assertEquals(listOf("0.1.0-alpha.2", "0.1.0-alpha.1"), parsed.releases.map { it.version })
        assertEquals("我的 → 版本与更新说明", parsed.releases.first().changes.single().experience)
        assertEquals("打开并阅读", parsed.releases.first().changes.single().verify)
        assertEquals(listOf("需手动安装"), parsed.releases.first().limitations)
    }

    @Test fun rejectsWrongInstalledVersionOrBrokenManifest() {
        assertThrows(IllegalArgumentException::class.java) {
            parseReleaseHistory(history, "0.1.0-alpha.1", 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseReleaseHistory(history, "0.1.0-alpha.2", 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseReleaseHistory(history.replace("\"component\": \"android\"", "\"component\": \"windows\""),
                "0.1.0-alpha.2", 2)
        }
        assertThrows(Exception::class.java) { parseReleaseHistory("not JSON", "0.1.0-alpha.2", 2) }
    }
}
