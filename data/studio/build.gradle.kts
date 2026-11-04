plugins {
    alias(libs.plugins.chagok.android.library)
}

android {
    namespace = "com.team.chagok.data.studio"
}

dependencies {
    implementation(projects.core.domain)
}
