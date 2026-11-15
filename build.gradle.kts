plugins {
    alias(libs.plugins.android.application) apply false
    id("com.android.test") version "8.5.0" apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.ksp) apply false
}
