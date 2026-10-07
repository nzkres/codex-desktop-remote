"""Validate component release catalogs and their generated Chinese histories."""

import argparse
from datetime import date
import json
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
COMPONENTS = ("android", "windows", "relay")
DIRECTORIES = {"android": "android-native", "windows": "windows", "relay": "relay"}
SEMVER = re.compile(
    r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)"
    r"(?:-(alpha|beta|rc)\.(0|[1-9]\d*))?$"
)
STAGES = {"alpha": 0, "beta": 1, "rc": 2}


class CatalogError(ValueError):
    pass


def require_text(value, location):
    if not isinstance(value, str) or not value.strip():
        raise CatalogError(f"{location}: nonempty text required")
    return value.strip()


def version_key(value):
    match = SEMVER.fullmatch(require_text(value, "version"))
    if not match:
        raise CatalogError(f"invalid version: {value!r}")
    major, minor, patch, stage, number = match.groups()
    return (int(major), int(minor), int(patch),
            3 if stage is None else STAGES[stage],
            0 if number is None else int(number))


def validate_catalog(catalog, component):
    if not isinstance(catalog, dict) or catalog.get("schema") != 1:
        raise CatalogError(f"{component}: schema 1 required")
    if catalog.get("component") != component:
        raise CatalogError(f"{component}: component mismatch")
    require_text(catalog.get("name"), f"{component}.name")
    releases = catalog.get("releases")
    if not isinstance(releases, list) or not releases:
        raise CatalogError(f"{component}.releases: nonempty array required")
    seen = set()
    previous = None
    previous_date = None
    previous_code = None
    for index, release in enumerate(releases):
        where = f"{component}.releases[{index}]"
        if not isinstance(release, dict):
            raise CatalogError(f"{where}: object required")
        version = require_text(release.get("version"), f"{where}.version")
        key = version_key(version)
        if version in seen:
            raise CatalogError(f"{where}: duplicate version")
        if previous is not None and key >= previous:
            raise CatalogError(f"{where}: versions must be newest first")
        seen.add(version)
        previous = key
        raw_date = require_text(release.get("date"), f"{where}.date")
        try:
            parsed_date = date.fromisoformat(raw_date)
        except ValueError as error:
            raise CatalogError(f"{where}.date: ISO date required") from error
        if parsed_date.isoformat() != raw_date:
            raise CatalogError(f"{where}.date: ISO date required")
        if previous_date is not None and parsed_date > previous_date:
            raise CatalogError(f"{where}: dates must be newest first")
        previous_date = parsed_date
        require_text(release.get("summary"), f"{where}.summary")
        changes = release.get("changes")
        if not isinstance(changes, list) or not changes:
            raise CatalogError(f"{where}.changes: nonempty array required")
        for change_index, change in enumerate(changes):
            change_where = f"{where}.changes[{change_index}]"
            if not isinstance(change, dict):
                raise CatalogError(f"{change_where}: object required")
            for field in ("title", "experience", "verify"):
                require_text(change.get(field), f"{change_where}.{field}")
        limitations = release.get("limitations")
        if not isinstance(limitations, list) or not limitations:
            raise CatalogError(f"{where}.limitations: nonempty array required")
        for limit_index, limitation in enumerate(limitations):
            require_text(limitation, f"{where}.limitations[{limit_index}]")
        if component == "android":
            code = release.get("versionCode")
            if isinstance(code, bool) or not isinstance(code, int) or code <= 0:
                raise CatalogError(f"{where}.versionCode: positive integer required")
            if previous_code is not None and code >= previous_code:
                raise CatalogError(f"{where}: Android versionCode must increase chronologically")
            previous_code = code
    return catalog


def render_markdown(catalog):
    lines = [f"# {catalog['name']}版本历史", "", "由 releases.json 生成；实际运行与验收状态以根 STATUS.md 为准。", ""]
    for release in catalog["releases"]:
        lines.extend((f"## {release['version']}（{release['date']}）", "", release["summary"], "", "### 更新", ""))
        for change in release["changes"]:
            lines.extend((f"- **{change['title']}**", f"  - 体验：{change['experience']}",
                          f"  - 验证：{change['verify']}"))
        lines.extend(("", "### 限制", ""))
        lines.extend(f"- {limitation}" for limitation in release["limitations"])
        lines.append("")
    return "\n".join(lines)


def check_repository(root, write=False):
    root = Path(root)
    catalogs = {}
    for component in COMPONENTS:
        path = root / DIRECTORIES[component] / "releases.json"
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as error:
            raise CatalogError(f"{component}: releases.json unreadable or invalid") from error
        catalogs[component] = validate_catalog(raw, component)
    try:
        package_version = json.loads((root / "package.json").read_text(encoding="utf-8"))["version"]
    except (OSError, UnicodeError, json.JSONDecodeError, KeyError, TypeError) as error:
        raise CatalogError("package.json: version unreadable or invalid") from error
    if package_version != catalogs["windows"]["releases"][0]["version"]:
        raise CatalogError("package.json.version must equal latest Windows version")
    rendered = {component: render_markdown(catalog) for component, catalog in catalogs.items()}
    for component, expected in rendered.items():
        path = root / "docs" / "versions" / f"{component.upper()}.md"
        if write:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(expected, encoding="utf-8", newline="\n")
        else:
            try:
                actual = path.read_text(encoding="utf-8")
            except (OSError, UnicodeError) as error:
                raise CatalogError(f"{component}: generated history missing") from error
            if actual != expected:
                raise CatalogError(f"{component}: generated history out of date; run --write")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="regenerate docs/versions/*.md")
    args = parser.parse_args()
    try:
        check_repository(ROOT, write=args.write)
    except CatalogError as error:
        print(error, file=sys.stderr)
        return 1
    print("Release catalogs and histories verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
