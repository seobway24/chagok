import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** 모든 모듈이 같은 JVM 타깃을 쓴다. Pleos 템플릿 기준 JDK 17. */
internal val JAVA_VERSION = JavaVersion.VERSION_17
internal const val JVM_TARGET = "17"

/** Pleos Connect 에뮬레이터 = API 34. 공모전 요건 minSdk 28. */
internal const val COMPILE_SDK = 34
internal const val MIN_SDK = 28
