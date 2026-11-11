plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Номер версии берётся из номера запуска GitHub Actions, чтобы каждый APK ставился поверх предыдущего.
val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.ivan.grumpweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ivan.grumpweather"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    // Один и тот же ключ для всех сборок — иначе Android не даст обновиться поверх старой версии.
    // Ключ в репо не лежит: CI достаёт его из секретов KEYSTORE_BASE64 / KEYSTORE_PASSWORD.
    // Если секретов нет — подписываем debug-ключом (ставится, но поверх старой версии не обновится).
    val releaseKeystore = rootProject.file("release.keystore")
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val hasReleaseKey = releaseKeystore.exists() && !keystorePassword.isNullOrEmpty()

    signingConfigs {
        if (hasReleaseKey) {
            create("grump") {
                storeFile = releaseKeystore
                storePassword = keystorePassword
                keyAlias = "grump"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "grump" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
