#!/usr/bin/env bash
# Kradus Media — bir buyruqli yig'ish (macOS / Linux).
# Ishlatish:  ./build.sh [debug]  — APK + testlar (standart)
#             ./build.sh release  — release APK (keystore.properties bo'lsa imzolangan)
#             ./build.sh test     — faqat unit testlar
#             ./build.sh install  — yig'ish + ulangan qurilmaga o'rnatish
#             ./build.sh clean    — build papkalarini tozalash
set -e
cd "$(dirname "$0")/android-native" || { echo "[X] android-native papkasi topilmadi."; exit 1; }

# --- Java tekshiruvi ---
if ! command -v java >/dev/null 2>&1; then
    echo "[X] Java topilmadi. JDK 17 o'rnating: https://adoptium.net"
    exit 1
fi
echo "[i] $(java -version 2>&1 | head -1)"

# --- Android SDK manzili (local.properties) ---
if [ ! -f local.properties ]; then
    SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    [ -z "$SDK" ] && [ -d "$HOME/Android/Sdk" ] && SDK="$HOME/Android/Sdk"
    [ -z "$SDK" ] && [ -d "$HOME/Library/Android/sdk" ] && SDK="$HOME/Library/Android/sdk"
    if [ -z "$SDK" ]; then
        echo "[X] Android SDK topilmadi."
        echo "    ANDROID_HOME muhit o'zgaruvchisini ko'rsating yoki"
        echo "    android-native/local.properties ga \"sdk.dir=...\" yozing."
        echo "    Batafsil: README.md > \"Loyihani yig'ish\" bo'limi."
        exit 1
    fi
    echo "sdk.dir=$SDK" > local.properties
    echo "[i] local.properties yaratildi: $SDK"
fi

MODE="${1:-debug}"
case "$MODE" in
    clean)
        ./gradlew --no-daemon :app:clean --console=plain
        echo "[OK] Tozalandi."
        ;;
    test)
        ./gradlew --no-daemon :app:testDebugUnitTest --console=plain
        echo "[OK] Testlar tugadi."
        ;;
    release)
        ./gradlew --no-daemon :app:assembleRelease --console=plain
        echo "[OK] Release APK: app/build/outputs/apk/release/app-release.apk"
        ;;
    install)
        ./gradlew --no-daemon :app:assembleDebug --console=plain
        # shellcheck disable=SC1091
        SDK_DIR=$(grep '^sdk.dir=' local.properties | cut -d= -f2-)
        ADB="$SDK_DIR/platform-tools/adb"
        [ -x "$ADB" ] || { echo "[X] adb topilmadi: $ADB"; exit 1; }
        "$ADB" install -r "app/build/outputs/apk/debug/app-debug.apk"
        echo "[OK] O'rnatildi."
        ;;
    *)
        ./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest --console=plain
        echo "[OK] Debug APK: app/build/outputs/apk/debug/app-debug.apk"
        ;;
esac
