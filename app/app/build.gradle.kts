plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.yeokma.saju"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yeokma.saju"
        minSdk = 34 // Pleos Connect = Android 14
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // 서버 주소. 에뮬레이터에서 10.0.2.2 = 이 Mac에서 켠 로컬 서버(wrangler dev)
        buildConfigField("String", "API_BASE", "\"http://10.0.2.2:8787\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    androidResources {
        noCompress += listOf("mp4")
    }
    // 태어난 곳 목록(data/places.tsv)은 서버와 같은 파일을 쓴다
    sourceSets["main"].assets.directories.add("../../data")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.stt)
}
