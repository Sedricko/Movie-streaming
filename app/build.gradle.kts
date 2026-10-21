import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.lumicast"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.lumicast"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Segredos do Telegram: nunca versionar. Defina em local.properties.
        buildConfigField("int", "TELEGRAM_API_ID", (localProps.getProperty("TELEGRAM_API_ID") ?: "0"))
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${localProps.getProperty("TELEGRAM_API_HASH") ?: ""}\"")
    }

    signingConfigs {
        getByName("debug") {
            // CI: chave de debug fixa (cache do Actions) para o APK novo instalar por cima; localmente usa a padrão.
            System.getenv("LUMICAST_DEBUG_KEYSTORE")?.let { storeFile = file(it) }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["main"].apply {
        // TDLib compilado por scripts/build-tdlib.sh (o CI faz isso e guarda em cache).
        java.srcDir(rootProject.file("tdlib/java"))
        jniLibs.srcDir(rootProject.file("tdlib/libs"))
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.cast.framework)
    implementation(libs.androidx.mediarouter)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.play.services.auth)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json) // org.json real nos testes (o do android.jar é só stub)
    testImplementation(libs.kotlinx.coroutines.test)
}
