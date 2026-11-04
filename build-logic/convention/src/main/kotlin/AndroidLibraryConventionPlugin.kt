import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** Pleos SDK 어댑터·저장소처럼 안드로이드 API 가 필요한 모듈. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            compileSdk = COMPILE_SDK
            defaultConfig.minSdk = MIN_SDK
            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.fromTarget(JVM_TARGET))
        }

        dependencies {
            add("implementation", libs.lib("kotlinx-coroutines-android"))
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
