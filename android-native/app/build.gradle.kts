import groovy.json.JsonSlurper
import org.gradle.api.tasks.Copy

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseNotesFile = rootProject.layout.projectDirectory.file("releases.json")
val releaseManifest = JsonSlurper().parse(releaseNotesFile.asFile) as? Map<*, *>
    ?: error("android_releases_invalid")
require(releaseManifest["schema"] == 1 && releaseManifest["component"] == "android") {
    "android_releases_invalid"
}
val currentRelease = (releaseManifest["releases"] as? List<*>)?.firstOrNull() as? Map<*, *>
    ?: error("android_releases_empty")
val currentVersionName = currentRelease["version"] as? String ?: error("android_version_invalid")
val currentVersionCode = currentRelease["versionCode"] as? Int ?: error("android_version_invalid")
require(currentVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")) && currentVersionCode > 0) {
    "android_version_invalid"
}

val generatedReleaseNotesAssetsDir = layout.buildDirectory.dir("generated/release-notes/assets")
val generateReleaseNotesAssets = tasks.register<Copy>("generateReleaseNotesAssets") {
    from(releaseNotesFile)
    into(generatedReleaseNotesAssetsDir)
}
val trustSource = System.getenv("DESKTOP_REMOTE_ANDROID_UPDATE_TRUST")?.takeIf { it.isNotBlank() }
    ?.let { file(it) } ?: rootProject.file("update-trust.default.json")
require(trustSource.isFile && (System.getenv("DESKTOP_REMOTE_ANDROID_UPDATE_TRUST").isNullOrBlank() ||
    !trustSource.canonicalFile.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath()))) {
    "update_trust_must_be_external_file"
}
val generatedTrustAssetsDir = layout.buildDirectory.dir("generated/update-trust/assets")
val generateTrustAsset = tasks.register<Copy>("generateTrustAsset") {
    from(trustSource) { rename { "update-trust.json" } }
    into(generatedTrustAssetsDir)
}
val signingNames = listOf("DESKTOP_REMOTE_ANDROID_KEYSTORE", "DESKTOP_REMOTE_ANDROID_STORE_PASSWORD",
    "DESKTOP_REMOTE_ANDROID_KEY_ALIAS", "DESKTOP_REMOTE_ANDROID_KEY_PASSWORD")
val signingValues = signingNames.map { System.getenv(it).orEmpty() }
require(signingValues.all { it.isBlank() } || signingValues.all { it.isNotBlank() }) {
    "android_signing_configuration_incomplete"
}
val controlledSigning = signingValues.all { it.isNotBlank() }

android {
    namespace = "dev.codex.remote"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.codex.desktopremote.lite"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = currentVersionCode
        versionName = currentVersionName
    }

    sourceSets.getByName("main").assets.srcDir(generatedReleaseNotesAssetsDir)
    sourceSets.getByName("main").assets.srcDir(generatedTrustAssetsDir)

    signingConfigs {
        if (controlledSigning) create("controlled") {
            val keyFile = file(signingValues[0])
            require(keyFile.isFile && !keyFile.canonicalFile.toPath()
                .startsWith(rootProject.projectDir.canonicalFile.toPath())) { "android_signing_keystore_must_be_external" }
            storeFile = keyFile
            storeType = "PKCS12"
            storePassword = signingValues[1]
            keyAlias = signingValues[2]
            keyPassword = signingValues[3]
        }
    }
    buildTypes {
        getByName("debug") { if (controlledSigning) signingConfig = signingConfigs.getByName("controlled") }
        release {
            isMinifyEnabled = false
            if (controlledSigning) signingConfig = signingConfigs.getByName("controlled")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Android's asset merge must see the generated directory after the copy runs.
tasks.configureEach {
    if (name == "preBuild" || (name.startsWith("merge") && name.endsWith("Assets"))) {
        dependsOn(generateReleaseNotesAssets, generateTrustAsset)
    }
    if (!controlledSigning && name in setOf("assembleRelease", "bundleRelease", "packageRelease")) {
        doFirst { error("controlled_android_signing_required_for_release") }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
