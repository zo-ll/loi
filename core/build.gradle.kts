plugins {
    alias(libs.plugins.loi.jvm)
}

// The core has one dependency. See DESIGN.md section 5.
dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
