plugins {
    alias(libs.plugins.loi.android.application)
}

android {
    namespace = "dev.loi.sample"
    defaultConfig {
        applicationId = "dev.loi.sample"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":loi-android"))
}
