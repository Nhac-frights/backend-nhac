#!/usr/bin/env bash
set -Eeuo pipefail

# Capture Android startup errors even when flutter test produces no test output.
# The workflow uploads this directory after the emulator runner exits.
mkdir -p e2e-logs
adb logcat -v time -s Flutter flutter AndroidRuntime ActivityTaskManager ActivityManager \
  >e2e-logs/emulator.log 2>&1 &
logcat_pid=$!
trap 'kill "$logcat_pid" 2>/dev/null || true; wait "$logcat_pid" 2>/dev/null || true' EXIT

./tool/run_e2e.sh
