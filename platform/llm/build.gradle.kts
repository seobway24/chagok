plugins {
    alias(libs.plugins.chagok.android.library)
}

android {
    namespace = "com.team.chagok.platform.llm"
}

dependencies {
    implementation(projects.core.quip)
    // 정보 뽑기 카트리지(FactExtractor)
    implementation(projects.core.domain)
    implementation(projects.core.privacy)
    implementation(libs.pleos.llm)
}
