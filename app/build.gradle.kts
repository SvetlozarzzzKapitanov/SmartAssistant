import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.sKapit.smartassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sKapit.smartassistant"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        val mapsApiKey = localProperties.getProperty("MAPS_API_KEY") ?: ""
        val bestTimeApiKey = localProperties.getProperty("BEST_TIME_API_KEY") ?: ""

        // Това прави ключа достъпен в Kotlin кода чрез BuildConfig.MAPS_API_KEY
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "BEST_TIME_API_KEY", "\"$bestTimeApiKey\"")
        // Това прави ключа достъпен в AndroidManifest.xml чрез ${MAPS_API_KEY}
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildFeatures {
        buildConfig = true // Задължително за по-новите версии на Android Studio
        viewBinding = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.google.gson)
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)
    implementation(libs.places)
    implementation(libs.okhttp.v530)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
}
