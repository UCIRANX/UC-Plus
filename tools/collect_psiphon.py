#!/usr/bin/env python3
"""Downloads Psiphon's official server list and writes two files for the app:

  server_list_compressed  the list exactly as Psiphon publishes it (zlib-compressed JSON)
  psiphon.txt             one "psiphon://auto#Psiphon" line and one "psiphon://<CC>#<CC>" line per
                          egress region found in the list

The list is only written when it can be read and holds at least one region. Otherwise nothing is
written, so the publish step keeps the last good files.
"""
import argparse
import json
import os
import sys
import urllib.request
import zlib

ORIGINAL_URL = "https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed"


def download(url):
    request = urllib.request.Request(url, headers={"User-Agent": "uc-plus-psiphon"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def entry_regions(blob):
    """Regions of the entries in a server list. Raises ValueError when the list cannot be read."""
    data = json.loads(zlib.decompress(blob).decode("utf-8"))["data"]
    regions = set()
    for line in data.split("\n"):
        line = line.strip()
        if not line:
            continue
        try:
            text = bytes.fromhex(line).decode("utf-8", "replace")
            start, end = text.find("{"), text.rfind("}")
            if start < 0 or end < start:
                continue
            region = json.loads(text[start:end + 1]).get("region", "")
        except ValueError:
            continue
        if isinstance(region, str) and len(region) == 2 and region.isalpha():
            regions.add(region.upper())
    return regions


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    try:
        blob = download(ORIGINAL_URL)
        regions = entry_regions(blob)
    except Exception as error:  # network, zlib or JSON problems: keep the last good files
        print(f"::warning::Psiphon official list unavailable: {error}", file=sys.stderr)
        return 0
    if not regions:
        print("::warning::The Psiphon list has no regions; nothing was written")
        return 0
    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "server_list_compressed"), "wb") as handle:
        handle.write(blob)
    lines = ["psiphon://auto#Psiphon"] + [f"psiphon://{code}#{code}" for code in sorted(regions)]
    with open(os.path.join(args.out, "psiphon.txt"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    print(f"Psiphon: {len(regions)} regions written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
