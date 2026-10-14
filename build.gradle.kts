plugins {
    alias(libs.plugins.android.application) apply false
    // Не применяется к модулям (в AGP 9 Kotlin встроен), только фиксирует версию KGP.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
