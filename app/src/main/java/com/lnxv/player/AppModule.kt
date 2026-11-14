package com.lnxv.player

import com.lnxv.player.playback.engine.PlayerController
import com.lnxv.player.ui.player.PlayerViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Dependency graph (Phase 4). Singletons stay owned by [LNXVApplication] (lazy, process-wide);
 * Koin only exposes them, so ViewModels receive abstractions through their constructors and can be unit-tested with fakes.
 * PlayerViewModel is migrated; the other ViewModels still read from LNXVApplication directly (see docs).
 */
val appModule = module {
    single<PlayerController> { (androidApplication() as LNXVApplication).playerManager }
    single { (androidApplication() as LNXVApplication).videoRepository }
    single { (androidApplication() as LNXVApplication).dataStore }
    viewModel { PlayerViewModel(androidApplication(), get(), get(), get()) }
}
