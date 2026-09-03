plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // LOS OBJETIVOS DE APPLE SOLO SE DECLARAN EN UN MAC, Y NO ES UN CAPRICHO.
    //
    // Kotlin/Native no puede compilar para iOS desde Windows ni desde Linux: el
    // enlazador y los SDK son de Apple. Si estos targets se declararan siempre,
    // el proyecto ni siquiera CONFIGURARIA en el ordenador de Dani, que es donde
    // se trabaja todos los dias.
    //
    // Asi, en Windows se compila y se prueba Android con normalidad, y el runner
    // macOS de la nube es el que ve los targets de iOS y genera el .ipa. El
    // codigo de commonMain es el mismo en los dos sitios; lo unico que cambia es
    // quien puede compilarlo.
    if (System.getProperty("os.name").startsWith("Mac")) {
        iosArm64()            // el iPad de verdad
        iosSimulatorArm64()   // el simulador, en los Mac con chip de Apple
    }

    sourceSets {
        commonMain.dependencies {
            // La interfaz, para los dos lados. `compose.` lo aporta el plugin
            // de Compose Multiplatform y en Android se resuelve a los mismos
            // artefactos de androidx que ya usa :app.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

            // El cliente HTTP, con un motor distinto en cada plataforma.
            // HttpURLConnection es de la JVM y no existe en Kotlin/Native.
            implementation("io.ktor:ktor-client-core:3.0.3")

            // Solo el runtime, SIN el plugin de serializacion: aqui no hay
            // clases @Serializable, se recorre el JSON a mano igual que antes
            // con org.json. Menos que aprender y menos que romper.
            //
            // api y no implementation: los almacenes DEVUELVEN JsonObject y
            // JsonArray en exportar(), asi que quien use :shared ve el tipo.
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

            // Las fechas, para los dos lados. java.time no existe en
            // Kotlin/Native, y todo lo que decide "que dia es hoy en España" es
            // logica comun: no puede quedarse en el modulo de Android.
            //
            // api por lo mismo: Novedades y Calendario devuelven LocalDate.
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }

        androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.0.3") }

        // ACCESORES (`commonMain.dependencies`) Y NO `val x by getting`.
        //
        // El CI lo dijo el 04/09/2026: "KotlinSourceSet with name 'iosMain' not
        // found". `by getting` exige que el source set exista YA cuando se
        // evalua este bloque, y los intermedios como iosMain los crea la
        // jerarquia por defecto del plugin, que puede no haber pasado todavia.
        // Los accesores son perezosos y se resuelven cuando toca.
        //
        // NO ERA UN AVISO MENOR: sin `iosMain`, DiscoIos.kt no se compilaba, o
        // sea que el unico fichero escrito para iOS no lo miraba nadie.
        if (System.getProperty("os.name").startsWith("Mac")) {
            iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.0.3") }
        }

        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

android {
    namespace = "com.dani.lector.compartido"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
