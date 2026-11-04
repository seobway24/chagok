plugins {
    alias(libs.plugins.chagok.jvm.library)
}

dependencies {
    api(projects.core.model)
    // QuipEngine 계약만 쓴다. 어떤 카트리지가 끼워지는지는 app 이 정한다
    api(projects.core.quip)
}
