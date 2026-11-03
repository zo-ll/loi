plugins {
    alias(libs.plugins.loi.jvm)
}

dependencies {
    api(project(":core"))
}

dependencies {
    testImplementation(project(":testing"))
}
