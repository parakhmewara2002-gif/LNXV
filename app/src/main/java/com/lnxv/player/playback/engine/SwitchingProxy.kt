package com.lnxv.player.playback.engine

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Creates an instance of interface [T] that forwards every call to whatever [target] returns at call time.
 *
 * Used so ExoPlayer, which fixes its LoadControl at build time, can use a small-buffer configuration for local
 * files and a deep-buffer one for network streams on the same player instance. Reflection keeps this independent
 * of the exact method signatures of the interface (they change between Media3 releases); the cost is one
 * reflective call per LoadControl callback, which is negligible next to the I/O it governs.
 */
object SwitchingProxy {
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> create(type: Class<T>, target: () -> T): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf<Class<*>>(type)) { proxy, method, args ->
            when {
                method.declaringClass == Any::class.java -> when (method.name) {
                    "equals" -> proxy === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    else -> "SwitchingProxy(${type.simpleName})"
                }
                else -> try {
                    method.invoke(target(), *(args ?: emptyArray()))
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            }
        } as T
}
