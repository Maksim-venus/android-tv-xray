plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.passwall.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.passwall.tv"
        // Floor for the legacy flavor (Android 7.1). Modern overrides this to 31.
        minSdk = 25
        targetSdk = 35
        versionCode = 10
        versionName = "0.1.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val releaseKeystoreFile = (
        System.getenv("RELEASE_KEYSTORE_FILE")
            ?: project.findProperty("RELEASE_KEYSTORE_FILE") as String?
        )?.takeIf { it.isNotBlank() }
    val releaseStorePassword = (
        System.getenv("RELEASE_KEYSTORE_PASSWORD")
            ?: project.findProperty("RELEASE_KEYSTORE_PASSWORD") as String?
        )?.takeIf { it.isNotBlank() }
    val releaseKeyAlias = (
        System.getenv("RELEASE_KEY_ALIAS")
            ?: project.findProperty("RELEASE_KEY_ALIAS") as String?
        )?.takeIf { it.isNotBlank() }
    val releaseKeyPassword = (
        System.getenv("RELEASE_KEY_PASSWORD")
            ?: project.findProperty("RELEASE_KEY_PASSWORD") as String?
        )?.takeIf { it.isNotBlank() }
    val releaseKeystore = releaseKeystoreFile?.let { file(it) }
    val hasReleaseKeystore = releaseKeystore != null && releaseKeystore.isFile &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    flavorDimensions += "device"

    productFlavors {
        create("legacy") {
            dimension = "device"
            applicationIdSuffix = ".legacy"
            versionNameSuffix = "-legacy"
            minSdk = 25
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
            buildConfigField("boolean", "ENABLE_IPV6", "false")
            buildConfigField("int", "FLAVOR_MIN_SDK", "25")
            resValue("string", "flavor_label", "legacy")
        }
        create("modern") {
            dimension = "device"
            minSdk = 31
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            buildConfigField("boolean", "ENABLE_IPV6", "true")
            buildConfigField("int", "FLAVOR_MIN_SDK", "31")
            resValue("string", "flavor_label", "modern")
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
                // v1 (JAR) is what some Android 7.1 boxes install. v2 is what stock Android 7+ checks.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Local builds without the release key stay debug-signed. CI always sets the env vars.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":core-xray"))
    implementation(project(":admin-web"))
    // Packages libv2ray classes + libgojni.so (armeabi-v7a / arm64-v8a).
    implementation(files(rootProject.file("core-xray/libs/libv2ray.aar")))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

tasks.named("preBuild") {
    dependsOn(":core-xray:fetchLibv2ray")
}
