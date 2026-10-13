package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class TaskTargetTest {
    @Test fun onlySelectedTargetRoutesTaskApis() {
        val target = TaskTarget("a".repeat(32), "电脑 A")
        assertTrue(target.isSelected)
        assertFalse(TaskTarget().isSelected)
        for (path in listOf("/api/projects", "/api/projects/project-a/sessions", "/api/activity", "/api/sessions", "/api/sessions/thread/mobile-sync?cursor=a&older=1",
            "/api/sessions/thread/messages", "/api/sessions/thread/signals", "/api/sessions/thread/stop",
            "/api/connector/update-status")) {
            assertEquals("/terminal-api/${target.id}$path", target.route(path))
            assertEquals(path, TaskTarget().route(path))
        }
        for (path in listOf("/auth/login", "/auth/logout", "/control-api/terminals")) {
            assertEquals(path, target.route(path))
        }
    }
    @Test fun targetCannotInjectAnAddressOrRoute() {
        for (id in listOf("../", "https://example.com", "A".repeat(32), "a".repeat(33), "/api/", "%2f")) {
            assertFalse(validTerminalId(id))
            try { TaskTarget(id); fail("Accepted invalid ID") } catch (_: IllegalArgumentException) { }
        }
        assertEquals("https://relay/terminal-api/${"b".repeat(32)}", TaskTarget("b".repeat(32)).endpoint("https://relay/"))
    }
    @Test fun draftsAndReceiptsAreDistinctForSameThreadOnTwoComputersAndAccounts() {
        val base = "https://relay"
        val a = TaskTarget("a".repeat(32)).endpoint(base)
        val b = TaskTarget("b".repeat(32)).endpoint(base)
        assertNotEquals(draftScope(a, "admin", "token"), draftScope(b, "admin", "token"))
        assertNotEquals(draftScope(a, "admin", "token"), draftScope(a, "other", "token"))
        assertEquals(draftScope(base, "admin", "token"), draftScope(TaskTarget().endpoint(base), "admin", "token"))
    }
}
