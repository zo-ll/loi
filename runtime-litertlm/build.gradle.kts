plugins {
    alias(libs.plugins.loi.android.library)
}

android {
    namespace = "dev.loi.runtime.litertlm"
}

dependencies {
    api(project(":core"))
    implementation(libs.litertlm.android)
}
