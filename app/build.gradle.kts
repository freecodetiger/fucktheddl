import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.zpc.fucktheddl"
    compileSdk = 36

    val localProperties = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
    val agentBaseUrl = localProperties.getProperty("agent.baseUrl", "http://127.0.0.1:8000")

    val ksPath = localProperties.getProperty("release.keystorePath")
        ?: System.getenv("RELEASE_KEYSTORE_PATH")
    val ksPassword = localProperties.getProperty("release.keystorePassword")
        ?: System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "android"
    val ksAlias = localProperties.getProperty("release.keyAlias")
        ?: System.getenv("RELEASE_KEY_ALIAS") ?: "ddlagent"
    val ksKeyPassword = localProperties.getProperty("release.keyPassword")
        ?: System.getenv("RELEASE_KEY_PASSWORD") ?: "android"

    val hasReleaseKeystore = ksPath != null && file(ksPath).exists()

    defaultConfig {
        applicationId = "com.zpc.fucktheddl"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AGENT_BASE_URL", "\"$agentBaseUrl\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = ksPassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword
            }
        }
        buildTypes {
            release {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                output.outputFileName.set("DDLAgent-v${defaultConfig.versionName}.apk")
            }
        }
    }

}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
