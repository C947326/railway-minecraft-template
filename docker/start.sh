#!/usr/bin/env bash
set -euo pipefail

export CREATE_CONSOLE_IN_PIPE="${CREATE_CONSOLE_IN_PIPE:-true}"

app_entry="${APP_ENTRY:-/app/server}"
run_uid="${UID:-1000}"
run_gid="${GID:-1000}"
app_pid=""

shutdown() {
  if [[ -n "${app_pid}" ]]; then
    kill -TERM "${app_pid}" 2>/dev/null || true
    wait "${app_pid}" 2>/dev/null || true
  fi
}

trap shutdown SIGTERM SIGINT

if command -v gosu >/dev/null 2>&1; then
  gosu "${run_uid}:${run_gid}" "${app_entry}" &
else
  "${app_entry}" &
fi
app_pid=$!

set +e
wait "${app_pid}"
exit_code=$?
set -e
shutdown
exit "${exit_code}"
