plugins {
    alias(libs.plugins.loi.android.library)
}

android {
    namespace = "dev.loi.android"
}

// The only dependency a user adds. See DESIGN.md section 4.
dependencies {
    api(project(":core"))
    api(project(":models:embeddinggemma2"))
    implementation(project(":runtime-litertlm"))
}

dependencies {
    testImplementation(project(":testing"))
}
