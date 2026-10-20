"""Extract reviewable command/config/permission/language/API surface lists.

This is a lexical inventory, not a Java or YAML parser. Review each proposed
classification against the source before implementation.
"""

from pathlib import Path
import re


FORK = Path(__file__).resolve().parents[1]
SOURCE = FORK.parent / "BungeeFriends-And-Party"
OUT = FORK / "docs" / "audit" / "surface_inventory.tsv"


def read(path):
    return path.read_text(encoding="utf-8-sig")


def java_files(root):
    return sorted((root / "src/main/java").rglob("*.java"))


def scan_java(root, pattern):
    found = {}
    for path in java_files(root):
        for match in re.finditer(pattern, read(path)):
            value = match.group(1)
            found.setdefault(value, path.relative_to(root).as_posix())
    return found


def language_keys(path):
    result = {}
    if not path.exists():
        return result
    stack = []
    for line_number, line in enumerate(read(path).splitlines(), 1):
        match = re.match(r"^(\s*)([A-Za-z][A-Za-z0-9_-]*):(?:\s|$)", line)
        if not match:
            continue
        indent, key = len(match.group(1)), match.group(2)
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, key))
        result.setdefault(".".join(part for _, part in stack), f"{path.name}:{line_number}")
    return result


def api_methods(path):
    result = {}
    if not path.exists():
        return result
    for match in re.finditer(r"(?m)^\s*public\s+(?:(?:static|final|synchronized)\s+)*[\w<>?, .\[\]]+?\s+(\w+)\s*\(", read(path)):
        result.setdefault(match.group(1), path.name)
    return result


def write(surface, old, new, disposition, test):
    keys = sorted(set(old) | set(new))
    for key in keys:
        relation = "shared" if key in old and key in new else "source-only" if key in new else "fork-only"
        rows.append((surface, key, relation, old.get(key, "-"), new.get(key, "-"), disposition(relation, key), test))


rows = []
fork_root = FORK / "core" if (FORK / "core/src/main/java").is_dir() else FORK
source_root = SOURCE / "core"
command_pattern = r'new\s+(\w+Command)\s*\(this(?:,\s*"([^"]+)")?'


def commands(path):
    result = {}
    for match in re.finditer(command_pattern, read(path)):
        name = match.group(2) or match.group(1).removesuffix("Command").lower()
        result.setdefault(name, path.relative_to(path.parents[4]).as_posix())
    return result


write("command", commands(fork_root / "src/main/java/net/simplyrin/bungeefriends/Main.java"),
      commands(source_root / "src/main/java/net/_5h11p/bungeefriends/CorePlugin.java"),
      lambda relation, key: "P4: migrate and regression check" if relation != "fork-only" else "P4: retain baseline", "command permission and alias smoke test")

permission_pattern = r'"((?:friends|bungeefriends\.api)\.[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*)"'
write("permission", scan_java(fork_root, permission_pattern), scan_java(source_root, permission_pattern),
      lambda relation, key: "P4: preserve" if relation == "shared" else "P4/P5: add and check defaults" if relation == "source-only" else "P4: retain baseline", "allowed and denied player test")

config_pattern = r'"((?:Plugin|BungeeParties|Player|Parties)\.[A-Za-z0-9_./-]+)"'
write("config-key", scan_java(fork_root, config_pattern), scan_java(source_root, config_pattern),
      lambda relation, key: "P3/P4: map and check default" if relation != "fork-only" else "P4: retain or document retirement", "default, reload and YAML/MySQL mode test")

for name in ("English", "Chinese"):
    write("message-key-" + name.lower(),
          language_keys(fork_root / f"src/main/resources/languages/{name}.yml"),
          language_keys(source_root / f"src/main/resources/{name}.yml"),
          lambda relation, key: "P4: migrate/check translation" if relation != "fork-only" else "P4: retain if still referenced", "compare locale keys and render message")

api_paths = (
    ("bungee-public-api", None, source_root / "src/main/java/net/_5h11p/bungeefriends/api/BungeeFriendsAPI.java"),
    ("spigot-public-api", None, SOURCE / "spigot-api/src/main/java/net/_5h11p/bungeefriends/api/spigot/SpigotFriendsAPI.java"),
)
for surface, old_path, source_path in api_paths:
    write(surface, api_methods(old_path) if old_path else {}, api_methods(source_path),
          lambda relation, key: "P5: redesign contract and migrate", "typed response and lifecycle contract test")

bungee_api = SOURCE / "bungee-api/src/main/java/net/_5h11p/bungeefriends/api/bungee/BungeeAPIPlugin.java"
request_types = {match.group(1): bungee_api.name for match in re.finditer(r'case\s+"([^"]+)"\s*:', read(bungee_api))}
write("plugin-message-request", {}, request_types,
      lambda relation, key: "P5: protocol contract and authorization", "Bungee/Spigot request-response test")

OUT.parent.mkdir(parents=True, exist_ok=True)
with OUT.open("w", encoding="utf-8", newline="\n") as stream:
    stream.write("surface\tkey\trelation\tfork_reference\tsource_reference\tproposed_disposition\tverification\n")
    for row in rows:
        stream.write("\t".join(row) + "\n")
print("inventory rows:", len(rows))
for surface in dict.fromkeys(row[0] for row in rows):
    print(surface, sum(row[0] == surface for row in rows))
