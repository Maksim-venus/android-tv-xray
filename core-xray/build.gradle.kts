plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val libv2rayAar = file("libs/libv2ray.aar")
val fetchLibv2ray = tasks.register<Exec>("fetchLibv2ray") {
    description = "Download AndroidLibXrayLite libv2ray.aar if missing"
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/fetch-libv2ray.sh")
    outputs.file(libv2rayAar)
    onlyIf { !libv2rayAar.exists() || libv2rayAar.length() < 1_000_000 }
}

val geositeDat = file("src/main/assets/xray/geosite.dat")
val fetchGeoAssets = tasks.register<Exec>("fetchGeoAssets") {
    description = "Download official Loyalsoldier geosite.dat if missing or too small"
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/fetch-geo-assets.sh")
    outputs.file(geositeDat)
    onlyIf { !geositeDat.exists() || geositeDat.length() < 100_000 }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(fetchLibv2ray)
    dependsOn(fetchGeoAssets)
}

android {
    namespace = "com.passwall.corexray"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":data"))
    // Compile against the AAR; the app module packages libgojni.so.
    compileOnly(files("libs/libv2ray.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.15.0")

    testImplementation("junit:junit:4.13.2")
}
