import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val personalSigningConfigured = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }
val releaseVersionName = System.getenv("ENCORE_VERSION_NAME") ?: "1.0.0"
val releaseVersionCode = System.getenv("ENCORE_VERSION_CODE")?.let { value ->
    requireNotNull(value.toIntOrNull()) { "ENCORE_VERSION_CODE must be an integer" }
} ?: 1

android {
    namespace = "app.encore.french"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.encore.french"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures { compose = true }

    signingConfigs {
        if (personalSigningConfigured) {
            create("personal") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (personalSigningConfigured) signingConfig = signingConfigs.getByName("personal")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (personalSigningConfigured) signingConfig = signingConfigs.getByName("personal")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }

dependencies {
    val roomVersion = "2.8.4"
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("junit:junit:4.13.2")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
