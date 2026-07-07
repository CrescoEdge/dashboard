# Cresco Live Mesh Dashboard

A standalone, dependency-light dashboard that visualizes a **live Cresco mesh** — every node,
link, learned route, and the multi-global coordinator set — in real time. Unlike the legacy
Mustache/Java dashboard in this repo's root, this is a single Python HTTP server that polls a
Cresco **global** over `pycrescolib` (wsapi) and serves a self-contained HTML page (no CDN, no
build step, vanilla JS + inline SVG).

## Run

```bash
./run.sh                 # or:
python3 dashboard_server.py --host <global-mgmt-ip> --port 8282 --serve-port 8900 --interval 15
```

Open `http://<host>:8900/`.

## What it shows

- **Overview** — mesh stat tiles + a live topology graph (globals centered, regions on a ring,
  agents spoked to their parent region — a true agent→region→global hierarchy).
- **Globals** — the **multi-global coordinator set**: all coordinators, the elected leader (★),
  epoch, and majority quorum (`live / quorum`, has-quorum). Fed by the `getcoordinators` action.
- **Routing** — the learned RouteView graph, inferred/peer links, and per-peer path decisions.
- **Nodes / Metrics** — per-node health.
- **Links** — federation links with RTT / jitter / cost / quality.

## Data sources (Cresco actions)

`getnetworkstate` (the pushed RouteView graph + path choices) and `getcoordinators` (coordinator
set + consensus state), both served by the global's `GlobalExecutor` / `AgentExecutor`.

## Notes on correctness (fixes)

- **Per-node CPU, not host load.** CPU is this JVM's own `process.cpu.usage × cpu_count` = cores
  used (+ % of the host's cores). It is NOT the host-wide `/proc/loadavg`, which reads identically
  in every container (containers are not cgroup-scoped for load average) and previously rendered as
  ~2000% on every node.
- **All globals shown.** A node's RouteView never contains itself, so the observer global is
  injected; and the node list is merged from the RouteView (the legacy global DB only registers a
  single global), so all coordinators appear — not just the polled one.
- **Hierarchy.** Agent→region edges in the RouteView point at a synthetic `_parent` pseudo-node,
  so the dashboard draws explicit agent→region spokes to render the tier structure.
