plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.chagok.android.compose)
}

// Pleos My Project 자격증명. chagok/.env (git 제외) 에서 빌드할 때만 읽는다. 없으면 빈 값 → SERVER 모드 비활성
val pleosEnv: Map<String, String> = rootProject.file(".env")
    .takeIf { it.isFile }
    ?.readLines()
    .orEmpty()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
    .associate { line ->
        val (key, raw) = line.split('=', limit = 2)
        val value = raw.trim()
        // 따옴표 값은 그대로, 아니면 "값  # 설명" 의 줄 끝 주석을 뗀다
        key.trim() to if (value.startsWith("\"")) value.removeSurrounding("\"")
        else value.substringBefore(" #").substringBefore("\t#").trim()
    }

// 우선순위: gradle -P 값 > .env. 로컬 테스트를 .env 수정 없이 돌릴 수 있게 한다 (예: -PAI_BASE_URL=...)
fun envValue(key: String): String? = (project.findProperty(key) as String?) ?: pleosEnv[key]

// BuildConfig 값을 상수가 아닌 식으로 만든다. "..." 리터럴이면 Kotlin 이 쓰는 쪽에 값을 박아 넣고(인라인),
// 증분 컴파일은 BuildConfig 만 바뀐 걸 모른다 → -P 로 값을 바꿔도 옛 값이 남는다(10/30 두 번 겪음)
fun runtimeString(value: String) = "String.valueOf(\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\")"
fun runtimeLong(value: Long) = "Long.parseLong(\"$value\")"

fun pleosEnvField(key: String) = runtimeString(envValue(key).orEmpty())

android {
    namespace = "com.team.chagok"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.team.chagok"
        // 공모전 요건: API 28+
        minSdk = 28
        // Pleos Connect 에뮬레이터 = API 34
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // 받아치기 엔진 순서. 코드 변경 없이 .env 의 QUIP_CHAIN 한 줄로 카트리지를 갈아끼운다 (architecture §3.3)
        // 아직 없는 엔진 id 는 건너뛰고, template 은 항상 마지막 폴백으로 붙는다
        // 차 안의 Pleos 온디바이스 LLM 이 1순위(대화가 차 밖으로 안 나간다). 에뮬레이터처럼 답이 없으면
        // ChainQuip 이 두 번 실패 뒤 2분간 건너뛰어 remote → template 으로 넘어간다 (10/30 무응답 대응)
        buildConfigField("String", "QUIP_CHAIN", runtimeString(envValue("QUIP_CHAIN") ?: "pleos-llm,remote,template"))
        // 받아치기 전체 대기 시간(ms). 외부 LLM 왕복을 감안해 기본 2500. 로컬 claude 중계는 6000~8000
        buildConfigField("long", "QUIP_BUDGET_MS", runtimeLong(envValue("QUIP_BUDGET_MS")?.toLongOrNull() ?: 2500L))
        // 외부 LLM HTTP 제한(ms). 0 = 기본 4000. 로컬 claude 중계는 8000 정도
        buildConfigField("long", "AI_TIMEOUT_MS", runtimeLong(envValue("AI_TIMEOUT_MS")?.toLongOrNull() ?: 0L))
        // 시연·에뮬레이터용: 지난 회(시연 가족) 기억을 미리 채워 둔다. 에뮬레이터는 마이크 입력이 없어 기억이 안 쌓인다
        buildConfigField("boolean", "DEMO_MEMORY", "Boolean.parseBoolean(\"${envValue("DEMO_MEMORY") == "true"}\")")
        buildConfigField("boolean", "DEMO_ANSWERS", "Boolean.parseBoolean(\"${project.findProperty("DEMO_ANSWERS") == "true"}\")")

        // 외부 LLM (remote). AI_PROVIDER = anthropic | openai(호환 서버는 AI_BASE_URL 로)
        buildConfigField("String", "AI_PROVIDER", pleosEnvField("AI_PROVIDER"))
        buildConfigField("String", "AI_API_KEY", pleosEnvField("AI_API_KEY"))
        buildConfigField("String", "AI_MODEL", pleosEnvField("AI_MODEL"))
        buildConfigField("String", "AI_BASE_URL", pleosEnvField("AI_BASE_URL"))

        // 주의: debug APK 안에 들어간다(디컴파일하면 보인다). SERVER 모드 registerApp 시험용. release 는 아래에서 비운다
        buildConfigField("String", "PLEOS_CLIENT_ID", pleosEnvField("CLIENT_ID"))
        buildConfigField("String", "PLEOS_CLIENT_SECRET", pleosEnvField("CLIENT_SECRET"))
    }

    // 제출용 서명 키. 키 파일·비밀번호는 저장소 밖(~/.gradle/gradle.properties)에만 있다.
    // 없으면(저장소를 받은 다른 사람) debug 키로 서명해 빌드는 그대로 된다
    val releaseStore = (findProperty("CHAGOK_STORE_FILE") as String?)?.let(::file)?.takeIf { it.isFile }
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = findProperty("CHAGOK_STORE_PASSWORD") as String?
                keyAlias = findProperty("CHAGOK_KEY_ALIAS") as String?
                keyPassword = findProperty("CHAGOK_KEY_PASSWORD") as String?
            }
        }
    }
    if (releaseStore == null) logger.warn("chagok: CHAGOK_STORE_FILE 없음 → release 를 debug 키로 서명")

    buildTypes {
        release {
            // ⚠️ 제출·배포용 APK 에는 AI 키·주소를 절대 넣지 않는다 (APK 를 풀면 누구나 꺼낸다 → 내 돈·내 계정).
            // .env 에 무엇이 있든 release 빌드는 빈 값으로 덮어쓴다. 사용자가 정차 화면 "AI 설정"에 키를 넣으면
            // remote 카트리지가 켜지고, 키가 없으면 템플릿으로 진행한다.
            // 개인 키·로컬 claude 중계(tools/claude-relay)는 debug 빌드 테스트 전용.
            buildConfigField("String", "AI_PROVIDER", runtimeString(""))
            buildConfigField("String", "AI_API_KEY", runtimeString(""))
            buildConfigField("String", "AI_MODEL", runtimeString(""))
            buildConfigField("String", "AI_BASE_URL", runtimeString(""))
            buildConfigField("String", "QUIP_CHAIN", runtimeString("pleos-llm,remote,template"))
            // Pleos 자격증명은 SERVER 모드(registerApp)에만 쓴다. 제출 빌드는 ON_DEVICE 라 필요 없다 → APK 에 넣지 않는다
            buildConfigField("String", "PLEOS_CLIENT_ID", runtimeString(""))
            buildConfigField("String", "PLEOS_CLIENT_SECRET", runtimeString(""))
            // 시연용 APK 만 -PDEMO_MEMORY=true 로 시연 가족 카드와 신호 버튼을 켠다. .env 값은 보지 않는다
            buildConfigField("boolean", "DEMO_MEMORY", "Boolean.parseBoolean(\"${project.findProperty("DEMO_MEMORY") == "true"}\")")
            buildConfigField("boolean", "DEMO_ANSWERS", "Boolean.parseBoolean(\"false\")")
            isMinifyEnabled = false
            // 제출용: apk/팀명_앱이름_release.apk — 블라인드 심사라 팀명에 실명 금지
            signingConfig = signingConfigs.getByName(if (releaseStore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.privacy)
    implementation(projects.core.content)
    implementation(projects.platform.vehicle)
    implementation(projects.platform.voice)
    implementation(projects.platform.llm)
    implementation(projects.platform.navi)
    implementation(projects.data.studio)
    implementation(projects.feature.onair)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
