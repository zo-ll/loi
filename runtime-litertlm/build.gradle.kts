plugins {
    alias(libs.plugins.loi.android.library)
}

android {
    namespace = "dev.loi.runtime.litertlm"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.litertlm.android)
}
