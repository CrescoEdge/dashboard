#!/bin/bash
# Start/stop the Cresco dashboard backend. Uses a pidfile so we never pkill -f
# ourselves. Auto-detects the containerlab global controller mgmt IP.
set -uo pipefail
cd "$(dirname "$0")/.."
DASH=/scratch/llm_crawl/cresco/dashboard
PY=/scratch/llm_crawl/cresco/run/venv/bin/python
PIDF="$DASH/.dash.pid"
LOG="$DASH/dashboard.log"
SERVE_PORT="${SERVE_PORT:-8900}"
INTERVAL="${INTERVAL:-8}"

stop() {
  if [ -f "$PIDF" ]; then
    kill "$(cat "$PIDF")" 2>/dev/null && echo "stopped $(cat "$PIDF")"
    rm -f "$PIDF"
  else
    echo "no pidfile"
  fi
}

start() {
  # detect global controller IP (containerlab), fall back to arg / localhost
  GIP="${CRESCO_HOST:-}"
  if [ -z "$GIP" ]; then
    GIP=$(docker inspect clab-cresco-mesh-global 2>/dev/null \
      | grep '"IPAddress"' | grep -oE '172\.20\.20\.[0-9]+' | head -1)
  fi
  GIP="${GIP:-172.20.20.4}"
  setsid "$PY" "$DASH/dashboard_server.py" --host "$GIP" \
      --serve-port "$SERVE_PORT" --interval "$INTERVAL" \
      > "$LOG" 2>&1 < /dev/null &
  echo $! > "$PIDF"
  disown
  echo "started pid $(cat "$PIDF")  host=$GIP  port=$SERVE_PORT  log=$LOG"
}

case "${1:-restart}" in
  start) start ;;
  stop) stop ;;
  restart) stop; sleep 1; start ;;
  *) echo "usage: $0 {start|stop|restart}"; exit 1 ;;
esac
