#!/usr/bin/env python3
"""Check that the semantic content-format contract still covers every op and rule.

The JSON Schemas describe shape; this script guards the page that describes meaning. It is
deliberately a token check, not a prose review: deleting a table row or the height formula
is the failure mode we have already had.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

OPS = ("launch", "wait", "burst", "spawn", "call", "sound", "light", "end")
TOKENS = (
    "flight.ticks",
    "flight.direction.yaw",
    "flight.direction.pitch",
    "v² / 0.16",
    "Hitting a block ends the current",
)


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("docs/content-format.md")
    if not path.is_file():
        print(f"missing {path}")
        return 1
    text = path.read_text(encoding="utf-8")

    missing = [op for op in OPS if not re.search(rf"\b{re.escape(op)}\b", text)]
    missing += [token for token in TOKENS if token not in text]
    if missing:
        print(f"{path} is missing semantic tokens: {', '.join(missing)}")
        return 1

    print(f"ok    {path} ({len(OPS)} ops, {len(TOKENS)} semantic tokens)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
