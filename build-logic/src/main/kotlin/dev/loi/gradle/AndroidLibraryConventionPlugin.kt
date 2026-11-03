package dev.loi.gradle

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** An Android library module. AGP 9 compiles its Kotlin with built-in Kotlin support. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        val java = libs.version("java").toInt()
        extensions.configure<LibraryExtension> {
            compileSdk = libs.version("compileSdk").toInt()
            defaultConfig.minSdk = libs.version("minSdk").toInt()
            compileOptions.sourceCompatibility = JavaVersion.toVersion(java)
            compileOptions.targetCompatibility = JavaVersion.toVersion(java)
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(java)
            explicitApi()
        }
        dependencies {
            add("testImplementation", libs.library("kotlin-test-junit"))
        }
    }
}
