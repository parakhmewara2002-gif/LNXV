package com.lnxv.player.core.cast

object CastBackendFactory {
    fun create(): CastBackend? = Media3CastBackend()
}
