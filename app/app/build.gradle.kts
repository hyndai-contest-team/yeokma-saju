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
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            // 레포에 올릴 APK(100MB 제한)를 위해 코드만 줄인다. 영상과 이미지는 그대로
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 공모전 시연용: 디버그 키로 서명해 바로 설치되게 한다
            signingConfig = signingConfigs.getByName("debug")
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
