#!/usr/bin/env python3
"""Generate YAPPC API reference markdown from route manifest and OpenAPI."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

import yaml

HTTP_METHODS = {"get", "post", "put", "patch", "delete", "options", "head"}


def load_yaml(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(f"Missing YAML source: {path}")
    with path.open("r", encoding="utf-8") as handle:
        data = yaml.safe_load(handle) or {}
    if not isinstance(data, dict):
        raise ValueError(f"Expected mapping in {path}")
    return data


def parse_manifest(data: dict[str, Any]) -> list[dict[str, Any]]:
    routes: list[dict[str, Any]] = []
    for group, entries in data.items():
        if not isinstance(entries, list):
            continue
        for entry in entries:
            if not isinstance(entry, dict) or "method" not in entry or "path" not in entry:
                continue
            routes.append(
                {
                    "group": group,
                    "method": str(entry.get("method", "")).upper(),
                    "path": str(entry.get("path", "")),
                    "operationId": str(entry.get("operationId", "")),
                    "auth": str(entry.get("auth", "")),
                    "scopes": entry.get("scopes") or [],
                    "owner": str(entry.get("owner", group)),
                    "boundary": str(entry.get("boundary", "")),
                    "privacyClassification": str(entry.get("privacyClassification", "")),
                    "auditEventType": str(entry.get("auditEventType", "")),
                }
            )
    return routes


def parse_openapi(data: dict[str, Any]) -> dict[tuple[str, str], dict[str, Any]]:
    routes: dict[tuple[str, str], dict[str, Any]] = {}
    paths = data.get("paths") or {}
    if not isinstance(paths, dict):
        return routes
    for path, methods in paths.items():
        if not isinstance(methods, dict):
            continue
        for method, details in methods.items():
            if str(method).lower() not in HTTP_METHODS or not isinstance(details, dict):
                continue
            routes[(str(method).upper(), str(path))] = {
                "operationId": details.get("operationId", ""),
                "summary": details.get("summary", ""),
                "tags": details.get("tags") or [],
                "security": details.get("security") or [],
            }
    return routes


def markdown_escape(value: Any) -> str:
    text = str(value)
    return text.replace("|", "\\|").replace("\n", " ")


def render(manifest_path: Path, openapi_path: Path) -> str:
    manifest_routes = parse_manifest(load_yaml(manifest_path))
    openapi_routes = parse_openapi(load_yaml(openapi_path))
    manifest_keys = {(route["method"], route["path"]) for route in manifest_routes}
    openapi_keys = set(openapi_routes)

    missing_openapi = sorted(manifest_keys - openapi_keys)
    extra_openapi = sorted(openapi_keys - manifest_keys)
    operation_mismatches = []
    for route in manifest_routes:
        openapi = openapi_routes.get((route["method"], route["path"]))
        if openapi and route["operationId"] != openapi.get("operationId"):
            operation_mismatches.append((route["method"], route["path"], route["operationId"], openapi.get("operationId", "")))

    lines = [
        "# YAPPC API Reference",
        "",
        "Generated from `docs/api/route-manifest.yaml` and `docs/api/openapi.yaml`.",
        "Do not hand-edit route rows; run `python products/yappc/scripts/generate-api-reference.py`.",
        "",
        "## Summary",
        "",
        f"- Manifest routes: {len(manifest_routes)}",
        f"- OpenAPI routes: {len(openapi_routes)}",
        f"- Missing in OpenAPI: {len(missing_openapi)}",
        f"- OpenAPI-only routes outside manifest table: {len(extra_openapi)}",
        f"- Operation ID mismatches: {len(operation_mismatches)}",
        "",
        "## Parity",
        "",
    ]

    if missing_openapi or operation_mismatches:
        lines.append("The generated reference found route parity drift:")
        lines.append("")
        for method, path in missing_openapi:
            lines.append(f"- Missing OpenAPI route: `{method} {path}`")
        for method, path, manifest_op, openapi_op in operation_mismatches:
            lines.append(
                f"- Operation mismatch for `{method} {path}`: manifest `{manifest_op}`, OpenAPI `{openapi_op}`"
            )
    else:
        lines.append("Every route-manifest path/method/operationId is represented in OpenAPI.")

    if extra_openapi:
        lines.extend(
            [
                "",
                "OpenAPI also contains routes outside the route-manifest table. They are not rendered as canonical YAPPC manifest routes here.",
            ]
        )

    lines.extend(
        [
            "",
            "## Routes",
            "",
            "| Method | Path | Operation | Owner | Auth | Scopes | Privacy | Boundary | OpenAPI Tags | Summary |",
            "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
        ]
    )

    for route in manifest_routes:
        openapi = openapi_routes.get((route["method"], route["path"]), {})
        scopes = ", ".join(str(scope) for scope in route["scopes"]) if route["scopes"] else "-"
        tags = ", ".join(str(tag) for tag in openapi.get("tags", [])) if openapi.get("tags") else "-"
        summary = openapi.get("summary") or "-"
        lines.append(
            "| {method} | `{path}` | `{operation}` | {owner} | {auth} | {scopes} | {privacy} | {boundary} | {tags} | {summary} |".format(
                method=markdown_escape(route["method"]),
                path=markdown_escape(route["path"]),
                operation=markdown_escape(route["operationId"]),
                owner=markdown_escape(route["owner"]),
                auth=markdown_escape(route["auth"]),
                scopes=markdown_escape(scopes),
                privacy=markdown_escape(route["privacyClassification"]),
                boundary=markdown_escape(route["boundary"]),
                tags=markdown_escape(tags),
                summary=markdown_escape(summary),
            )
        )

    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default="products/yappc/docs/api/route-manifest.yaml")
    parser.add_argument("--openapi", default="products/yappc/docs/api/openapi.yaml")
    parser.add_argument("--output", default="products/yappc/docs/API_REFERENCE.md")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    content = render(Path(args.manifest), Path(args.openapi))
    output = Path(args.output)

    if args.check:
        existing = output.read_text(encoding="utf-8") if output.exists() else ""
        if existing != content:
            print(f"API reference is stale: {output}", file=sys.stderr)
            print("Run: python products/yappc/scripts/generate-api-reference.py", file=sys.stderr)
            return 1
        print(f"API reference is up to date: {output}")
        return 0

    output.write_text(content, encoding="utf-8")
    print(f"Wrote {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
