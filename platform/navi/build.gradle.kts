plugins {
    alias(libs.plugins.chagok.android.library)
}

android {
    namespace = "com.team.chagok.platform.navi"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.pleos.navi)
}
