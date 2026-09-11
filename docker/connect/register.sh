#!/bin/sh
set -eu

CONNECT_URL="${CONNECT_URL:-http://kafka-connect:8083}"
CONNECTOR_DIR="${CONNECTOR_DIR:-/connect}"
MAX_TRIES=60
INTERVAL=5

extract_config() {
  sed -n '/"config"[[:space:]]*:/,$p' "$1" \
    | sed '1s/.*"config"[[:space:]]*:[[:space:]]*{/{/' \
    | sed '$d'
}

echo "[connect-init] ${CONNECT_URL} 응답 대기 (최대 $((MAX_TRIES * INTERVAL))초)"
i=1
while :; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "${CONNECT_URL}/connectors" || true)
  if [ "$code" = "200" ]; then
    echo "[connect-init] Kafka Connect 준비됨 (${i}회 시도)"
    break
  fi
  if [ "$i" -ge "$MAX_TRIES" ]; then
    echo "[connect-init] ${MAX_TRIES}회 재시도 후에도 응답 없음 (마지막 코드=${code}). 포기." >&2
    exit 1
  fi
  i=$((i + 1))
  sleep "$INTERVAL"
done

failed=0
for name in order-outbox-connector product-outbox-connector coupon-outbox-connector; do
  file="${CONNECTOR_DIR}/${name}.json"
  code=$(curl -s -o /dev/null -w '%{http_code}' \
    -X POST -H 'Content-Type: application/json' \
    --data-binary "@${file}" "${CONNECT_URL}/connectors" || true)
  case "$code" in
    201) echo "[connect-init] ${name} 등록 (201)" ;;
    409)
         cfg="/tmp/${name}.config.json"
         extract_config "${file}" > "${cfg}"
         put=$(curl -s -o /dev/null -w '%{http_code}' \
           -X PUT -H 'Content-Type: application/json' \
           --data-binary "@${cfg}" "${CONNECT_URL}/connectors/${name}/config" || true)
         case "$put" in
           200|201) echo "[connect-init] ${name} 이미 존재 (409) — 설정 갱신 (HTTP ${put})" ;;
           *)       echo "[connect-init] ${name} 설정 갱신 실패 (HTTP ${put})" >&2
                    failed=1 ;;
         esac ;;
    *)   echo "[connect-init] ${name} 등록 실패 (HTTP ${code})" >&2
         curl -s -X POST -H 'Content-Type: application/json' --data-binary "@${file}" "${CONNECT_URL}/connectors" >&2 || true
         echo >&2
         failed=1 ;;
  esac
done

sleep 5
for name in order-outbox-connector product-outbox-connector coupon-outbox-connector; do
  echo "[connect-init] ${name} status:"
  curl -s "${CONNECT_URL}/connectors/${name}/status" || true
  echo
done

exit "$failed"
