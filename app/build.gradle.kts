import java.util.Properties

// Las claves viven en local.properties, que git ignora. Nunca en un .kt.
val local = Properties().apply {
    val f = rootProject.file("local.properties")
    // OJO: load(InputStream) lee en ISO-8859-1 y destroza acentos y eñes.
    // Con un Reader en UTF-8 las contrasenas con "ó" o "ñ" llegan enteras.
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}
fun secreto(clave: String) = (local.getProperty(clave) ?: "").trim()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dani.lector"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dani.lector"
        minSdk = 26          // Android 8. Cubre practicamente cualquier movil en uso.
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // Se hornean en la app al compilar. Si estan vacias, la app las pide
        // por la pantalla de Ajustes.
        buildConfigField("String", "COMICVINE_CLAVE", "\"${secreto("comicvine.clave")}\"")

        // SOLO arm64. Es lo que mas adelgaza el APK con diferencia.
        //
        // El motor RAR5 de 7-Zip es una libreria NATIVA, y una libreria nativa
        // se empaqueta una vez POR ARQUITECTURA: 15,8 MB para arm64-v8a, 12,5
        // para armeabi-v7a, 12,4 para x86 y 14,6 para x86_64. Cincuenta y cinco
        // megas para que el movil use UNA.
        //
        // arm64-v8a es lo que lleva cualquier movil de los ultimos diez años.
        // armeabi-v7a es de 32 bits y x86/x86_64 son emuladores. Esta app es
        // para el movil de Dani, asi que sobran las tres.
        //
        // SI ALGUN DIA HACE FALTA EL EMULADOR: añadir "x86_64" a la lista. Sin
        // el, la app arranca igual pero el motor RAR5 no carga; Rar5.iniciar lo
        // atrapa y lo cuenta, no se cierra nada.
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes {
        release {
            // R8: quita el codigo y los recursos que no se usan. Reduce el APK
            // y ayuda al arranque. Lo que puede romper esta en proguard-rules.pro
            // y son cuatro lineas, porque esta app no usa reflexion para nada.
            //
            // AL PROBAR ESTA VARIANTE HAY QUE PROBAR DOS COSAS SI O SI: abrir un
            // CBR (motor nativo) y crear una lista (red + JSON). Son los dos
            // sitios donde un fallo de R8 no aparece al compilar sino al usar.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // FIRMADA CON LA CLAVE DE DEPURACION, A PROPOSITO.
            //
            // Esto es una app personal que no va a ninguna tienda: lo unico que
            // hace falta es poder darle a Run con la variante release puesta y
            // que se instale en el movil. Sin esto, release sale sin firmar y
            // Android no la deja instalar.
            //
            // Y hace falta poder probarla, porque la version DEBUG que instala
            // Android Studio por defecto va mas lenta: el compilador de Compose
            // no aplica las mismas optimizaciones y hay comprobaciones de mas.
            // Medir el tiron en debug y sacar conclusiones es medir otra app.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        // combinedClickable y ModalBottomSheet son experimentales pero estables
        // en la practica. Activarlo aqui evita tener que poner @OptIn en cada
        // funcion que los use y olvidarse en alguna.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
        jvmTarget = "17"
    }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    // Las funciones puras del proyecto se comprueban de verdad, no "a ojo".
    // El primero es Huecos; detras van Parser, Racha y los demas, que el
    // documento daba por probados y no lo estaban.
    testImplementation("junit:junit:4.13.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // Para LocalLifecycleOwner. El de androidx.compose.ui.platform quedo
    // obsoleto en Compose 1.7 y se mudo aqui; hace falta para poder parar las
    // animaciones cuando la app deja de estar delante.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // El trabajo diario que mira si ha salido un numero de las series que
    // sigues. Es la unica cosa de la app que corre con la app cerrada, y entra
    // sabiendo que el proyecto habia decidido no tener WorkManager: aquella
    // regla se escribio por el calor, y lo que calentaba era una animacion
    // infinita repintando a 120 Hz. Esto es un despertar al dia. Ver
    // datos/Vigilante.kt.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // CBR = RAR. junrar es Java puro y funciona en Android, pero solo lee RAR4.
    implementation("com.github.junrar:junrar:7.5.5")

    // Y RAR5, que junrar NO lee.
    //
    // Esta dependencia estuvo aqui, se quito el 24/08/2026 por no usarse desde
    // Kotlin, y vuelve el 25/08/2026 porque la razon de quitarla era falsa: se
    // creia que la biblioteca del usuario no tenia RAR5 y resulta que si.
    //
    // La version sale de la cache de Gradle de la maquina, del intento
    // anterior: 16.02-2.4. El paquete Java es net.sf.sevenzipjbinding y la
    // libreria nativa viaja en los ASSETS del aar (sevenzipjbinding-lib
    // .properties por plataforma), no en jniLibs, asi que abiFilters no la
    // recorta: se extrae en el primer uso.
    //
    // OJO si esto no resuelve: settings.gradle.kts conserva el repositorio de
    // JitPack "para 7-Zip-JBinding", asi que la coordenada buena podria ser la
    // de JitPack en vez de esta. Es lo unico que no se ha podido comprobar
    // antes de escribirlo, porque desde donde se edito no habia red a Maven.
    implementation("com.sorrowblue.sevenzipjbinding:7-Zip-JBinding-4Android:16.02-2.4")
}
