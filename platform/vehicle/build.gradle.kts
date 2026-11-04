plugins {
    alias(libs.plugins.chagok.android.library)
}

android {
    namespace = "com.team.chagok.platform.vehicle"
    // CarUxRestrictionsManager (AAOS 표준)
    useLibrary("android.car")
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.pleos.vehicle)
}
