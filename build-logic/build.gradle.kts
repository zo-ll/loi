plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    // AGP built-in Kotlin uses the Kotlin Gradle plugin from the build classpath.
    runtimeOnly(libs.kotlin.gradle.plugin)
    runtimeOnly(libs.android.gradle.plugin)
    testImplementation(libs.kotlin.test.junit)
}

gradlePlugin {
    plugins {
        register("loiJvm") {
            id = "loi.jvm"
            implementationClass = "dev.loi.gradle.JvmConventionPlugin"
        }
        register("loiAndroidLibrary") {
            id = "loi.android-library"
            implementationClass = "dev.loi.gradle.AndroidLibraryConventionPlugin"
        }
        register("loiAndroidApplication") {
            id = "loi.android-application"
            implementationClass = "dev.loi.gradle.AndroidApplicationConventionPlugin"
        }
        register("loiBoundaries") {
            id = "loi.boundaries"
            implementationClass = "dev.loi.gradle.boundaries.ImportBoundariesPlugin"
        }
    }
}
