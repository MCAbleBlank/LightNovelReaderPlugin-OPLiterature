#!/bin/sh
# 仅用于本机（Android arm64 / proot、无 Android Studio）的离线构建：
#   - Gradle 用本机安装版，不走 wrapper
#   - 显式指定 Java 证书库
#   - aapt2 只有 x86_64 版本，用 qemu 包装器
# 常规构建请直接使用 ./gradlew :plugin:assembleDebug
set -e
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH="$JAVA_HOME/bin:/opt/gradle-9.6.1/bin:$PATH"
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts -Djavax.net.ssl.trustStorePassword=changeit"

cd "$(dirname "$0")"
gradle :plugin:assembleDebug -Pandroid.aapt2FromMavenOverride=/opt/aapt2q/aapt2

VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' plugin/build.gradle.kts)
mkdir -p dist
cp plugin/build/outputs/apk/debug/plugin-debug.apk.lnrp "dist/OPLiterature-${VERSION}.apk.lnrp"
echo "产物: $(pwd)/dist/OPLiterature-${VERSION}.apk.lnrp"
