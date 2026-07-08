#!/usr/bin/env python3
"""
Cresco Mesh Dashboard — backend.

Polls a Cresco global controller over the wss clientlib API and serves a normalized
JSON model (topology, health, metrics, multi-link broker telemetry) plus a static
single-page Tremor-style dashboard.

Design notes:
  * pycrescolib latches `_failed_connection` after a single RPC timeout and refuses to
    send again on that clientlib. get_agent_list() on *bridged* edge regions reliably
    times out (8s). So the poller uses a FRESH clientlib per cycle and relies on the
    two calls that are fast + reliable on a fresh connection:
        - get_region_list()                         (~0.2s) -> topology + bridging
        - get_metric_inventory(scope='global')      (~4s)   -> every node's metrics,
          including edge nodes under `children`, incl. the netlink link telemetry.
    The global agent list is fetched too (fast); edge agent lists are intentionally
    skipped and their agent/plugin info is derived from the metric inventory instead.

Usage:
  ./venv/bin/python dashboard/dashboard_server.py [--host 172.20.20.3] [--port 8282]
                                                  [--serve-port 8900] [--interval 8]
"""
import argparse
import json
import re
import sys
import threading
import time
from collections import defaultdict, deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, "/scratch/llm_crawl/cresco/code/pycrescolib")
from pycrescolib.clientlib import clientlib  # noqa: E402

HERE = Path(__file__).resolve().parent
CLAB_DIR = HERE.parent / "containerlab"

# ---------------------------------------------------------------------------
# configured-topology discovery (containerlab)
# ---------------------------------------------------------------------------

def discover_configured_edges(clab_dir=CLAB_DIR):
    """Read the containerlab topology (yml + generated topology-data.json) to learn the
    *intended* broker mesh — including the redundant region<->region peer bridge that
    may carry no telemetry yet. Returns (edges, roles) or ([], {}) if files absent.

    edges: list of {a_norm, z_norm, a, z, kind}  (kind: 'uplink' | 'peer')
    roles: {cresco_id: 'GLOBAL'|'REGION'}
    """
    yml = clab_dir / "cresco-mesh.clab.yml"
    tdata = clab_dir / "clab-cresco-mesh" / "topology-data.json"
    if not yml.exists():
        return [], {}
    text = yml.read_text()
    # map shortname -> {role, region, agent, cresco_id} by scanning env blocks
    short_to_node = {}
    roles = {}
    # split the nodes: section into per-node blocks keyed by 4-space-indented shortnames
    body = text.split("nodes:", 1)[-1].split("\n  links:", 1)[0]
    blocks = re.split(r"\n    (\w+):", "\n" + body)
    # blocks = ['', name1, block1, name2, block2, ...]
    for i in range(1, len(blocks) - 1, 2):
        short = blocks[i]
        blk = blocks[i + 1]
        envm = re.search(r"env:\s*\{(.*?)\}", blk, re.DOTALL)
        if not envm:
            continue
        env = envm.group(1)
        role = (re.search(r"CRESCO_ROLE:\s*(\w+)", env) or [None, ""])[1] if re.search(r"CRESCO_ROLE:\s*(\w+)", env) else ""
        region = (re.search(r"REGION:\s*([\w\-]+)", env) or [None, ""])
        agent = (re.search(r"AGENT:\s*([\w\-]+)", env) or [None, ""])
        region = region.group(1) if hasattr(region, "group") else ""
        agent = agent.group(1) if hasattr(agent, "group") else ""
        cid = f"{region}_{agent}"
        short_to_node[short] = cid
        roles[cid] = "GLOBAL" if role == "global" else "REGION"

    edges = []
    if tdata.exists():
        try:
            links = json.load(open(tdata)).get("links", [])
        except Exception:
            links = []
        for lk in links:
            ep = lk.get("endpoints", {})
            an = ep.get("a", {}).get("node")
            zn = ep.get("z", {}).get("node")
            a_cid = short_to_node.get(an)
            z_cid = short_to_node.get(zn)
            if not a_cid or not z_cid:
                continue
            a_norm, z_norm = sorted([norm(a_cid), norm(z_cid)])
            kind = "peer" if (roles.get(a_cid) == "REGION" and roles.get(z_cid) == "REGION") else "uplink"
            edges.append({"a_norm": a_norm, "z_norm": z_norm,
                          "a": a_cid, "z": z_cid, "kind": kind})
    return edges, roles


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------

def to_float(v, default=0.0):
    try:
        if v is None:
            return default
        return float(v)
    except (ValueError, TypeError):
        return default


def norm(name):
    """Canonical node key: hyphens -> underscores (netlink names use underscores)."""
    return name.replace("-", "_")


def flatten_metrics(node_inv):
    """Turn one node inventory's metrics_by_source into a flat, typed model.

    Returns dict: {jvm, sys, net, disk, links, counters, plugins, bundles}.
    """
    out = {
        "jvm": {}, "sys": {}, "net": {}, "disk": {},
        "links": {},           # peer_key -> {rtt_ms, jitter_ms, tx_mbps, rx_mbps, sendlat_ms, backlog, cost}
        "counters": {},        # controller/regional/global/cep scalar counters
        "plugins": [],         # plugin bundle group names (repo/wsapi/...)
    }
    mbs = node_inv.get("metrics_by_source", {}) or {}
    for src, groups in mbs.items():
        if not isinstance(groups, dict):
            continue
        bundle = src.split(":", 1)[1] if ":" in src else src
        for gname, metrics in groups.items():
            if not isinstance(metrics, list):
                continue
            # record which plugin bundles are present (non-controller, non-sysinfo groups)
            if gname in ("repo", "wsapi", "stunnel", "executor", "filerepo"):
                if gname not in out["plugins"]:
                    out["plugins"].append(gname)
            for m in metrics:
                name = m.get("name")
                val = m.get("value", m.get("count"))
                if name is None:
                    continue
                if gname == "netlink":
                    mm = re.match(r"link\.(.+)\.(rtt_ms|jitter_ms|tx_mbps|rx_mbps|sendlat_ms|backlog)$", name)
                    if mm:
                        peer, metric = mm.group(1), mm.group(2)
                        out["links"].setdefault(peer, {})[metric] = to_float(val)
                elif gname == "jvm":
                    out["jvm"][name] = to_float(val)
                elif gname in ("system", "memory", "processor", "network"):
                    out["sys"][name] = to_float(val)
                elif gname == "disk":
                    out["disk"][name] = to_float(val)
                elif gname in ("controller", "regional", "global", "cep"):
                    out["counters"][name] = to_float(val)
    # derived composite link cost (per CONTAINERLAB_SIMULATION.md cost model)
    for peer, lk in out["links"].items():
        rtt = lk.get("rtt_ms", 0.0)
        sendlat = lk.get("sendlat_ms", 0.0)
        backlog = lk.get("backlog", 0.0)
        tx = lk.get("tx_mbps", 0.0)
        lk["cost"] = round(rtt + sendlat + backlog * 0.1 + (50.0 / tx if tx > 0.01 else 0.0), 2)
    if "sysinfo" not in out["plugins"] and out["sys"]:
        out["plugins"].append("sysinfo")
    return out


def collect_nodes(inv, acc, region_names):
    """Recursively walk inventory (root + children) collecting per-node models."""
    node = inv.get("node")
    if node:
        region, agent = split_node(node, region_names)
        acc[node] = {
            "id": node,
            "region": region,
            "agent": agent,
            "metrics": flatten_metrics(inv),
        }
    for _, child in (inv.get("children") or {}).items():
        collect_nodes(child, acc, region_names)


def split_node(node_str, region_names):
    """Split 'region_agent' where region may itself contain '_' after hyphen->'_' subst.

    We know the region names, so pick the region that is a prefix of node_str + '_'.
    """
    best = None
    for rn in region_names:
        pref = rn + "_"
        if node_str.startswith(pref) and (best is None or len(rn) > len(best)):
            best = rn
    if best:
        return best, node_str[len(best) + 1:]
    # fallback: split on last underscore
    if "_" in node_str:
        r, a = node_str.rsplit("_", 1)
        return r, a
    return node_str, node_str


# ---------------------------------------------------------------------------
# poller
# ---------------------------------------------------------------------------

class MeshPoller:
    def __init__(self, host, port, key, interval, history=180):
        self.host = host
        self.port = port
        self.key = key
        self.interval = interval
        self.history_len = history
        self.configured_edges, self.configured_roles = discover_configured_edges()
        self._lock = threading.Lock()
        self._snapshot = {
            "ok": False, "ts": 0, "host": host, "port": port,
            "error": "starting up", "regions": [], "nodes": [], "links": [],
            "summary": {}, "poll_ms": 0,
        }
        # link_id -> deque[(ts, rtt_ms, cost)]
        self._history = defaultdict(lambda: deque(maxlen=self.history_len))
        self._stop = threading.Event()

    def snapshot(self):
        with self._lock:
            snap = dict(self._snapshot)
        snap["tunnels"] = tunnel_snapshot()   # merged from the PUSHED stunnel_trace stream
        # graph from the PUSHED link-state when the getnetworkstate POLL is empty/starved (push > pull).
        polled = snap.get("network") or {}
        if not (polled.get("nodes")):
            pv = pushed_route_view()
            if pv:
                snap["network"] = pv
        return snap

    def history(self):
        with self._lock:
            return {lid: list(dq) for lid, dq in self._history.items()}

    def start(self):
        t = threading.Thread(target=self._loop, daemon=True)
        t.start()

    def stop(self):
        self._stop.set()

    def _loop(self):
        while not self._stop.is_set():
            t0 = time.time()
            try:
                snap = self._poll_once()
                snap["poll_ms"] = int((time.time() - t0) * 1000)
                with self._lock:
                    self._snapshot = snap
                    self._record_history(snap)
            except Exception as e:  # keep last-good snapshot, mark degraded
                with self._lock:
                    self._snapshot = dict(self._snapshot)
                    self._snapshot["ok"] = False
                    self._snapshot["error"] = f"{type(e).__name__}: {e}"
                    self._snapshot["ts"] = time.time()
            self._stop.wait(self.interval)

    def _record_history(self, snap):
        ts = snap["ts"]
        for lk in snap["links"]:
            self._history[lk["id"]].append([ts, lk["rtt_ms"], lk["cost"]])

    def _poll_once(self):
        c = clientlib(self.host, self.port, self.key)
        if not c.connect():
            raise ConnectionError(f"cannot connect to wss://{self.host}:{self.port}")
        try:
            gc = c.globalcontroller
            regions_raw = gc.get_region_list() or []
            region_names = sorted({r.get("name") for r in regions_raw if r.get("name")})

            inv = gc.get_metric_inventory(scope="global", include_plugins=True,
                                          include_resource=True, timeout=90.0) or {}
            # global agent detail (fast + reliable); edge agent lists are skipped by design
            try:
                gagents = gc.get_agent_list(dst_region="global-region") or []
            except Exception:
                gagents = []

            nodes_acc = {}
            collect_nodes(inv, nodes_acc, region_names)

            # DYNAMIC routing state: the global's learned mesh graph (edges w/ rtt/cost/conns) +
            # path choices, pushed into its RouteView via the dataplane. This is what makes the
            # dashboard show links/paths changing (inference, scaling, re-selection) live.
            self._netstate = self._get_network_state(c)
            self._coords = self._get_coordinators(c)
            return self._build_model(regions_raw, region_names, nodes_acc, gagents, inv)
        finally:
            try:
                c.close()
            except Exception:
                pass

    def _build_model(self, regions_raw, region_names, nodes_acc, gagents, inv):
        # region model: fold local + bridged entries
        regions = {}
        for r in regions_raw:
            name = r.get("name")
            if not name:
                continue
            entry = regions.setdefault(name, {
                "name": name, "local": False, "bridged": False,
                "agents": 0, "bridged_agents": [],
            })
            if r.get("type") == "local":
                entry["local"] = True
                entry["agents"] = max(entry["agents"], int(to_float(r.get("agents"), 0)))
            elif r.get("type") == "bridged":
                entry["bridged"] = True
                ba = r.get("bridged_agent")
                if ba and ba not in entry["bridged_agents"]:
                    entry["bridged_agents"].append(ba)

        # agent detail lookup from global agent list
        agent_detail = {}
        for a in gagents:
            aid = a.get("agent_id")
            if aid:
                mode = ""
                try:
                    mode = json.loads(a.get("status_desc", "{}")).get("mode", "")
                except Exception:
                    pass
                agent_detail[(a.get("region_id"), aid)] = {
                    "environment": a.get("environment"),
                    "location": a.get("location"),
                    "platform": a.get("platform"),
                    "plugins_count": int(to_float(a.get("plugins"), 0)),
                    "mode": mode,
                }

        # node models
        nodes = []
        node_by_norm = {}
        for nid, n in nodes_acc.items():
            m = n["metrics"]
            jvm = m["jvm"]
            sysm = m["sys"]
            disk = m["disk"]
            mem_total = sysm.get("memory.total", 0.0)
            mem_avail = sysm.get("memory.available", 0.0)
            disk_total = disk.get("disk.total", 0.0)
            disk_avail = disk.get("disk.available", 0.0)
            detail = agent_detail.get((n["region"], n["agent"]), {})
            role = (self.configured_roles.get(nid) or detail.get("mode")
                    or self._infer_role(n["region"], inv))
            node = {
                "id": nid,
                "region": n["region"],
                "agent": n["agent"],
                "role": role,
                "plugins": sorted(set(m["plugins"]) | {"controller"}),
                "plugins_count": detail.get("plugins_count", len(m["plugins"])),
                "environment": detail.get("environment", "Linux"),
                "location": detail.get("location", ""),
                "health": {
                    # PER-NODE cpu: this JVM's own CPU (process.cpu.usage, 0-1 of total capacity) x cores
                    # = cores used (docker-stats basis, 1.0 == one full core). NOT the host-wide system load
                    # (which reads identically in every container because /proc is not cgroup-scoped).
                    "cpu_load": round((sysm.get("process.cpu.usage", jvm.get("process.cpu.usage", 0.0)) or 0.0)
                                      * (int(sysm.get("system.cpu.count", jvm.get("system.cpu.count", 1.0)) or 1)), 3),
                    # host-wide system CPU % and load, clearly separate (shared across all containers on this host)
                    "sys_cpu_pct": round((sysm.get("system.cpu.usage", sysm.get("system.cpu.load", 0.0)) or 0.0) * 100, 1),
                    "load_avg_1m": round(sysm.get("system.load.average.1m", 0.0), 2),
                    "cpu_count": int(sysm.get("system.cpu.count", jvm.get("system.cpu.count", 0.0)) or 0),
                    "jvm_mem_used_mb": round(jvm.get("jvm.memory.used", 0.0) / 1e6, 1),
                    "jvm_mem_committed_mb": round(jvm.get("jvm.memory.committed", 0.0) / 1e6, 1),
                    "jvm_threads_live": int(jvm.get("jvm.threads.live", 0.0)),
                    "jvm_threads_peak": int(jvm.get("jvm.threads.peak", 0.0)),
                    "jvm_classes": int(jvm.get("jvm.classes.loaded", 0.0)),
                    "mem_used_pct": round((1 - mem_avail / mem_total) * 100, 1) if mem_total else 0.0,
                    "mem_total_gb": round(mem_total / 1e9, 1),
                    "mem_used_gb": round((mem_total - mem_avail) / 1e9, 1),
                    "disk_used_pct": round((1 - disk_avail / disk_total) * 100, 1) if disk_total else 0.0,
                    "disk_total_gb": round(disk_total / 1e9, 1),
                    "uptime_s": int(sysm.get("system.uptime.seconds", 0.0)),
                    "thread_count": int(sysm.get("system.thread.count", 0.0)),
                    "net_sent_kb": round(sysm.get("net.bytes.sent", 0.0) / 1e3, 1),
                    "net_recv_kb": round(sysm.get("net.bytes.recv", 0.0) / 1e3, 1),
                },
                "counters": {k: round(v, 3) for k, v in m["counters"].items()},
                "raw_links": m["links"],
            }
            nodes.append(node)
            node_by_norm[norm(nid)] = node

        # ---- edges: seed with configured mesh, then overlay measured netlink telemetry ----
        links = []
        seen = {}

        def _edge(a, b, kind="uplink"):
            a, b = sorted([a, b])
            lid = f"{a}::{b}"
            rec = seen.get(lid)
            if rec is None:
                rec = {
                    "id": lid,
                    "a": self._pretty(a, node_by_norm),
                    "b": self._pretty(b, node_by_norm),
                    "a_norm": a, "b_norm": b, "kind": kind,
                    "rtt_ms": 0.0, "jitter_ms": 0.0, "tx_mbps": 0.0,
                    "rx_mbps": 0.0, "sendlat_ms": 0.0, "backlog": 0.0, "cost": 0.0,
                    "measured": False, "measured_by": [],
                }
                seen[lid] = rec
                links.append(rec)
            return rec

        # 1. configured edges (intended mesh, incl. redundant peer bridges w/o telemetry)
        for ce in self.configured_edges:
            _edge(ce["a_norm"], ce["z_norm"], ce["kind"])

        # 2. measured telemetry from each node's netlink group
        for n in nodes:
            src_norm = norm(n["id"])
            for peer_key, lk in n["raw_links"].items():
                if peer_key == src_norm:
                    continue  # self / local broker — not an inter-node edge
                a, b = sorted([src_norm, peer_key])
                rec = _edge(a, b)
                rec["measured"] = True
                # take the max (worst) of directional measurements
                rec["rtt_ms"] = max(rec["rtt_ms"], lk.get("rtt_ms", 0.0))
                rec["jitter_ms"] = max(rec["jitter_ms"], lk.get("jitter_ms", 0.0))
                rec["tx_mbps"] = max(rec["tx_mbps"], lk.get("tx_mbps", 0.0))
                rec["rx_mbps"] = max(rec["rx_mbps"], lk.get("rx_mbps", 0.0))
                rec["sendlat_ms"] = max(rec["sendlat_ms"], lk.get("sendlat_ms", 0.0))
                rec["backlog"] = max(rec["backlog"], lk.get("backlog", 0.0))
                rec["cost"] = max(rec["cost"], lk.get("cost", 0.0))
                rec["measured_by"].append(n["id"])

        for lk in links:
            lk["rtt_ms"] = round(lk["rtt_ms"], 2)
            lk["jitter_ms"] = round(lk["jitter_ms"], 3)
            if not lk["measured"]:
                lk["quality"] = "unknown"
            else:
                lk["quality"] = ("bad" if lk["rtt_ms"] >= 80 else
                                 "warn" if lk["rtt_ms"] >= 30 else "good")
            lk["redundant"] = (lk.get("kind") == "peer")

        # strip raw_links from node payload (kept only for edge building)
        for n in nodes:
            n.pop("raw_links", None)

        # MULTI-GLOBAL: the legacy DB node-list only knows the OBSERVER global (Cresco's global DB has no
        # global<->global registration), so it reports a single global. The real coordinator set lives in the
        # pushed RouteView graph. Merge every node the graph knows -- especially the OTHER globals -- so the
        # dashboard reflects all coordinators, not just the one we happen to poll.
        existing = {norm(n["id"]) for n in nodes}
        for gnode in ((self._netstate or {}).get("nodes") or []):
            nid = gnode.get("node")
            if not nid:
                continue
            nn = norm(nid)
            grole = gnode.get("role", "region")
            uirole = "GLOBAL" if grole == "global" else ("AGENT" if grole == "agent" else "REGION")
            if nn in existing:
                if grole == "global":
                    for n in nodes:
                        if norm(n["id"]) == nn:
                            n["role"] = "GLOBAL"
                continue
            nodes.append({
                "id": nid, "region": gnode.get("region", ""), "agent": nid.split("_", 1)[-1],
                "role": uirole, "plugins": ["controller"], "plugins_count": 1,
                "environment": "Linux", "location": "", "graph_only": True,
                "health": {}, "counters": {},
            })
            existing.add(nn)

        # summary
        total_plugins = sum(n["plugins_count"] for n in nodes)
        worst = max(links, key=lambda x: x["rtt_ms"], default=None)
        reachable = 0
        for n in nodes:
            reachable = max(reachable, int(n["counters"].get("reachable.agent.count", 0)))
        summary = {
            "regions": len([r for r in regions.values() if r["local"]]),
            "nodes": len(nodes),
            "agents": len(nodes),
            "plugins": total_plugins,
            "links": len(links),
            "reachable_agents": reachable,
            "worst_rtt_ms": worst["rtt_ms"] if worst else 0.0,
            "worst_link": (worst["a"] + " ↔ " + worst["b"]) if worst else "—",
            "links_degraded": len([l for l in links if l["quality"] in ("warn", "bad")]),
            "links_measured": len([l for l in links if l["measured"]]),
            "peer_links": len([l for l in links if l.get("kind") == "peer"]),
        }

        return {
            "ok": True, "ts": time.time(), "host": self.host, "port": self.port,
            "error": None,
            "regions": sorted(regions.values(), key=lambda r: (not r["local"], r["name"])),
            "nodes": sorted(nodes, key=lambda n: (n["role"] != "GLOBAL", n["id"])),
            "links": sorted(links, key=lambda l: -l["rtt_ms"]),
            "summary": summary,
            "network": getattr(self, "_netstate", None),
            "coordinators": getattr(self, "_coords", None),
        }

    def _get_network_state(self, c):
        """Fetch the global's live RouteView graph + path choices via the getnetworkstate action."""
        try:
            r = c.messaging.global_controller_msgevent(True, "EXEC", {"action": "getnetworkstate"}, 15)
            if r and "networkstate" in r:
                return json.loads(r["networkstate"])
        except Exception as e:
            print(f"getnetworkstate failed: {e}", file=sys.stderr)
        return None

    def _get_coordinators(self, c):
        """Fetch the multi-global coordinator set + consensus state via the getcoordinators action."""
        try:
            r = c.messaging.global_controller_msgevent(True, "EXEC", {"action": "getcoordinators"}, 15)
            if r and "coordinators" in r:
                coords = [x for x in (r.get("coordinators", "") or "").split(",") if x]
                return {
                    "coordinators": coords,
                    "count": len(coords),
                    "leader": r.get("leader"),
                    "epoch": int(r.get("epoch", 0) or 0),
                    "live": int(r.get("live_coordinators", 0) or 0),
                    "quorum": int(r.get("quorum", 0) or 0),
                    "has_quorum": str(r.get("has_quorum", "")).lower() == "true",
                    "rejected_stale_epochs": int(r.get("rejected_stale_epochs", 0) or 0),
                }
        except Exception as e:
            print(f"getcoordinators failed: {e}", file=sys.stderr)
        return None

    def _pretty(self, norm_key, node_by_norm):
        n = node_by_norm.get(norm_key)
        return n["id"] if n else norm_key.replace("_", "-")

    def _is_peer_link(self, lk):
        # region<->region (neither endpoint is the global controller) = redundant peer bridge
        return "global" not in lk["a_norm"].lower() and "global" not in lk["b_norm"].lower()

    def _infer_role(self, region, inv):
        if region == inv.get("node", "").rsplit("_", 1)[0] or region == "global-region":
            return "GLOBAL"
        return "REGION"


# ---------------------------------------------------------------------------
# http server
# ---------------------------------------------------------------------------

POLLER = None

# ---------------------------------------------------------------------------
# PUSHED stunnel trace stream (subscribe, do not poll). Each tunnel's existence beacon
# (type=tunnel) and its live traffic trace (hops + bits_per_second) arrive on the
# cresco_msg_type='stunnel_trace' dataplane stream; we merge them by stunnel_id.
# ---------------------------------------------------------------------------
_TUNNELS = {}
_TUN_LOCK = threading.Lock()
_TUN_TTL = 15.0   # seconds before an unheard tunnel is dropped

def _on_trace(message):
    try:
        d = json.loads(message) if isinstance(message, str) else message
        sid = d.get("stunnel_id")
        if not sid:
            return
        with _TUN_LOCK:
            t = _TUNNELS.get(sid, {})
            # existence/beacon fields
            for k in ("src_region", "src_agent", "dst_region", "dst_agent", "src_port",
                      "dst_host", "dst_port", "status", "clients"):
                if d.get(k) is not None:
                    t[k] = d[k]
            # live traffic fields (only overwrite when this message carries them)
            if d.get("hops"):
                t["hops"] = d["hops"]
            if d.get("bits_per_second") is not None and d.get("type") != "tunnel":
                t["bits_per_second"] = d["bits_per_second"]
                t["direction"] = d.get("direction", t.get("direction"))
            if d.get("total_bytes") is not None:
                t["total_bytes"] = d["total_bytes"]
            t["stunnel_id"] = sid
            t["_ts"] = time.time()
            _TUNNELS[sid] = t
    except Exception:
        pass

def tunnel_snapshot():
    now = time.time()
    with _TUN_LOCK:
        for sid in [s for s, t in _TUNNELS.items() if now - t.get("_ts", 0) > _TUN_TTL]:
            _TUNNELS.pop(sid, None)
        out = []
        for t in _TUNNELS.values():
            o = {k: v for k, v in t.items() if k != "_ts"}
            # a beacon with no recent traffic reads as 0 b/s (idle but existing)
            if now - t.get("_ts", 0) > 6:
                o["bits_per_second"] = "0"
            out.append(o)
        return sorted(out, key=lambda x: x.get("stunnel_id", ""))

# ---------------------------------------------------------------------------
# PUSHED link-state (route_lsa). Building the topology graph from the same pushed LSAs the mesh
# gossips means the graph SURVIVES heavy load — unlike the getnetworkstate POLL, which starves when
# a global's broker is busy forwarding tunnel bytes (push scales, pull does not).
# ---------------------------------------------------------------------------
_LSA = {}
_LSA_LOCK = threading.Lock()
_LSA_TTL = 60.0

def _on_lsa(message):
    try:
        d = json.loads(message) if isinstance(message, str) else message
        node = d.get("node")
        if not node:
            return
        edges = []
        paths = d.get("edgePaths") or []
        nums = d.get("edgesNum") or []
        for k in range(min(len(paths), len(nums))):
            srtt, cost, conns = (nums[k] + [0, 0, 0])[:3]
            edges.append({"from": node, "to": paths[k],
                          "rtt": round(srtt, 1), "cost": round(cost, 1), "conns": int(conns)})
        with _LSA_LOCK:
            _LSA[node] = {"node": node, "region": d.get("region"), "role": d.get("role"),
                          "edges": edges, "_ts": time.time()}
    except Exception:
        pass

def pushed_route_view():
    now = time.time()
    with _LSA_LOCK:
        for n in [x for x, v in _LSA.items() if now - v.get("_ts", 0) > _LSA_TTL]:
            _LSA.pop(n, None)
        nodes, edges = [], []
        for v in _LSA.values():
            nodes.append({"node": v["node"], "region": v["region"], "role": v["role"]})
            edges.extend(v["edges"])
        return {"nodes": nodes, "edges": edges, "routes": [], "observer": None, "pushed": True} if nodes else None

def start_tunnel_stream(host, port, key):
    def run():
        while True:
            try:
                c = clientlib(host, port, key)
                if not c.connect():
                    time.sleep(5); continue
                c.get_dataplane("cresco_msg_type='stunnel_trace'", _on_trace).connect()
                c.get_dataplane("cresco_msg_type='route_lsa'", _on_lsa).connect()
                print(f"[streams] subscribed to pushed stunnel_trace + route_lsa on {host}", file=sys.stderr)
                while True:
                    time.sleep(30)
            except Exception as e:
                print(f"[streams] error: {e}; reconnecting", file=sys.stderr)
                time.sleep(5)
    th = threading.Thread(target=run, daemon=True)
    th.start()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass  # quiet

    def _send(self, code, body, ctype="application/json"):
        if isinstance(body, (dict, list)):
            body = json.dumps(body).encode()
        elif isinstance(body, str):
            body = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        # never let a browser serve a stale dashboard (was causing "dashboard is broken" after JS fixes)
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            html = (HERE / "index.html").read_text()
            return self._send(200, html, "text/html; charset=utf-8")
        if self.path.startswith("/api/snapshot"):
            return self._send(200, POLLER.snapshot())
        if self.path.startswith("/api/history"):
            return self._send(200, POLLER.history())
        if self.path.startswith("/api/health"):
            snap = POLLER.snapshot()
            return self._send(200, {"ok": snap["ok"], "ts": snap["ts"], "error": snap["error"]})
        return self._send(404, {"error": "not found"})


def main():
    global POLLER
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="172.20.20.3", help="Cresco global controller host")
    ap.add_argument("--port", type=int, default=8282, help="Cresco wss port")
    ap.add_argument("--key", default="test-service-key-0001")
    ap.add_argument("--serve-port", type=int, default=8900, help="dashboard http port")
    ap.add_argument("--interval", type=float, default=8.0, help="poll interval seconds")
    args = ap.parse_args()

    POLLER = MeshPoller(args.host, args.port, args.key, args.interval)
    POLLER.start()
    start_tunnel_stream(args.host, args.port, args.key)   # PUSH subscription to stunnel traces

    srv = ThreadingHTTPServer(("0.0.0.0", args.serve_port), Handler)
    print(f"Cresco dashboard: http://0.0.0.0:{args.serve_port}  "
          f"(polling {args.host}:{args.port} every {args.interval}s)")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        POLLER.stop()
        srv.shutdown()


if __name__ == "__main__":
    main()
