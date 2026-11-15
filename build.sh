#!/bin/bash
# 墨笺 (InkNote) 构建脚本 —— 在 proot Ubuntu 中执行
# 用法: bash build.sh [gradle 任务，默认 :app:assembleRelease]
set -u
PROJ="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}"
export ANDROID_HOME=/root/android-sdk
export ANDROID_SDK_ROOT=/root/android-sdk
export GRADLE_USER_HOME=/storage/emulated/0/WORK/02-工具链/android-user-home
export PATH="$JAVA_HOME/bin:$PATH"
TASK="${1:-:app:assembleRelease}"
cd "$PROJ" || exit 1
bash /storage/emulated/0/WORK/02-工具链/toolchain/gradle-8.11.1/bin/gradle "$TASK" \
  -Pandroid.aapt2FromMavenOverride=/root/aapt2-bin/aapt2 2>&1 | tee /tmp/inknote-build.log
RC=${PIPESTATUS[0]}
echo "== 退出码： $RC（日志：/tmp/inknote-build.log）"
exit $RC
