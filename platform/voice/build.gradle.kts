plugins {
    alias(libs.plugins.chagok.android.library)
}

android {
    namespace = "com.team.chagok.platform.voice"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
}
