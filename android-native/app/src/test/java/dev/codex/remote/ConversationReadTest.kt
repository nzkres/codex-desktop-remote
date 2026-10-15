package dev.codex.remote
import org.junit.Assert.*
import org.junit.Test
class ConversationReadTest {
    private val a="a".repeat(64); private val b="b".repeat(64)
    @Test fun listAndBackgroundCannotReadAndLateAckCannotConsumeNewResult() {
        val first=ReadWatermark("").observe(a,10)
        assertTrue(first.unread)
        assertTrue(first.acknowledge(a,false,true).unread)
        assertTrue(first.acknowledge(a,true,false).unread)
        val read=first.acknowledge(a,true,true);assertFalse(read.unread)
        val next=read.observe(b,20);assertTrue(next.unread)
        assertTrue(next.acknowledge(a,true,true).unread)
        assertEquals(b,next.observe(a,9).revision)
    }
    @Test fun nativeRunningSignalWinsOverStaleSessionAndUnknownIsNotIdle() {
        val session=Session("t","p","title","COMPLETED",null,"default",1)
        assertTrue(hubStatus(session,SignalSnapshot("t",false,false,emptySet(),emptySet(),true),1).running)
        assertFalse(hubStatus(session,SignalSnapshot("t",false,false,emptySet(),emptySet()),1).known)
        val waiting=hubStatus(session,SignalSnapshot("t",false,false,setOf("q"),emptySet(),false),1)
        assertTrue(waiting.waiting);assertFalse(waiting.running)
    }
    @Test fun restartRestoresReadPositionsAndDevicesDoNotShareWatermarks() {
        val rows=mutableMapOf<String,String>()
        val editor=java.lang.reflect.Proxy.newProxyInstance(android.content.SharedPreferences.Editor::class.java.classLoader,arrayOf(android.content.SharedPreferences.Editor::class.java)){proxy,m,args ->
            when(m.name){"putString"->{rows[args[0] as String]=args[1] as String;proxy};"commit"->true;else->null} }
        val prefs=java.lang.reflect.Proxy.newProxyInstance(android.content.SharedPreferences::class.java.classLoader,arrayOf(android.content.SharedPreferences::class.java)){_,m,args ->
            when(m.name){"getString"->rows[args[0]] ?: args[1];"edit"->editor;else->null} } as android.content.SharedPreferences
        val store=ConversationReadStore(prefs);store.observe("device-a","task",a,1);store.acknowledge("device-a","task",a)
        store.observe("device-b","task",a,1)
        val reopened=ConversationReadStore(prefs)
        assertFalse(reopened.value("device-a","task").unread);assertTrue(reopened.value("device-b","task").unread)
        reopened.observe("device-a","task",b,2);assertTrue(reopened.value("device-a","task").unread)
    }
}
