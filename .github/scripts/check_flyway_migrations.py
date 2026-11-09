#!/usr/bin/env python3

import argparse
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import PurePosixPath


VERSION_PATTERN = re.compile(
    r"^V([0-9]+(?:[._][0-9]+)*)__.+\.sql$"
)


def git_files(revision):
    """Return file paths and their Git blob hashes at a revision"""
    result = subprocess.run(
        ["git", "ls-tree", "-r", "-z", revision],
        check=True,
        capture_output=True,
    )

    files = {}

    for entry in result.stdout.split(b"\0"):
        if not entry:
            continue

        metadata, path = entry.split(b"\t", 1)
        _, kind, blob_sha = metadata.split()

        if kind == b"blob":
            files[path.decode("utf-8", errors="surrogateescape")] = (
                blob_sha.decode("ascii")
            )

    return files


def is_migration(path, migration_path):
    """Check whether a file is in the specified migration directory"""
    migration_path = migration_path.strip("/")

    return (
        path.startswith(migration_path + "/")
        and VERSION_PATTERN.fullmatch(PurePosixPath(path).name)
        is not None
    )


def migration_version(path):
    """Extract and normalize the Flyway migration version"""
    match = VERSION_PATTERN.fullmatch(PurePosixPath(path).name)

    if not match:
        return None

    return tuple(
        int(part)
        for part in re.split(r"[._]", match.group(1))
    )


def check_duplicates(base_files, head_files, migration_path):
    """Check added migrations for duplicate Flyway versions"""
    base_versions = defaultdict(list)
    head_versions = defaultdict(list)

    for path in base_files:
        if is_migration(path, migration_path):
            version = migration_version(path)
            base_versions[version].append(path)

    for path in head_files:
        if is_migration(path, migration_path):
            version = migration_version(path)
            head_versions[version].append(path)

    added_paths = {
        path
        for path in head_files
        if path not in base_files
        and is_migration(path, migration_path)
    }

    errors = []

    for version, paths in head_versions.items():
        new_paths = [path for path in paths if path in added_paths]

        if not new_paths:
            continue

        version_str = ".".join(map(str, version))

        if version in base_versions:
            existing = "\n".join(
                f"  main: {path}"
                for path in base_versions[version]
            )
            added = "\n".join(
                f"  PR:   {path}"
                for path in new_paths
            )

            errors.append(
                f"Migration version V{version_str} already exists "
                f"on main:\n{existing}\n{added}"
            )

        elif len(paths) > 1:
            conflicting = "\n".join(
                f"  {path}" for path in paths
            )

            errors.append(
                f"Duplicate migration version V{version_str} "
                f"in PR:\n{conflicting}"
            )

    return errors


def check_modified(base_files, head_files, migration_path):
    """Ensure migrations already on main remain unchanged"""
    errors = []

    for path, base_sha in base_files.items():
        if not is_migration(path, migration_path):
            continue

        if path not in head_files:
            errors.append(f"Deleted or renamed: {path}")

        elif head_files[path] != base_sha:
            errors.append(f"Modified: {path}")

    return errors


def main():
    parser = argparse.ArgumentParser(
        description="Validate Flyway migrations in a pull request"
    )

    parser.add_argument(
        "--check",
        required=True,
        choices=["duplicates", "modified"],
        help="Type of validation to perform",
    )

    parser.add_argument(
        "--service",
        required=True,
        help="Microservice name used for reporting",
    )

    parser.add_argument(
        "--migration-path",
        required=True,
        help="Migration directory relative to repository root",
    )

    parser.add_argument(
        "--base",
        required=True,
        help="Git SHA of the PR base commit",
    )

    args = parser.parse_args()

    base_files = git_files(args.base)
    head_files = git_files("HEAD")

    if args.check == "duplicates":
        errors = check_duplicates(
            base_files,
            head_files,
            args.migration_path,
        )
    else:
        errors = check_modified(
            base_files,
            head_files,
            args.migration_path,
        )

    if errors:
        print(
            f"FAILED: {args.check} validation for {args.service}",
            file=sys.stderr,
        )

        for error in errors:
            print(f"\nERROR: {error}", file=sys.stderr)

        sys.exit(1)

    print(f"PASSED: {args.check} validation for {args.service}")


if __name__ == "__main__":
    main()