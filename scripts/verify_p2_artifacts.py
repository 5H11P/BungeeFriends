"""Check P2's six Maven artifacts without a running server."""

from pathlib import Path
from struct import unpack_from
from zipfile import ZipFile
import re


ROOT = Path(__file__).resolve().parents[1]
LICENSE = (ROOT / "LICENSE.md").read_bytes()
ARTIFACTS = [
    ("core", "BungeeFriends-2.4.jar", "plugin.yml"),
    ("bungee-api", "BungeeFriends-BungeeAPI-2.4.jar", "bungee.yml"),
    ("spigot-api", "BungeeFriend-SpigotAPI-2.4.jar", "plugin.yml"),
    ("examples/spigot-api-example", "bungeefriends-spigot-api-example-2.4.jar", "plugin.yml"),
    ("examples/party-cmd-example", "bungeefriends-party-cmd-example-2.4.jar", "plugin.yml"),
    ("examples/party-cmd-sender", "bungeefriends-party-cmd-sender-example-2.4.jar", "plugin.yml"),
]


for module, filename, descriptor in ARTIFACTS:
    path = ROOT / module / "target" / filename
    with ZipFile(path) as archive:
        names = set(archive.namelist())
        assert descriptor in names, f"missing {descriptor} in {path}"
        assert archive.read("LICENSE.md") == LICENSE, f"wrong LICENSE.md in {path}"
        info = archive.read(descriptor).decode("utf-8")
        match = re.search(r"(?m)^main:\s*(\S+)\s*$", info)
        assert match, f"missing main in {path}"
        main_class = match.group(1).replace(".", "/") + ".class"
        assert main_class in names, f"missing {main_class} in {path}"
        assert not any(name.startswith("net/_5h11p/") for name in names), f"old package in {path}"
        classes = [name for name in names if name.endswith(".class")]
        majors = {unpack_from(">H", archive.read(name), 6)[0] for name in classes}
        assert majors and max(majors) <= 52, f"Java version exceeds 8 in {path}: {majors}"
        print(f"OK {module}: {filename}, main={match.group(1)}, class-major={max(majors)}")

print("P2 artifact check passed: six JARs, entry classes, Fork license, Java 8 bytecode")
