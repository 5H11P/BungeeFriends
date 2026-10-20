"""Check the Maven plugin artifacts and shared protocol library without a server."""

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
]
PROTOCOL_MODULE = ("protocol", "BungeeFriends-Protocol-2.4.jar", "net/simplyrin/bungeefriends/api/protocol/Protocol.class")
PROTOCOL_CLASS = "net/simplyrin/bungeefriends/api/protocol/Protocol.class"


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

protocol_path = ROOT / PROTOCOL_MODULE[0] / "target" / PROTOCOL_MODULE[1]
with ZipFile(protocol_path) as archive:
    names = set(archive.namelist())
    assert PROTOCOL_MODULE[2] in names, f"missing {PROTOCOL_MODULE[2]} in {protocol_path}"
    assert archive.read("LICENSE.md") == LICENSE, f"wrong LICENSE.md in {protocol_path}"
    classes = [name for name in names if name.endswith(".class")]
    majors = {unpack_from(">H", archive.read(name), 6)[0] for name in classes}
    assert majors and max(majors) <= 52, f"Java version exceeds 8 in {protocol_path}: {majors}"
    assert not any(name.startswith("net/_5h11p/") for name in names), f"old package in {protocol_path}"
    assert not any(name.endswith(("plugin.yml", "bungee.yml")) for name in names), "protocol library must not declare a server plugin entry"
    print(f"OK protocol: {PROTOCOL_MODULE[1]}, class-major={max(majors)}")

for module, filename, _ in ARTIFACTS:
    if module not in {"bungee-api", "spigot-api"}:
        continue
    path = ROOT / module / "target" / filename
    with ZipFile(path) as archive:
        assert PROTOCOL_CLASS in archive.namelist(), f"missing shaded protocol class in {path}"

print("Artifact check passed: four JARs, plugin entries, protocol packaging, Fork license, Java 8 bytecode")
