package com.lnxv.player.playback

import com.lnxv.player.playback.engine.SwitchingProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

interface Greeter {
    fun greet(name: String): String
    fun count(): Int
    fun boom()
}

class SwitchingProxyTest {
    private class Impl(val tag: String) : Greeter {
        var calls = 0
        override fun greet(name: String) = "$tag:$name"
        override fun count() = ++calls
        override fun boom() = throw IllegalStateException("from $tag")
    }

    @Test fun forwards_to_the_target_chosen_at_call_time() {
        val a = Impl("a")
        val b = Impl("b")
        var useB = false
        val proxy = SwitchingProxy.create(Greeter::class.java) { if (useB) b else a }
        assertEquals("a:x", proxy.greet("x"))
        useB = true
        assertEquals("b:y", proxy.greet("y"))
    }

    @Test fun each_target_keeps_its_own_state() {
        val a = Impl("a")
        val b = Impl("b")
        var useB = false
        val proxy = SwitchingProxy.create(Greeter::class.java) { if (useB) b else a }
        proxy.count(); proxy.count()
        useB = true
        assertEquals(1, proxy.count())
        assertEquals(2, a.calls)
    }

    @Test fun exceptions_from_the_target_are_rethrown_unwrapped() {
        val proxy = SwitchingProxy.create(Greeter::class.java) { Impl("t") }
        try {
            proxy.boom()
            fail("expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("from t", e.message)
        }
    }

    @Test fun object_methods_are_answered_by_the_proxy_itself() {
        val p1 = SwitchingProxy.create(Greeter::class.java) { Impl("t") }
        val p2 = SwitchingProxy.create(Greeter::class.java) { Impl("t") }
        assertTrue(p1 == p1)
        assertFalse(p1 == p2)
        assertEquals(p1.hashCode(), p1.hashCode())
        assertNotEquals(p1.toString(), "")
    }
}
