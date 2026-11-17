package com.lnxv.player

import com.lnxv.player.core.session.SessionState
import com.lnxv.player.playback.engine.PlayerController
import com.lnxv.player.ui.home.HomeViewModel
import com.lnxv.player.ui.player.PlayerViewModel
import com.lnxv.player.ui.settings.SettingsViewModel
import com.lnxv.player.ui.vault.VaultViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Dependency graph. Process-wide singletons stay owned by [LNXVApplication] (lazy); Koin only exposes them,
 * so every ViewModel receives its dependencies through its constructor and can be unit-tested with fakes.
 * No ViewModel reads LNXVApplication directly any more.
 */
private fun org.koin.core.scope.Scope.app() = androidApplication() as LNXVApplication

val appModule = module {
    single<PlayerController> { app().playerManager }
    single { app().videoRepository }
    single { app().dataStore }
    single { app().vaultRepository }
    single { app().vaultTransfers }
    single { app().playlistDao }
    single { app().videoPrefsRepository }
    single { app().session }

    viewModel { PlayerViewModel(androidApplication(), get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(androidApplication(), get(), get(), get(), get(), get(), get()) }
    viewModel { VaultViewModel(androidApplication(), get(), get(), get()) }
    viewModel { SettingsViewModel(androidApplication(), get()) }
}
