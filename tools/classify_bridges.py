#!/usr/bin/env python3
"""Labels each Tor bridge with the country its address is in.

Reads <out>/bridges.json (written by collect_bridges.py) and the per-country GeoIP files in
<out>/geo/ (written by build_geo.py). The same files are what the app gives Tor as GeoIPFile
when the user picks an exit country, so a bridge's label and the exit country use one source.

Writes <out>/tor-countries.txt: one "FINGERPRINT CC" line per bridge with a known country.
Bridges whose address is not in any range are left out, so the app shows them under no country.
Domain names (webtunnel) are resolved with DNS from the CI runner.
"""
import argparse
import bisect
import ipaddress
import json
import os
import re
import socket
import sys
from urllib.parse import urlparse

FP_RE = re.compile(r"(?<![0-9A-Fa-f])[0-9A-Fa-f]{40}(?![0-9A-Fa-f])")
TRANSPORTS = ("webtunnel", "obfs4", "vanilla")


def load_ranges(geo_dir):
    """Returns (starts, ends, codes) for IPv4 and for IPv6 as integer ranges, sorted by start."""
    v4, v6 = [], []
    for name in os.listdir(geo_dir):
        if not (name.endswith(".geoip") or name.endswith(".geoip6")):
            continue
        with open(os.path.join(geo_dir, name), encoding="utf-8") as f:
            for raw in f:
                parts = raw.strip().split(",")
                if len(parts) != 3:
                    continue
                try:
                    if name.endswith(".geoip"):
                        v4.append((int(parts[0]), int(parts[1]), parts[2].strip().upper()))
                    else:
                        v6.append((int(ipaddress.IPv6Address(parts[0])),
                                   int(ipaddress.IPv6Address(parts[1])),
                                   parts[2].strip().upper()))
                except ValueError:
                    continue
    return [sorted(v4), sorted(v6)]


def country_of(ip_text, tables, cache):
    if ip_text in cache:
        return cache[ip_text]
    code = ""
    try:
        ip = ipaddress.ip_address(ip_text)
        ranges = tables[0] if ip.version == 4 else tables[1]
        value = int(ip)
        if ranges:
            starts = [r[0] for r in ranges]
            i = bisect.bisect_right(starts, value) - 1
            if i >= 0 and ranges[i][0] <= value <= ranges[i][1]:
                code = ranges[i][2]
    except ValueError:
        pass
    cache[ip_text] = code
    return code


def bridge_host(transport, line):
    if transport == "webtunnel":
        m = re.search(r"url=(\S+)", line)
        return urlparse(m.group(1)).hostname if m else None
    parts = line.split()
    if transport == "obfs4":
        if len(parts) < 2:
            return None
        return parts[1].rsplit(":", 1)[0].strip("[]")
    return parts[0].rsplit(":", 1)[0].strip("[]") if parts else None


def resolve(host, cache):
    if host in cache:
        return cache[host]
    ip = ""
    try:
        ipaddress.ip_address(host)
        ip = host
    except ValueError:
        try:
            ip = socket.gethostbyname(host)
        except OSError:
            ip = ""
    cache[host] = ip
    return ip


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    geo_dir = os.path.join(args.out, "geo")
    if not os.path.isdir(geo_dir):
        sys.exit("[classify] missing geo/ directory; run build_geo.py first")
    with open(os.path.join(args.out, "bridges.json"), encoding="utf-8") as f:
        bridges = json.load(f)["bridges"]

    tables = load_ranges(geo_dir)
    if not tables[0]:
        sys.exit("[classify] no IPv4 ranges loaded")
    country_cache, dns_cache = {}, {}
    labelled = {}
    total = 0
    for transport in TRANSPORTS:
        for line in bridges.get(transport, []):
            total += 1
            m = FP_RE.search(line)
            host = bridge_host(transport, line)
            if not m or not host:
                continue
            ip = resolve(host, dns_cache)
            if not ip:
                continue
            code = country_of(ip, tables, country_cache)
            if len(code) == 2:
                labelled[m.group(0).upper()] = code
    out_path = os.path.join(args.out, "tor-countries.txt")
    with open(out_path, "w", encoding="utf-8") as f:
        for fp in sorted(labelled):
            f.write(f"{fp} {labelled[fp]}\n")
    counts = {}
    for code in labelled.values():
        counts[code] = counts.get(code, 0) + 1
    print(f"[classify] {len(labelled)}/{total} bridges labelled, {len(counts)} countries")


if __name__ == "__main__":
    main()
