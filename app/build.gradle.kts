plugins {
    alias(libs.plugins.android.application)
}

val youkidsApiBaseUrl = providers.gradleProperty("youkidsApiBaseUrl")
    .orElse("https://afterlight.yasiraz.my/api/v1")
    .get()

android {
    namespace = "com.example.youkids"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.youkids"
        minSdk = 23
        targetSdk = 37
        versionCode = 18
        versionName = "2.7"
        buildConfigField("String", "API_BASE_URL", "\"$youkidsApiBaseUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.tvprovider)
    implementation(libs.glide)
    testImplementation(libs.junit)
}