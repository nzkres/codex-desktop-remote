package dev.codex.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePendingTest {
    private val hash = "a".repeat(64)
    private val binding = "b".repeat(64)
    private val pending = PendingInstall("0.1.0-alpha.2", 2, "0.1.0-alpha.3", 3,
        hash, 3, binding)
    private val ledger = UpdateLedger(3, binding, mapOf(pending.version to hash))

    @Test fun cancelledSystemInstallRemainsRetryableButNeverSuccess() {
        assertEquals(PendingInstallState.RETRY,
            assessPendingInstall(pending, ledger, pending.oldVersion, pending.oldCode, null, true))
    }

    @Test fun installedPackageNeedsBothExactOwnApkAndFreshTaskEvidence() {
        assertEquals(PendingInstallState.TASK_PROOF_REQUIRED,
            assessPendingInstall(pending, ledger, pending.version, pending.versionCode, hash, false))
        assertEquals(PendingInstallState.VERIFIED,
            assessPendingInstall(pending, ledger, pending.version, pending.versionCode, hash, true))
        assertEquals(PendingInstallState.NEEDS_REVIEW,
            assessPendingInstall(pending, ledger, pending.version, pending.versionCode, "c".repeat(64), true))
        assertEquals(PendingInstallState.NEEDS_REVIEW,
            assessPendingInstall(pending, ledger, pending.version, pending.versionCode + 1, hash, true))
    }

    @Test fun changedOrLostLedgerCannotVerifyOrRetry() {
        assertEquals(PendingInstallState.NEEDS_REVIEW,
            assessPendingInstall(pending, UpdateLedger(), pending.oldVersion, pending.oldCode, null, false))
        assertEquals(PendingInstallState.NEEDS_REVIEW,
            assessPendingInstall(pending, ledger.copy(binding = "c".repeat(64)),
                pending.version, pending.versionCode, hash, true))
        assertEquals(PendingInstallState.NEEDS_REVIEW,
            assessPendingInstall(pending, ledger.copy(versions = emptyMap()),
                pending.version, pending.versionCode, hash, true))
    }

    @Test fun unexpectedInstalledVersionIsNotTreatedAsCancellation() {
        assertEquals(PendingInstallState.NEEDS_REVIEW,
            assessPendingInstall(pending, ledger, "0.1.0-alpha.4", 4, hash, true))
    }

    @Test fun downloadNeedsHeadroomForPartialAndExistingApk() {
        val packageBytes = 100L * 1024 * 1024
        val minimum = packageBytes * 2 + 64L * 1024 * 1024
        assertFalse(hasDownloadSpace(minimum - 1, packageBytes))
        assertTrue(hasDownloadSpace(minimum, packageBytes))
        assertFalse(hasDownloadSpace(Long.MAX_VALUE, 0))
        assertFalse(hasDownloadSpace(Long.MAX_VALUE, 601L * 1024 * 1024))
    }
}
