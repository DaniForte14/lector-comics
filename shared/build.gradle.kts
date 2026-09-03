plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
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
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                // El cliente HTTP, con un motor distinto en cada plataforma.
                // HttpURLConnection es de la JVM y no existe en Kotlin/Native.
                implementation("io.ktor:ktor-client-core:3.0.3")
                // Solo el runtime, SIN el plugin de serializacion: aqui no hay
                // clases @Serializable, se recorre el JSON a mano igual que
                // antes con org.json. Menos que aprender y menos que romper.
                // api y no implementation, por lo mismo que kotlinx-datetime:
                // los almacenes DEVUELVEN JsonObject/JsonArray en exportar().
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                // Las fechas, para los dos lados. java.time no existe en
                // Kotlin/Native, y todo lo que decide "que dia es hoy en España"
                // es logica comun: no puede quedarse en el modulo de Android.
                // api y no implementation: `Novedades` y `Calendario` DEVUELVEN LocalDate,
                // asi que quien use :shared tiene que ver el tipo.
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
        }
        val androidMain by getting {
            dependencies { implementation("io.ktor:ktor-client-okhttp:3.0.3") }
        }

        // El motor de iOS solo se puede resolver donde existe el target, o sea
        // en un Mac. Misma regla que los targets de arriba.
        if (System.getProperty("os.name").startsWith("Mac")) {
            val iosMain by getting {
                dependencies { implementation("io.ktor:ktor-client-darwin:3.0.3") }
            }
        }

        val commonTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
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
