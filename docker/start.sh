#!/usr/bin/env bash
set -euo pipefail

export CREATE_CONSOLE_IN_PIPE="${CREATE_CONSOLE_IN_PIPE:-true}"

app_entry="${APP_ENTRY:-/app/server}"
run_uid="${UID:-1000}"
run_gid="${GID:-1000}"
app_pid=""
bundled_plugins_dir="${BUNDLED_PLUGINS_DIR:-/app/plugins-bundled}"
managed_plugins_dir="${MANAGED_PLUGINS_DIR:-/data/plugins}"

shutdown() {
  if [[ -n "${app_pid}" ]]; then
    kill -TERM "${app_pid}" 2>/dev/null || true
    wait "${app_pid}" 2>/dev/null || true
  fi
}

trap shutdown SIGTERM SIGINT

install_bundled_plugins() {
  if [[ ! -d "${bundled_plugins_dir}" ]]; then
    return
  fi

  mkdir -p "${managed_plugins_dir}"

  shopt -s nullglob
  local plugin_path
  for plugin_path in "${bundled_plugins_dir}"/*.jar; do
    local plugin_name
    plugin_name="$(basename "${plugin_path}")"
    cp -f "${plugin_path}" "${managed_plugins_dir}/${plugin_name}"
  done
  shopt -u nullglob
}

install_bundled_plugins

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
