#!/usr/bin/env python3
"""Builds per-country GeoIP files for choosing a Tor exit country.

Tor's own GeoIP data (geoip, geoip6 from the Tor source) is split by country code. Each
country gets its own small file in the same format, so the app downloads only the country the
user picks. Output goes to <out>/geo/:

  index.txt        one two-letter code per line, for countries that have IPv4 ranges
  <cc>.geoip       IPv4 ranges of the country
  <cc>.geoip6      IPv6 ranges of the country (only when it has any)

Lines are copied unchanged, so Tor reads them exactly like the full file.
"""
import argparse
import collections
import os
import sys
import urllib.request

SOURCES = [
    "https://raw.githubusercontent.com/torproject/tor/main/src/config/%s",
    "https://github.com/torproject/tor/raw/main/src/config/%s",
    "https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/%s",
    "https://gitlab.torproject.org/tpo/core/tor/-/raw/release-0.4.8/src/config/%s",
    "https://raw.githubusercontent.com/torproject/tor/release-0.4.8/src/config/%s",
    "https://raw.githubusercontent.com/torproject/tor/release-0.4.7/src/config/%s",
    "https://cdn.jsdelivr.net/gh/torproject/tor@main/src/config/%s",
]

MIN_BYTES = {"geoip": 200_000, "geoip6": 50_000}


def fetch(name):
    last = "no source tried"
    for pattern in SOURCES:
        url = pattern % name
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "uc-plus-build"})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read()
            if len(data) >= MIN_BYTES[name]:
                print(f"[geo] {name} from {url} ({len(data)} bytes)")
                return data.decode("utf-8", "replace")
            last = f"{url}: only {len(data)} bytes"
        except Exception as e:  # keep trying the other mirrors
            last = f"{url}: {e}"
    sys.exit(f"[geo] could not download {name}: {last}")


def split(text):
    groups = collections.defaultdict(list)
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(",")
        if len(parts) != 3:
            continue
        code = parts[2].strip().lower()
        if len(code) == 2 and code.isalpha():
            groups[code].append(line)
    return groups


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    geo_dir = os.path.join(args.out, "geo")
    os.makedirs(geo_dir, exist_ok=True)

    v4 = split(fetch("geoip"))
    v6 = split(fetch("geoip6"))
    if not v4:
        sys.exit("[geo] no IPv4 ranges were parsed")

    for code, lines in v4.items():
        with open(os.path.join(geo_dir, f"{code}.geoip"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
    for code, lines in v6.items():
        with open(os.path.join(geo_dir, f"{code}.geoip6"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
    with open(os.path.join(geo_dir, "index.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(v4)) + "\n")
    print(f"[geo] {len(v4)} countries with IPv4 ranges, {len(v6)} with IPv6 ranges")


if __name__ == "__main__":
    main()
