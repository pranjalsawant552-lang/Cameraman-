#!/usr/bin/env python3
"""
Attendance bridge — pulls punches from biometric machines over the LAN and posts them to the
studio app's generic webhook. Use it for machines that can't push (no "Cloud Server/ADMS"
setting) but are on your network. Works with ZKTeco-protocol machines (TCP port 4370, which is
most ZKTeco / eSSL / Realtime / Mantra-class devices) through the `pyzk` library.

Run on any always-on PC in the studio that can reach both the machine(s) and the server:

    pip install pyzk requests
    python bridge.py --config bridge.json

bridge.json:
{
  "webhook_url": "http://SERVER:8080/api/webhooks/attendance/generic",
  "key": "<webhook secret from Attendance settings>",
  "poll_seconds": 120,
  "devices": [
    {"name": "Front door", "ip": "192.168.1.201", "port": 4370, "serial": "FRONTDOOR"}
  ]
}

Already-sent punches are remembered in bridge_state.json, so restarts don't create duplicates.
Other brands: adapt fetch_punches() — the only brand-specific part. Everything else is generic.
"""
import argparse, json, os, sys, time

import requests
from zk import ZK  # pyzk

STATE_FILE = "bridge_state.json"


def load_state():
    if os.path.exists(STATE_FILE):
        with open(STATE_FILE) as f:
            return json.load(f)
    return {}


def save_state(state):
    with open(STATE_FILE, "w") as f:
        json.dump(state, f)


def fetch_punches(device):
    """Return [(employee_code, datetime)] from one ZK-protocol machine."""
    zk = ZK(device["ip"], port=int(device.get("port", 4370)), timeout=10,
            password=int(device.get("password", 0)))
    conn = None
    try:
        conn = zk.connect()
        return [(str(a.user_id), a.timestamp) for a in conn.get_attendance()]
    finally:
        if conn:
            conn.disconnect()


def post_punch(cfg, code, ts, serial):
    r = requests.post(cfg["webhook_url"], timeout=15, json={
        "key": cfg["key"],
        "employeeCode": code,
        "timestamp": ts.strftime("%Y-%m-%dT%H:%M:%S"),
        "deviceSerial": serial,
    })
    r.raise_for_status()


def run_once(cfg, state):
    for dev in cfg["devices"]:
        name = dev.get("name", dev["ip"])
        serial = dev.get("serial", name)
        last = state.get(dev["ip"])  # ISO timestamp of last punch already sent
        try:
            punches = fetch_punches(dev)
        except Exception as e:
            print(f"[{name}] cannot reach machine: {e}", file=sys.stderr)
            continue
        new = sorted((p for p in punches if last is None or p[1].isoformat() > last),
                     key=lambda p: p[1])
        sent = 0
        for code, ts in new:
            try:
                post_punch(cfg, code, ts, serial)
            except Exception as e:
                print(f"[{name}] server rejected punch, will retry next cycle: {e}", file=sys.stderr)
                break
            state[dev["ip"]] = ts.isoformat()
            sent += 1
        save_state(state)
        print(f"[{name}] {len(punches)} on device, {sent} new sent")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", default="bridge.json")
    ap.add_argument("--once", action="store_true", help="run a single cycle and exit")
    args = ap.parse_args()
    with open(args.config) as f:
        cfg = json.load(f)
    state = load_state()
    while True:
        run_once(cfg, state)
        if args.once:
            break
        time.sleep(int(cfg.get("poll_seconds", 120)))


if __name__ == "__main__":
    main()
