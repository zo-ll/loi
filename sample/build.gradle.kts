plugins {
    alias(libs.plugins.loi.android.application)
}

android {
    namespace = "dev.loi.sample"
    defaultConfig {
        applicationId = "dev.loi.sample"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        // A minified build proves that the consumer R8 rules of loi are enough. It uses the debug key.
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":loi-android"))
    implementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.kotlin.test.junit)
}
