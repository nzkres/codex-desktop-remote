package dev.codex.remote

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class DeviceQuotaTest {
    private fun prefs():SharedPreferences {
        val rows=mutableMapOf<String,String>()
        val editor=Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,arrayOf(SharedPreferences.Editor::class.java)){proxy,m,args->
            when(m.name){"putString"->{rows[args[0] as String]=args[1] as String;proxy};"commit"->true;else->null}
        }
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){_,m,args->
            when(m.name){"getString"->rows[args[0]]?:args[1];"edit"->editor;else->null}
        } as SharedPreferences
    }
    private fun snapshot(at:Long,used:Double)=UsageSnapshot(listOf(UsageLimit("codex",at,listOf(UsageWindow(used,300,9999999)))))
    private val a="a".repeat(32);private val b="b".repeat(32)
    @Test fun deviceSwitchesCannotReuseAnotherComputersQuota() {
        val store=DeviceQuotaStore(prefs());store.update(a,"thread-a",snapshot(1000,20.0));store.update(b,"thread-b",snapshot(2000,70.0))
        assertEquals(80,quotaLimit(store.values[a])!!.windows[0].remaining)
        assertEquals(30,quotaLimit(store.values[b])!!.windows[0].remaining)
        assertNull(store.values["c".repeat(32)])
    }
    @Test fun realNewerSnapshotMayDecreaseOrResetAndOldResponsesCannotOverwrite() {
        val store=DeviceQuotaStore(prefs());store.update(a,"a",snapshot(1000,20.0));store.update(a,"a",snapshot(2000,50.0))
        store.update(a,"old",snapshot(1500,99.0));assertEquals(50,quotaLimit(store.values[a])!!.windows[0].remaining)
        store.update(a,"a",snapshot(3000,0.0));assertEquals(100,quotaLimit(store.values[a])!!.windows[0].remaining)
    }
    @Test fun failureAndRestartPreserveSnapshotButRequireRecheck() {
        val prefs=prefs();val store=DeviceQuotaStore(prefs);store.update(a,"a",snapshot(1000,20.0));store.failed(a)
        assertTrue(store.values[a]!!.unavailable);assertEquals(80,quotaLimit(store.values[a])!!.windows[0].remaining)
        val restored=DeviceQuotaStore(prefs);assertTrue(restored.values[a]!!.unavailable)
        assertEquals(store.values[a]!!.snapshot,restored.values[a]!!.snapshot)
        store.update(a,"a",null);assertTrue(store.values[a]!!.unavailable)
    }
}
