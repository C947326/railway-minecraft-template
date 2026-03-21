#!/usr/bin/env bash
set -euo pipefail

export CREATE_CONSOLE_IN_PIPE="${CREATE_CONSOLE_IN_PIPE:-true}"

app_entry="${APP_ENTRY:-/app/server}"
run_uid="${UID:-1000}"
run_gid="${GID:-1000}"
app_pid=""
bundled_plugins_dir="${BUNDLED_PLUGINS_DIR:-/app/plugins-bundled}"
managed_plugins_dir="${MANAGED_PLUGINS_DIR:-/data/plugins}"
resource_pack_path="${RESOURCE_PACK_PATH:-/app/resource-pack/fugitive-baron-resource-pack.zip}"

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

configure_resource_pack() {
  if [[ ! -f "${resource_pack_path}" ]]; then
    return
  fi

  local public_domain="${RAILWAY_PUBLIC_DOMAIN:-}"
  local resource_pack_url="${RESOURCE_PACK_PUBLIC_URL:-}"

  if [[ -z "${resource_pack_url}" ]]; then
    if [[ -z "${public_domain}" ]]; then
      return
    fi
    resource_pack_url="https://${public_domain}/resource-pack/fugitive-baron-resource-pack.zip"
  fi

  local resource_pack_sha1=""
  if command -v sha1sum >/dev/null 2>&1; then
    resource_pack_sha1="$(sha1sum "${resource_pack_path}" | awk '{print $1}')"
  elif command -v shasum >/dev/null 2>&1; then
    resource_pack_sha1="$(shasum -a 1 "${resource_pack_path}" | awk '{print $1}')"
  fi

  if [[ -n "${resource_pack_sha1}" ]]; then
    local resource_pack_id
    resource_pack_id="${resource_pack_sha1:0:8}-${resource_pack_sha1:8:4}-${resource_pack_sha1:12:4}-${resource_pack_sha1:16:4}-${resource_pack_sha1:20:12}"
    export RESOURCE_PACK="${resource_pack_url}"
    export RESOURCE_PACK_SHA1="${resource_pack_sha1}"
    export RESOURCE_PACK_ID="${RESOURCE_PACK_ID:-${resource_pack_id}}"
    export RESOURCE_PACK_ENFORCE="${RESOURCE_PACK_ENFORCE:-TRUE}"
  fi
}

install_bundled_plugins
configure_resource_pack

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
