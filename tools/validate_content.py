#!/usr/bin/env python3
"""Validate shipped and example content against the JSON Schemas in src/main/resources/schemas.

Usage:
    python3 -m pip install jsonschema
    python3 tools/validate_content.py [path ...]

With no arguments every data/<namespace>/{inks,shows,fireworks} file is checked. Paths given
on the command line are checked against the schema named by their directory, so a data pack
outside this repository can be linted with the same schemas.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    from jsonschema import Draft202012Validator
    from referencing import Registry, Resource
    from referencing.jsonschema import DRAFT202012
except ImportError:  # pragma: no cover - dependency hint
    sys.exit("missing dependency: python3 -m pip install jsonschema")

ROOT = Path(__file__).resolve().parent.parent
SCHEMA_DIR = ROOT / "src" / "main" / "resources" / "schemas"
DATA_DIR = ROOT / "src" / "main" / "resources" / "data"

# Directory name -> schema file. Keep in sync with ContentStore / DefinitionStore.
KINDS = {
    "inks": "ink.schema.json",
    "shows": "show.schema.json",
    "fireworks": "firework.schema.json",
}


def load_schemas() -> dict[str, dict]:
    schemas: dict[str, dict] = {}
    for path in sorted(SCHEMA_DIR.glob("*.schema.json")):
        try:
            schemas[path.name] = json.loads(path.read_text(encoding="utf-8"))
        except OSError as error:
            sys.exit(f"cannot read {path}: {error}")
        except json.JSONDecodeError as error:
            sys.exit(f"{path} is not valid JSON: {error}")
    if not schemas:
        sys.exit(f"no schemas found in {SCHEMA_DIR}")
    # The files deliberately carry no $id so an editor resolves "./common.schema.json"
    # against the schema file on disk instead of fetching a URL. The validator needs a
    # base URI, so give it one here without touching the shipped documents.
    for name, schema in schemas.items():
        schema["$id"] = f"https://magic-firework.dev/schemas/{name}"
    return schemas


def build_registry(schemas: dict[str, dict]) -> Registry:
    resources = []
    for schema in schemas.values():
        resources.append((schema["$id"], Resource.from_contents(schema, default_specification=DRAFT202012)))
    return Registry().with_resources(resources)


def kind_of(path: Path) -> str | None:
    """Return the schema file for a path by looking at its parent directory."""
    for directory, schema in KINDS.items():
        if path.parent.name == directory:
            return schema
    return None


def targets(arguments: list[str]) -> list[Path]:
    if arguments:
        files = [Path(argument).resolve() for argument in arguments]
    else:
        files = [
            path
            for directory in KINDS
            for path in DATA_DIR.glob(f"*/{directory}/*.json")
        ]
    return sorted(files)


def describe(error) -> list[str]:
    """One error, plus the most useful nested causes for anyOf/oneOf failures."""
    lines = [_line(error)]
    for nested in _leaves(error.context):
        line = _line(nested)
        if line not in lines:
            lines.append(line)
        if len(lines) >= 5:
            break
    return lines


def _leaves(errors):
    for error in errors:
        if error.context:
            yield from _leaves(error.context)
        else:
            yield error


def _line(error) -> str:
    location = "/".join(str(part) for part in error.absolute_path) or "<root>"
    return f"{location}: {error.message}"


def main() -> int:
    schemas = load_schemas()
    registry = build_registry(schemas)
    validators = {
        name: Draft202012Validator(schema, registry=registry) for name, schema in schemas.items()
    }

    checked = 0
    failures = 0
    for path in targets(sys.argv[1:]):
        schema_name = kind_of(path)
        if schema_name is None or schema_name not in validators:
            print(f"skip  {path} (no schema for this directory)")
            continue
        checked += 1
        try:
            instance = json.loads(path.read_text(encoding="utf-8"))
        except OSError as error:
            failures += 1
            print(f"FAIL  {path}: cannot read: {error}")
            continue
        except json.JSONDecodeError as error:
            failures += 1
            print(f"FAIL  {path}: not valid JSON: {error}")
            continue
        errors = sorted(validators[schema_name].iter_errors(instance), key=lambda e: list(e.absolute_path))
        if not errors:
            print(f"ok    {path}")
            continue
        failures += 1
        print(f"FAIL  {path}")
        for error in errors:
            for line in describe(error):
                print(f"      {line}")

    print(f"\n{checked - failures}/{checked} files valid")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
