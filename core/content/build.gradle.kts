plugins {
    alias(libs.plugins.chagok.jvm.library)
}

// 내장 형식 팩·스킨·템플릿·시연 명부. 안드로이드 없이 JVM 시뮬레이션 테스트에서도 같은 콘텐츠를 쓴다
dependencies {
    api(projects.core.model)
    api(projects.core.quip)

    testImplementation(projects.core.domain)
    testImplementation(projects.core.privacy)
}
