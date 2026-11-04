pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Playground SDK (Vehicle / STT / TTS / NaviHelper / LLM)
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}

rootProject.name = "chagok"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// 순수 Kotlin(JVM) — 안드로이드 없이 단위 테스트
include(":core:model")
include(":core:domain")
include(":core:quip")
include(":core:privacy")
include(":core:content")

// Pleos SDK 어댑터 — domain 의 port 를 구현
include(":platform:vehicle")
include(":platform:voice")
include(":platform:llm")
include(":platform:navi")

// 저장소
include(":data:studio")

// 화면
include(":feature:onair")
