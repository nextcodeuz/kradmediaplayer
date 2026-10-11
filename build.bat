@echo off
REM Kradus Media — bir buyruqli yig'ish (Windows).
REM Ishlatish:  build.bat [debug]  — APK + testlar (standart)
REM             build.bat release  — release APK (keystore.properties bo'lsa imzolangan)
REM             build.bat test     — faqat unit testlar
REM             build.bat install  — yig'ish + ulangan qurilmaga o'rnatish
REM             build.bat clean    — build papkalarini tozalash
setlocal EnableDelayedExpansion

cd /d "%~dp0android-native" 2>nul || (echo [X] android-native papkasi topilmadi. & exit /b 1)

REM --- Java 17 tekshiruvi ---
where java >nul 2>nul || (echo [X] Java topilmadi. JDK 17 o'rnating: https://adoptium.net & exit /b 1)
for /f "tokens=*" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do echo [i] %%v

REM --- Android SDK manzili (local.properties) ---
if not exist "local.properties" (
    set "SDK="
    if defined ANDROID_HOME set "SDK=%ANDROID_HOME%"
    if not defined SDK if defined ANDROID_SDK_ROOT set "SDK=!ANDROID_SDK_ROOT!"
    if not defined SDK if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK=%LOCALAPPDATA%\Android\Sdk"
    if not defined SDK (
        echo [X] Android SDK topilmadi.
        echo     ANDROID_HOME muhit o'zgaruvchisini ko'rsating yoki
        echo     android-native\local.properties ga "sdk.dir=..." yozing.
        echo     Batafsil: README.md ^> "Loyihani yig'ish" bo'limi.
        exit /b 1
    )
    set "SDK=!SDK:\=/!"
    echo sdk.dir=!SDK!>"local.properties"
    echo [i] local.properties yaratildi: !SDK!
)

set "MODE=%~1"
if "%MODE%"=="" set "MODE=debug"

if /i "%MODE%"=="clean" (
    call gradlew.bat --no-daemon :app:clean --console=plain || exit /b 1
    echo [OK] Tozalandi.
    exit /b 0
)
if /i "%MODE%"=="test" (
    call gradlew.bat --no-daemon :app:testDebugUnitTest --console=plain || exit /b 1
    echo [OK] Testlar tugadi.
    exit /b 0
)
if /i "%MODE%"=="release" (
    call gradlew.bat --no-daemon :app:assembleRelease --console=plain || exit /b 1
    echo [OK] Release APK: app\build\outputs\apk\release\app-release.apk
    exit /b 0
)
if /i "%MODE%"=="install" (
    call gradlew.bat --no-daemon :app:assembleDebug --console=plain || exit /b 1
    for /f "tokens=*" %%s in ('findstr /r "^sdk.dir=" local.properties') do set "%%s"
    set "ADB=!sdk.dir!/platform-tools/adb.exe"
    if not exist "!ADB!" (echo [X] adb topilmadi: !ADB! & exit /b 1)
    "!ADB!" install -r "app\build\outputs\apk\debug\app-debug.apk" || exit /b 1
    echo [OK] O'rnatildi.
    exit /b 0
)

REM standart: debug
call gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest --console=plain || exit /b 1
echo [OK] Debug APK: app\build\outputs\apk\debug\app-debug.apk
