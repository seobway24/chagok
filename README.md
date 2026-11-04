# 차곡 팟캐스트 (chagok)

**가족이 차에 타면, 출발부터 목적지까지 우리 가족만의 팟캐스트가 열립니다.**
차량 신호(와이퍼·전조등·문·벨트·내비 남은 시간)가 코너를 열고, 진행자 목소리(Pleos TTS)가 한 사람씩 이름을 불러 묻고, 짧은 대답(Pleos STT)에 한 줄로 받아칩니다. 회차와 대답은 차 안에만 쌓이고, 다음 회에 지난 대답을 먼저 꺼냅니다.

- 개발: 개인 참가 (저장소·커밋 이름 chagok)
- 플랫폼: Pleos Connect (Android Automotive, API 34) · 최소 API 28
- 언어: Kotlin · Jetpack Compose · Gradle 8

## 시연 영상

[![차곡 시연 영상 (2분 43초)](docs/video/chagok_demo_thumb.png)](docs/video/chagok_demo.mp4)

Pleos Connect 에뮬레이터에서 실제 앱을 돌린 화면입니다. 진행자 목소리는 앱의 온디바이스 TTS 출력을 스피커에서 녹음했고, 차량 신호는 앱의 디버그 신호 패널로 넣었고, 패널은 영상에서 가렸습니다. 가족 대답은 데모 데이터이고 그 목소리는 합성 음성(edge-tts)이며, 대답하는 동안은 화면을 멈췄습니다. 배경 음악은 AI로 만들었습니다(ACE-Step, MIT 라이선스).

<!-- 유튜브: (업로드 후 추가) -->

## 화면

| | |
|---|---|
| ![벨트 안내](docs/screens/03_belt_reminder.png)<br>탑승: 벨트를 매면 출석, 안 맨 사람은 이름으로 안내 | ![출석 완료](docs/screens/04_all_belted_ready.png)<br>모두 매면 카운트다운 뒤 방송 시작 |
| ![오프닝](docs/screens/05_show_opening.png)<br>오프닝: 회차와 탄 사람 이름을 불러 줌 | ![지난 회 인용](docs/screens/11_route_highlight.png)<br>내비 목적지가 제목이 되고, 지난 회 한 줄을 인용해 이어 묻기 |
| ![기상 특보](docs/screens/07_corner_weather.png)<br>와이퍼 → 기상 특보 | ![터널 토크](docs/screens/08_corner_tunnel.png)<br>터널 통과(전조등) → 터널 토크 |
| ![게스트 입장](docs/screens/09_corner_guest.png)<br>뒷문 닫힘 → 게스트 입장 | ![주행 특집](docs/screens/10_corner_distance.png)<br>함께 100km → 주행 특집 |
| ![주행 화면](docs/screens/14_driving_corner_tunnel.png)<br>주행 중: 코너 이름 한 줄, 터치 없음 | ![잠깐 쉬기](docs/screens/13_pause_turn_signal.png)<br>깜빡이 → 잠깐 쉬기 |
| ![엔딩](docs/screens/16_ending.png)<br>기어 P → 엔딩 인사 | ![다음 회 한마디](docs/screens/17_keepsake_prompt.png)<br>다음 회에 틀 한마디, 누가 남길까요? |
| ![저장 완료](docs/screens/19_keepsake_saved.png)<br>한마디는 이 차에만 저장 | ![목소리 제안](docs/screens/28_keepsake_offer.png)<br>다음 회 시작 때 지난 회 목소리 틀기 |
| ![회차 정리](docs/screens/20_episode_recap.png)<br>지난 방송 표지 → 회차 정리 · 기억 지우기 | ![AI 설정](docs/screens/26_ai_settings_privacy.png)<br>외부 AI는 선택: 이름을 가려 보내고 키는 차에만 암호화 저장 |

## APK

| 파일 | 설명 |
|---|---|
| [`apk/chagok_차곡팟캐스트_release.apk`](apk/) | 제출용 release 빌드 (자체 서명 키, 외부 AI 키 없음) |

## 실행 방법

### 1. 설치
```bash
adb install -r apk/chagok_차곡팟캐스트_release.apk
# 마이크 권한을 미리 줄 때 (Pleos Connect 에뮬레이터는 사용자 10)
adb shell pm grant --user 10 com.team.chagok android.permission.RECORD_AUDIO
```

### 2. 첫 설정 (정차 중)
1. 앱을 열면 녹음 동의와 보호자 비밀번호를 정합니다.
2. 대기 화면에서 **방송 시작**을 누르거나, Virtual Console 에서 **동승자 자리 착석·벨트 → 운전석 벨트를 마지막에** 맵니다. 벨트를 매면 출석입니다.
3. 처음 보는 자리는 진행자가 "누구세요?"라고 묻고, 말한 이름으로 부릅니다.

### 3. 신호로 코너 열기 (Pleos Connect 에뮬레이터 · Virtual Console)

| Virtual Console 조작 | 앱에서 일어나는 일 |
|---|---|
| 기어 D / 주행 | 주행 화면(한 줄·터치 없음). 운전자는 이름을 부르지 않음 |
| 앞 유리 와이퍼 켜기 (20초 유지) | 기상 특보 코너 |
| 전조등 켜기 → **5초 이상 뒤** 끄기 | 터널 토크 코너 (10분 넘게 켜져 있으면 밤으로 보고 무시) |
| 뒷문 열고 닫기 | 게스트 입장 코너 |
| 방향지시등 켜기 | 진행 멈춤. 끄면 2초 뒤 재개 |
| 브레이크 밟기 | 밟는 동안과 뗀 뒤 10초 새 코너 보류 |
| 주행 중 벨트 풀기 | 멈추고 이름을 불러 안내 |
| 내비 경로 안내 (목적지 설정) | 제목 "제N회 · ○○ 가는 길", 오프닝 "○○까지 함께 갑니다", 남은 3분에 도착 예고 |
| 기어 P | 엔딩 (오늘의 한마디 · 운전자 소감 · 인사) → 하차 한마디 녹음(선택) |

신호가 45초 넘게 없으면 채움 코너(오늘의 순간·둘 중 하나·꿈 인터뷰·우리 집 투표 등)가 열립니다. 내비 남은 시간을 알면 길이에 맞춰 간격을 늘립니다.

### 4. 소스에서 빌드
```bash
./gradlew test            # 단위 테스트
./gradlew :app:assembleRelease
```
- release 서명 키는 저장소에 없습니다. `~/.gradle/gradle.properties` 에 `CHAGOK_STORE_FILE` 등이 없으면 debug 키로 서명합니다.
- 외부 AI 연결은 선택입니다. release 빌드에는 키가 들어가지 않으며, 정차 화면의 AI 설정에 보호자가 직접 넣었을 때만 켜집니다.

## 테스트 환경
- Pleos Connect 에뮬레이터 (Android Studio, API 34, 사용자 10), Virtual Console 로 차량 신호 주입
- 단위 테스트: `core` 모듈 상태 머신·신호·기억·안전 규칙 (`./gradlew test`)
- 공모전 기간에는 운영사가 Pleos LLM 서비스를 제공하지 않아, 에뮬레이터·제출 빌드 모두 받아치기를 내장 문장으로 진행합니다. 연결 코드는 들어 있어 서비스가 열리면 자동으로 먼저 씁니다(실제 LLM 응답으로는 검증 전)
- 에뮬레이터는 차가 움직이지 않아 내비 남은 시간이 줄지 않습니다. 도착 예고는 실차에서 남은 3분에 열립니다.

## Pleos SDK 사용

| SDK | 버전 | 쓰는 곳 | 권한 |
|---|---|---|---|
| Vehicle (와이퍼·조명·문·주행거리·방향지시등·브레이크·기어·좌석) | 2.3.2 | `platform/vehicle` | CAR_WINDOWS · CAR_LIGHTS · CAR_DOORS · CAR_DRIVE · CAR_SEATS |
| TextToSpeech / SpeechToText (온디바이스) | 3.3.1 | `platform/voice` | TTS_SERVICE · STT_SERVICE · RECORD_AUDIO |
| LLM (온디바이스) — **연결만 구현, 공모전 기간 서비스 미제공으로 미검증** | 2.1.3.2 | `platform/llm` | LLM_SERVICE |
| NaviHelper (목적지 이름·남은 시간만) | 3.0.5 | `platform/navi` | NAVI_ROUTE |
| CarUxRestrictions (주행 판단) | AAOS | `platform/vehicle` | — |

차량 제어 권한은 쓰지 않습니다(읽기만).

## 구조
```
app/            진입점 · 의존성 조립
core/model      신호·코너·출연자 같은 값
core/domain     방송 규칙(순수 상태 머신) · 진행 · 기억 · 신호 해석
core/content    기본 코너 10개 · 질문 67개
core/quip       받아치기 카트리지 체인 · 안전 필터
core/privacy    외부 AI로 나가는 글의 이름 가리기 · 동의 확인
data/studio     회차·기억·목소리 저장 (암호화)
platform/*      Pleos SDK 어댑터 (vehicle · voice · llm · navi)
build-logic/    공통 Gradle 설정
feature/onair   화면 (정차·주행·엔딩)
legal/          개인정보 처리방침 · 글꼴 라이선스
```

## 개인정보
서버가 없습니다. 대답·목소리는 기기 안에 암호화(AES-GCM, Android Keystore)해 저장합니다. 내비에서는 목적지 이름과 남은 시간만 읽고 저장하지 않습니다. 처리방침: [`legal/privacy/index.html`](legal/privacy/index.html)

## 오픈소스 고지
[`NOTICE`](NOTICE)
