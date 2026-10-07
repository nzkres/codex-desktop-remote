"""Read the relay's local release record without accessing runtime state."""

import json
from pathlib import Path


class ReleaseNotesError(Exception):
    pass


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate field")
        result[key] = value
    return result


def _text(value):
    return isinstance(value, str) and bool(value.strip()) and len(value) <= 4096


def load_catalog():
    """Return the current version and history from the installed releases.json."""
    try:
        path = Path(__file__).with_name("releases.json")
        record = json.loads(path.read_bytes(), object_pairs_hook=_unique_object)
        if not isinstance(record, dict) or set(record) != {"schema", "component", "name", "releases"}:
            raise ValueError("invalid record")
        if type(record["schema"]) is not int or record["schema"] != 1 or record["component"] != "relay" or not _text(record["name"]):
            raise ValueError("invalid record")
        releases = record["releases"]
        if not isinstance(releases, list) or not releases:
            raise ValueError("invalid releases")
        versions = set()
        for release in releases:
            if not isinstance(release, dict) or set(release) != {"version", "date", "summary", "changes", "limitations"}:
                raise ValueError("invalid release")
            if not all(_text(release[key]) for key in ("version", "date", "summary")) or release["version"] in versions:
                raise ValueError("invalid release")
            versions.add(release["version"])
            if not isinstance(release["changes"], list) or not isinstance(release["limitations"], list):
                raise ValueError("invalid release")
            for change in release["changes"]:
                if not isinstance(change, dict) or set(change) != {"title", "experience", "verify"} or not all(_text(value) for value in change.values()):
                    raise ValueError("invalid change")
            if not all(_text(value) for value in release["limitations"]):
                raise ValueError("invalid limitation")
        return {"component": "relay", "version": releases[0]["version"], "releases": releases}
    except (OSError, ValueError, TypeError, UnicodeError):
        # Filesystem paths and parser details must not appear in HTTP or CLI errors.
        raise ReleaseNotesError("release_notes_unavailable") from None


def format_history(catalog):
    lines = ["服务器中继版本历史"]
    for release in catalog["releases"]:
        lines.append(f"\n{release['version']}  {release['date']}")
        lines.append(release["summary"])
        for change in release["changes"]:
            lines.append(f"- {change['title']}")
            lines.append(f"  体验：{change['experience']}")
            lines.append(f"  验证：{change['verify']}")
        if release["limitations"]:
            lines.append("限制：")
            lines.extend(f"- {value}" for value in release["limitations"])
    return "\n".join(lines)
