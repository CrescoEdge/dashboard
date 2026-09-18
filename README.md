# Cresco Live Mesh Dashboard

A standalone, dependency-light dashboard that visualizes a **live Cresco mesh** — every node,
link, learned route, and the multi-global coordinator set — in real time. A single Python HTTP
server polls a Cresco **global** over `pycrescolib` (wsapi) and serves a self-contained HTML page
(no CDN, no build step, vanilla JS + inline SVG).

> Replaces the retired Java/Mustache dashboard that previously lived in this repo.

## Run

```bash
./run.sh start           # host = $CRESCO_HOST, else the containerlab global (docker), else localhost
# or directly:
../../run/venv/bin/python dashboard_server.py --host <global> --port 8282 --serve-port 8900 --interval 8
```

Open `http://<host>:8900/`. `pycrescolib` is found in the sibling checkout (`../pycrescolib`) or via
`$PYCRESCOLIB_PATH`. Render check (needs `pip install playwright && playwright install chromium`):

```bash
../../run/venv/bin/python verify_render.py http://localhost:8900/ --expect-storage
```

## Tabs

- **Overview** — mesh stat tiles + a live topology graph (globals centered, regions on a ring,
  agents spoked to their parent region — a true agent → region → global hierarchy).
- **Globals** — the **multi-global coordinator set**: all coordinators, the elected leader (★),
  epoch, and majority quorum (`live / quorum`, has-quorum). Fed by the `getcoordinators` action.
- **Routing** — the learned RouteView graph, inferred/peer links, and per-peer path decisions.
- **Nodes / Metrics** — per-node health.
- **Links** — federation links with RTT / jitter / cost / quality.
- **Storage** — appears only while a Cresco Global File System (`io.cresco.gfs`) is deployed: federation
  capacity (used / pledged), storage-node liveness (UP / SUSPECT / LOST), object health (DURABLE /
  DEGRADED / LOST), repairs in flight, the per-site reciprocity ledger, the objects that are not DURABLE
  and the full storage-node roster. The Overview also gets a Storage tile and rings every agent that
  hosts storage nodes (green ok · amber suspect or >90% full · red hosts a LOST store).

## Data sources (Cresco actions)

`getnetworkstate` (the pushed RouteView graph + path choices) and `getcoordinators` (coordinator
set + consensus state), served by the global's `GlobalExecutor` / `AgentExecutor`.

### Storage (GFS) — self-detecting, push first

- **Detection is free.** The gfs plugin registers a `gfs` metric group per role (`gfs.index.*` only on
  an index instance, `gfs.store.*` only on a storage instance), and the controller folds every plugin's
  `getmetrics` into the `get_metric_inventory(scope=global)` call the poller already makes, keyed
  `<region>_<agent>:<pluginId>`. So the inventory says whether GFS exists, how many instances of each
  role there are, and the exact address of the index — no configuration. `--gfs-index
  region:agent:plugin` overrides discovery.
- **No fan-out.** The index is the federation's aggregator, so detail is ONE RPC to it:
  `storagesummary` → stats, roster, reciprocity ledger, objects not DURABLE (bounded by `limit`).
  Polled every `--storage-interval` seconds (default 10).
- **Push wins.** The index primary publishes the same map as a `cresco_msg_type='gfs_state'` dataplane
  beacon every `gfs_beacon_period_ms` (plugin config, default 10 s); the dashboard subscribes next to
  `route_lsa` / `stunnel_trace` and uses the beacon whenever it is fresh, falling back to the poll.
  Above `gfs_beacon_nodes_max` nodes (default 1000) the beacon omits the roster and the poll supplies it.
- **Tenant isolation.** `storagesummary` is read-only and contains no file content or keys. In a
  site-per-tenant deployment the dashboard's service key lives in the federation-core tenant; reaching
  an index in another tenant needs a `broker_cross_tenant_sinks` READ rule, not new code.

## Correctness notes

- **Per-node CPU, not host load.** CPU is this JVM's own `process.cpu.usage × cpu_count` = cores
  used (+ % of host cores). NOT the host-wide `/proc/loadavg`, which reads identically in every
  container (load average is not cgroup-scoped) and otherwise renders as ~2000% on every node.
- **All globals shown.** A node's RouteView never contains itself, so the observer global is
  injected; the node list is merged from the RouteView (the legacy global DB registers only one
  global), so the full coordinator set appears — not just the polled one.
- **Hierarchy.** Agent→region RouteView edges point at a synthetic `_parent` pseudo-node, so the
  dashboard draws explicit agent→region spokes to render the tier structure.
