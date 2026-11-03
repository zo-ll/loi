package dev.loi.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** An Android application module, such as the sample app. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        val java = libs.version("java").toInt()
        extensions.configure<ApplicationExtension> {
            compileSdk = libs.version("compileSdk").toInt()
            defaultConfig.minSdk = libs.version("minSdk").toInt()
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
            compileOptions.sourceCompatibility = JavaVersion.toVersion(java)
            compileOptions.targetCompatibility = JavaVersion.toVersion(java)
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(java)
        }
    }
}
