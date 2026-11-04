plugins {
    alias(libs.plugins.chagok.android.library)
    alias(libs.plugins.chagok.android.compose)
}

android {
    namespace = "com.team.chagok.feature.onair"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    // 가족 프로필 QR — 네트워크 없이 차 화면 → 휴대폰 카메라
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}
