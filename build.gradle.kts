plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Para :shared, el modulo que comparten Android e iOS.
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21" apply false
    id("com.android.library") version "8.7.2" apply false
    // Compose Multiplatform: la interfaz compartida. 1.7.3 es la que va con
    // Kotlin 2.0.21; el plugin de compilador de Compose ya esta arriba.
    id("org.jetbrains.compose") version "1.7.3" apply false
}
