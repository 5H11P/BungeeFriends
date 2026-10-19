"""Create reproducible, read-only inventories for migration review.

Run from the Fork root: python scripts/migration_snapshot.py
The sibling BungeeFriends-And-Party tree is read but never modified.
"""

from collections import Counter
from hashlib import sha256
from pathlib import Path
import re
import subprocess


FORK = Path(__file__).resolve().parents[1]
SOURCE = FORK.parent / "BungeeFriends-And-Party"
OUTPUT = FORK / "docs" / "audit"
FORK_BASELINE = "bc22a63"


def git(repo, *args):
    return subprocess.check_output(["git", "-C", str(repo), *args]).decode("utf-8", "replace")


def files(repo):
    return sorted(
        (p for p in repo.rglob("*") if p.is_file() and not {".git", "target"}.intersection(p.relative_to(repo).parts)),
        key=lambda p: p.relative_to(repo).as_posix(),
    )


def record(path, root):
    data = path.read_bytes()
    return path.relative_to(root).as_posix(), len(data), sha256(data).hexdigest()


def write_tsv(name, header, rows):
    with (OUTPUT / name).open("w", encoding="utf-8", newline="\n") as stream:
        stream.write("\t".join(header) + "\n")
        for row in rows:
            stream.write("\t".join((str(v) if v != "" else "-").replace("\t", " ").replace("\n", " ") for v in row) + "\n")


def baseline_candidate(relative):
    if relative.startswith("core/src/main/"):
        candidate = relative.removeprefix("core/").replace("/net/_5h11p/", "/net/simplyrin/")
        if candidate.endswith("/CorePlugin.java"):
            candidate = candidate[:-len("CorePlugin.java")] + "Main.java"
        return candidate
    if relative == "core/README.md":
        return "README.md"
    if relative in {"README.md", "pom.xml", "LICENSE.md"}:
        return relative
    return ""


def normalize_java(data):
    # Diagnostic only: exact match after mechanical package/main-class changes.
    return (data.replace(b"\r\n", b"\n")
            .replace(b"net._5h11p.bungeefriends", b"net.simplyrin.bungeefriends")
            .replace(b"CorePlugin", b"Main")
            .replace(b"BungeeAPIPlugin", b"BungeeFriendsAPIBungee")
            .replace(b"SpigotAPIPlugin", b"BungeeFriendsAPISpigot"))


def source_head_candidate(relative):
    candidate = relative.replace("/net/_5h11p/", "/net/simplyrin/")
    candidate = candidate.replace("/CorePlugin.java", "/Main.java")
    candidate = candidate.replace("/BungeeAPIPlugin.java", "/BungeeFriendsAPIBungee.java")
    candidate = candidate.replace("/SpigotAPIPlugin.java", "/BungeeFriendsAPISpigot.java")
    if candidate.startswith("examples/") and "/src/main/java/" in candidate:
        candidate = "spigot-api/src/main/java/" + candidate.split("/src/main/java/", 1)[1]
    return candidate


def file_kind(relative):
    name = Path(relative).name
    if name == "dependency-reduced-pom.xml":
        return "generated-build-output"
    if name in {"LICENSE.md", "GPL-3.0.txt"}:
        return "license-text"
    if relative.endswith(".java"):
        return "java-source"
    if relative.endswith((".yml", ".yaml")):
        return "config-or-descriptor"
    if name == "pom.xml":
        return "maven-build"
    if relative.endswith(".md"):
        return "documentation"
    return "other"


def license_header(path):
    try:
        head = path.read_text(encoding="utf-8-sig").splitlines()[:48]
    except UnicodeDecodeError:
        return "unreadable", "", ""
    text = "\n".join(head)
    authors = ", ".join(dict.fromkeys(re.findall(r"(?im)^\s*\*?\s*author\s*:\s*(.+)$", text)))
    copyrights = ", ".join(dict.fromkeys(re.findall(r"(?im)^\s*\*?\s*(Copyright\s*\(c\)[^\n]+)", text)))
    if re.search(r"(?i)license\s*:\s*LGPL\s*v3", text):
        license_name = "LGPL v3 header"
    elif "The MIT License" in text or "Permission is hereby granted, free of charge" in text:
        license_name = "MIT header"
    elif "GNU LESSER GENERAL PUBLIC LICENSE" in text:
        license_name = "LGPL text"
    elif "GNU GENERAL PUBLIC LICENSE" in text:
        license_name = "GPL text"
    else:
        license_name = "no explicit header"
    return license_name, authors, copyrights


def main():
    if not SOURCE.is_dir():
        raise SystemExit(f"Missing source tree: {SOURCE}")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    source_files, fork_files = files(SOURCE), files(FORK)
    source_records = [record(p, SOURCE) for p in source_files]
    baseline_paths = git(FORK, "ls-tree", "-r", "--name-only", FORK_BASELINE).splitlines()
    fork_records = []
    for relative in baseline_paths:
        data = subprocess.check_output(["git", "-C", str(FORK), "show", f"{FORK_BASELINE}:{relative}"])
        fork_records.append((relative, len(data), sha256(data).hexdigest()))
    write_tsv("source_manifest.tsv", ["path", "bytes", "sha256"], source_records)
    write_tsv("fork_baseline_manifest.tsv", ["path", "bytes", "sha256"], fork_records)
    (OUTPUT / "source_git_status.txt").write_text(
        f"HEAD {git(SOURCE, 'rev-parse', 'HEAD').strip()}\n"
        + git(SOURCE, "status", "--porcelain=v1", "--untracked-files=all"),
        encoding="utf-8", newline="\n",
    )
    fork_by_path = {p.relative_to(FORK).as_posix(): p for p in fork_files}
    source_head_paths = set(git(SOURCE, "ls-tree", "-r", "--name-only", "HEAD").splitlines())
    rows = []
    for source_path in source_files:
        relative = source_path.relative_to(SOURCE).as_posix()
        candidate = baseline_candidate(relative)
        target = fork_by_path.get(candidate)
        source_data = source_path.read_bytes()
        if target is None:
            category = "new-vs-fork"
        elif source_data == target.read_bytes():
            category = "identical-to-fork"
        elif source_path.suffix == ".java" and normalize_java(source_data) == target.read_bytes().replace(b"\r\n", b"\n"):
            category = "mechanical-rename-vs-fork"
        else:
            category = "changed-or-restructured"
        head_candidate = source_head_candidate(relative)
        if head_candidate not in source_head_paths:
            source_delta = "new-in-source-worktree"
            head_candidate = "-"
        else:
            old_data = subprocess.check_output(["git", "-C", str(SOURCE), "show", f"HEAD:{head_candidate}"])
            if source_data == old_data:
                source_delta = "unchanged-from-source-head"
            elif source_path.suffix == ".java" and normalize_java(source_data) == old_data.replace(b"\r\n", b"\n"):
                source_delta = "mechanical-package-or-main-rename"
            else:
                source_delta = "changed-from-source-head"
        kind = file_kind(relative)
        if kind == "generated-build-output":
            lineage = "generated-ignore"
        elif kind == "license-text":
            lineage = "license-review"
        elif target is not None:
            lineage = "inherited-fork-candidate"
        elif head_candidate != "-":
            lineage = "source-extension-or-relocated"
        else:
            lineage = "source-worktree-new"
        rows.append((relative, kind, lineage, candidate or "-", category, head_candidate, source_delta))
    write_tsv("file_comparison.tsv", ["source_path", "kind", "lineage", "fork_candidate", "fork_relation", "source_head_candidate", "source_worktree_change"], rows)
    license_rows = []
    for label, root, tree in (("fork", FORK, fork_files), ("source", SOURCE, source_files)):
        for path in tree:
            relative = path.relative_to(root).as_posix()
            if path.suffix == ".java" or path.name in {"LICENSE.md", "GPL-3.0.txt"}:
                license_rows.append((label, relative, *license_header(path)))
    write_tsv("license_headers.tsv", ["tree", "path", "detected_header", "author_field", "copyright_field"], license_rows)
    print("source files:", len(source_records), "fork files:", len(fork_records))
    print("lineage:", dict(Counter(row[2] for row in rows)))
    print("vs fork:", dict(Counter(row[4] for row in rows)))
    print("vs source HEAD:", dict(Counter(row[6] for row in rows)))
    print("license headers:", dict(Counter(row[2] for row in license_rows)))


if __name__ == "__main__":
    main()
