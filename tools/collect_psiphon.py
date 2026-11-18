#!/usr/bin/env python3
"""Writes psiphon.txt for the Psiphon subscription: "psiphon://auto#Psiphon" and one
"psiphon://<CC>#<CC>" line per egress region found in the Psiphon server list.

The list is read the way the app reads it: the GitHub mirror first, then Psiphon's own S3 copy.
The file is zlib-compressed JSON whose "data" field holds one hex-encoded server entry per line;
each decoded entry is a JSON object with a "region" field. When neither source can be read,
nothing is written and the script still exits 0, so the publish step keeps the last good file.
"""
import argparse
import json
import os
import sys
import urllib.request
import zlib

MIRROR_URL = "https://raw.githubusercontent.com/UCIRANX/UC-MAX/main/psiphon-mirror/server_list_compressed"
ORIGINAL_URL = "https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed"


def download(url):
    request = urllib.request.Request(url, headers={"User-Agent": "uc-plus-psiphon"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def entry_regions(blob):
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
    regions = set()
    for name, url in (("mirror", MIRROR_URL), ("s3", ORIGINAL_URL)):
        try:
            regions = entry_regions(download(url))
        except Exception as error:  # network, zlib or JSON problems: try the next source
            print(f"::warning::Psiphon {name} list unavailable: {error}", file=sys.stderr)
            continue
        if regions:
            print(f"Psiphon regions from {name}: {len(regions)}")
            break
    if not regions:
        print("::warning::No Psiphon regions found; psiphon.txt was not written")
        return 0
    os.makedirs(args.out, exist_ok=True)
    lines = ["psiphon://auto#Psiphon"] + [f"psiphon://{code}#{code}" for code in sorted(regions)]
    with open(os.path.join(args.out, "psiphon.txt"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
