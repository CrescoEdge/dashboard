#!/bin/bash
# Start/stop the Cresco dashboard backend. Uses a pidfile so we never pkill -f ourselves.
# Host resolution: $CRESCO_HOST, else the containerlab global controller's mgmt IP (if docker is
# present), else localhost. Python: $PY, else the sibling run/venv, else python3.
set -uo pipefail
DASH="$(cd "$(dirname "$0")" && pwd)"
PY="${PY:-$DASH/../../run/venv/bin/python}"; [ -x "$PY" ] || PY=python3
PIDF="$DASH/.dash.pid"
LOG="${LOG:-$DASH/dashboard.log}"
SERVE_PORT="${SERVE_PORT:-8900}"
INTERVAL="${INTERVAL:-8}"
EXTRA="${DASH_ARGS:-}"   # e.g. DASH_ARGS="--gfs-index global-region:global-controller:gfs-index-primary"

stop() {
  if [ -f "$PIDF" ]; then
    kill "$(cat "$PIDF")" 2>/dev/null && echo "stopped $(cat "$PIDF")"
    rm -f "$PIDF"
  else
    echo "no pidfile"
  fi
}

start() {
  GIP="${CRESCO_HOST:-}"
  if [ -z "$GIP" ] && command -v docker >/dev/null 2>&1; then
    GIP=$(docker inspect clab-cresco-mesh-global 2>/dev/null \
      | grep '"IPAddress"' | grep -oE '172\.20\.20\.[0-9]+' | head -1)
  fi
  GIP="${GIP:-localhost}"
  # detach from this shell: setsid on Linux, nohup elsewhere (macOS has no setsid)
  if command -v setsid >/dev/null 2>&1; then DETACH=setsid; else DETACH=nohup; fi
  $DETACH "$PY" "$DASH/dashboard_server.py" --host "$GIP" \
      --serve-port "$SERVE_PORT" --interval "$INTERVAL" $EXTRA \
      > "$LOG" 2>&1 < /dev/null &
  echo $! > "$PIDF"
  disown 2>/dev/null || true
  echo "started pid $(cat "$PIDF")  host=$GIP  port=$SERVE_PORT  log=$LOG"
}

case "${1:-restart}" in
  start) start ;;
  stop) stop ;;
  restart) stop; sleep 1; start ;;
  status) if [ -f "$PIDF" ] && kill -0 "$(cat "$PIDF")" 2>/dev/null; then echo "running pid $(cat "$PIDF") on :$SERVE_PORT"; else echo "stopped"; fi ;;
  *) echo "usage: $0 {start|stop|restart|status}"; exit 1 ;;
esac
