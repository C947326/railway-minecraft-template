#!/usr/bin/env bash
set -euo pipefail

export CREATE_CONSOLE_IN_PIPE="${CREATE_CONSOLE_IN_PIPE:-true}"

app_entry="${APP_ENTRY:-/app/server}"
run_uid="${UID:-1000}"
run_gid="${GID:-1000}"
console_pipe="${CONSOLE_IN_NAMED_PIPE:-${MC_CONSOLE_PIPE:-/tmp/minecraft-console-in}}"
shutdown_request_path="${MC_SHUTDOWN_REQUEST_PATH:-/tmp/minecraft-poweroff-request.json}"
shutdown_timeout="${MC_SHUTDOWN_TIMEOUT_SECONDS:-45}"
shutdown_notice="${MC_SHUTDOWN_NOTICE:-Server powering off from the control dashboard.}"
shutting_down=0

send_console_command() {
  local command="$1"
  if [[ -p "${console_pipe}" ]]; then
    printf '%s\n' "${command}" > "${console_pipe}" || true
  fi
}

/start &
mc_pid=$!

if command -v gosu >/dev/null 2>&1; then
  gosu "${run_uid}:${run_gid}" "${app_entry}" &
else
  "${app_entry}" &
fi
app_pid=$!

shutdown() {
  if [[ "${shutting_down}" -eq 1 ]]; then
    return
  fi
  shutting_down=1

  if [[ -f "${shutdown_request_path}" ]] && kill -0 "${mc_pid}" 2>/dev/null; then
    send_console_command "say ${shutdown_notice}"
    send_console_command "stop"

    for ((elapsed = 0; elapsed < shutdown_timeout; elapsed++)); do
      if ! kill -0 "${mc_pid}" 2>/dev/null; then
        break
      fi
      sleep 1
    done
  fi

  kill -TERM "${mc_pid}" "${app_pid}" 2>/dev/null || true
  wait "${mc_pid}" "${app_pid}" 2>/dev/null || true
  rm -f "${shutdown_request_path}"
}

trap shutdown SIGTERM SIGINT

watch_for_poweroff() {
  while true; do
    if [[ -f "${shutdown_request_path}" ]]; then
      kill -TERM "$$" 2>/dev/null || true
      return
    fi

    if ! kill -0 "${mc_pid}" 2>/dev/null && ! kill -0 "${app_pid}" 2>/dev/null; then
      return
    fi

    sleep 1
  done
}

watch_for_poweroff &
watcher_pid=$!

wait -n "${mc_pid}" "${app_pid}"
exit_code=$?
kill "${watcher_pid}" 2>/dev/null || true
shutdown
exit "${exit_code}"
