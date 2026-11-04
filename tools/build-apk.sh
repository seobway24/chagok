#!/usr/bin/env bash
# 제출용과 시연용 APK 를 apk/ 에 만든다. AI 키는 어느 쪽에도 들어가지 않는다.
#   chagok_release.apk  빈 명부로 시작, 사람 카드를 직접 만든다
#   chagok_demo.apk     시연 카드 4장과 신호 버튼이 있다. 저장하지 않는다
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p apk
./gradlew :app:assembleRelease </dev/null
cp app/build/outputs/apk/release/app-release.apk apk/chagok_release.apk
./gradlew :app:assembleRelease -PDEMO_MEMORY=true </dev/null
cp app/build/outputs/apk/release/app-release.apk apk/chagok_demo.apk
ls -la apk
