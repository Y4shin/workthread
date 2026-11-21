#!/usr/bin/env python3
"""Check the requirements directory against its own rules.

The directory is one row per settled statement in the planning record, ordered
by when that statement was written: order 0 is the original specification,
orders 1 to 27 are the planning tasks in the order they were worked, 28 is the
effort map, and 29 is the glossary. Each row cites the file and the line it
came from, carries one disposition, and, when kept, names the child effort that
owes it.

Rules that run now:

  1. The header is exact, ids are unique, and the order column is non-decreasing
     and uses only known unit orders.
  2. Every row cites a source file that exists and a line inside that file, and
     the source line is not blank.
  3. Every status is one of the five settled values, and every non-kept row
     carries a note saying why.
  4. Every kept row names an owner that exists. `planner-v1` marks a row this
     planning effort discharges itself: a process rule, a record it writes, or a
     decision that constrains the children without being product capability.
     Every other value is one of the twelve child effort slugs.
  5. Every `superseded` and `duplicate` row points at a row that exists. A
     `superseded` target sits at the same or a later order, because replacement
     only ever flows forward in time; a `duplicate` target sits at the same or
     an earlier order, because the canonical statement is the older one. No row
     points at itself, and every supersede chain ends at a row that is not
     itself superseded.
  6. When the extraction parts are present, every part row appears in the
     directory exactly once, and the directory holds nothing they do not.
  7. When a specification exists, every kept row owned by a child effort is
     named by exactly one `Covers:` line in it, every identifier a `Covers:`
     line names exists and is kept, and the spec column names the section that
     covers the row.

Rule 6 compares the extraction columns row for row, not merely id for id, and
checks the frozen catalogue against the parts as well, so a substitution that
keeps the row count still fails.

Exit codes: 0 pass, 1 violations, 2 an input file is unreadable.

Usage:
    python3 docs/v1/check-requirements.py [--csv PATH] [--parts DIR] [--spec PATH]
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

COLUMNS = [
    "id",
    "order",
    "source_file",
    "line",
    "section",
    "title",
    "status",
    "target",
    "effort",
    "spec",
    "note",
]
STATUSES = {"kept", "superseded", "duplicate", "refused", "deferred"}
POINTING = {"superseded", "duplicate"}
NEEDS_NOTE = {"superseded", "duplicate", "refused", "deferred"}
UNIT_ORDERS = set(range(30))
CHILD_EFFORTS = {
    "01-scaffolding",
    "02-work-store",
    "03-cli-basics",
    "04-cli-work-model",
    "05-cli-planning",
    "06-cli-forecast",
    "07-cli-review",
    "08-mcp-agent-interface",
    "09-daemon-api",
    "10-client-shell",
    "11-client-views",
    "12-tray-surface",
}
SELF_OWNER = "planner-v1"
COVERS = re.compile(r"^\s*Covers:\s*(.+)$", re.IGNORECASE)


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != COLUMNS:
            raise SystemExit(
                f"unexpected header in {path}:\n  found: {reader.fieldnames}\n  want:  {COLUMNS}"
            )
        return [dict(row) for row in reader]


def source_lines(path: Path) -> list[str] | None:
    try:
        return path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return None


def read_covers(spec: Path) -> dict[str, list[str]]:
    """Return spec section heading -> identifiers named in its Covers line."""
    covers: dict[str, list[str]] = {}
    section = "(preamble)"
    for line in spec.read_text(encoding="utf-8").splitlines():
        heading = re.match(r"^#{1,6}\s+(.*\S)\s*$", line)
        if heading:
            section = heading.group(1)
            continue
        match = COVERS.match(line)
        if match:
            parts = [p.strip().strip("`") for p in re.split(r"[,\s]+", match.group(1))]
            for part in parts:
                if not part:
                    continue
                if ".." in part:
                    start, _, end = part.partition("..")
                    covers.setdefault(section, []).append("range:" + start + ".." + end)
                else:
                    covers.setdefault(section, []).append(part)
    return covers


def check_rows(rows: list[dict[str, str]], problems: list[str]) -> None:
    known = {row["id"]: row for row in rows}

    # Rule 1: header already checked; ids unique; order well formed.
    for ident, seen in Counter(row["id"] for row in rows).items():
        if seen > 1:
            problems.append(f"rule 1: identifier {ident} appears {seen} times")
    previous = -1
    for index, row in enumerate(rows, start=2):
        if not row["order"].isdigit():
            problems.append(f"rule 1: line {index} has non-numeric order {row['order']!r}")
            continue
        order = int(row["order"])
        if order not in UNIT_ORDERS:
            problems.append(f"rule 1: {row['id']} has unknown order {order}")
        if order < previous:
            problems.append(f"rule 1: line {index} ({row['id']}) orders backwards at {order}")
        previous = order

    # Rule 2: the citation resolves to a non-blank source line.
    cache: dict[str, list[str] | None] = {}
    for row in rows:
        path = Path(row["source_file"])
        if path not in cache:
            cache[path] = source_lines(path)
        lines = cache[path]
        if lines is None:
            problems.append(f"rule 2: {row['id']} cites missing file {row['source_file']}")
            continue
        if not row["line"].isdigit():
            problems.append(f"rule 2: {row['id']} has non-numeric line {row['line']!r}")
            continue
        number = int(row["line"])
        if number < 1 or number > len(lines):
            problems.append(
                f"rule 2: {row['id']} cites line {number}, outside {row['source_file']}"
            )
        elif not lines[number - 1].strip():
            problems.append(f"rule 2: {row['id']} cites blank line {number}")

    # Rule 3: status vocabulary and the note that explains a non-kept row.
    for row in rows:
        if row["status"] not in STATUSES:
            problems.append(f"rule 3: {row['id']} has unknown status {row['status']!r}")
        if row["status"] in NEEDS_NOTE and not row["note"].strip():
            problems.append(f"rule 3: {row['id']} is {row['status']} with no note")

    # Rule 4: every kept row has an owner that exists.
    owned = [row for row in rows if row["status"] == "kept"]
    for row in owned:
        if not row["effort"]:
            problems.append(f"rule 4: {row['id']} is kept with no effort")
        elif row["effort"] != SELF_OWNER and row["effort"] not in CHILD_EFFORTS:
            problems.append(f"rule 4: {row['id']} names unknown effort {row['effort']!r}")

    # Rule 5: the replacement graph points the right way and terminates.
    for row in rows:
        if row["status"] not in POINTING:
            continue
        target_id = row["target"]
        if not target_id:
            problems.append(f"rule 5: {row['id']} is {row['status']} with no target")
            continue
        if target_id == row["id"]:
            problems.append(f"rule 5: {row['id']} points at itself")
            continue
        target = known.get(target_id)
        if target is None:
            problems.append(f"rule 5: {row['id']} points at unknown {target_id}")
            continue
        if not (row["order"].isdigit() and target["order"].isdigit()):
            continue
        own, other = int(row["order"]), int(target["order"])
        if row["status"] == "superseded" and other < own:
            problems.append(
                f"rule 5: {row['id']} (order {own}) is superseded by earlier {target_id} (order {other})"
            )
        if row["status"] == "duplicate" and other > own:
            problems.append(
                f"rule 5: {row['id']} (order {own}) duplicates later {target_id} (order {other})"
            )
    for row in rows:
        seen: list[str] = []
        cursor = row
        while cursor["status"] == "superseded" and cursor["target"]:
            if cursor["id"] in seen:
                problems.append(f"rule 5: supersede chain loops: {' -> '.join(seen + [cursor['id']])}")
                break
            seen.append(cursor["id"])
            nxt = known.get(cursor["target"])
            if nxt is None:
                break
            cursor = nxt


EXTRACTED = ("id", "order", "source_file", "line", "section", "title")


def concatenate(parts: Path, problems: list[str]) -> list[tuple[str, ...]]:
    """Rebuild the expected extraction rows from the parts, in part order."""
    expected: list[tuple[str, ...]] = []
    for path in sorted(parts.glob("*.csv")):
        with path.open(newline="", encoding="utf-8") as handle:
            reader = csv.DictReader(handle)
            if reader.fieldnames != COLUMNS:
                problems.append(f"rule 6: {path.name} has a different header")
                continue
            for part_row in reader:
                expected.append(tuple(part_row[key] for key in EXTRACTED))
    return expected


def compare(label: str, expected: list[tuple[str, ...]], found: list[tuple[str, ...]], problems: list[str]) -> None:
    """Compare the extraction columns row for row, not just id for id."""
    if len(expected) != len(found):
        problems.append(f"rule 6: the parts hold {len(expected)} rows, {label} holds {len(found)}")
    for index, want in enumerate(expected):
        if index >= len(found):
            break
        got = found[index]
        if want != got:
            differing = [key for key, a, b in zip(EXTRACTED, want, got) if a != b]
            problems.append(
                f"rule 6: {label} row {index + 2} ({want[0]}) differs from the parts in "
                f"{', '.join(differing)}"
            )
            break


def check_parts(parts: Path, rows: list[dict[str, str]], problems: list[str]) -> int:
    if not sorted(parts.glob("*.csv")):
        return 0
    expected = concatenate(parts, problems)
    found = [tuple(row[key] for key in EXTRACTED) for row in rows]
    compare("the directory", expected, found, problems)
    return len(expected)


def check_catalog(catalog: Path, expected: list[tuple[str, ...]], problems: list[str]) -> None:
    if not catalog.is_file() or not expected:
        return
    with catalog.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != COLUMNS:
            problems.append(f"rule 6: the catalogue {catalog.name} has a different header")
            return
        found = [tuple(row[key] for key in EXTRACTED) for row in reader]
    compare("the catalogue", expected, found, problems)


def check_spec(spec: Path, rows: list[dict[str, str]], problems: list[str]) -> None:
    covers = read_covers(spec)
    cited: dict[str, list[str]] = defaultdict(list)
    known = {row["id"]: row for row in rows}
    order = [row["id"] for row in rows]
    position = {ident: index for index, ident in enumerate(order)}
    kept_ids = {row["id"] for row in rows if row["status"] == "kept"} if "status" in rows[0] else set()
    expanded: dict[str, list[str]] = {}
    for section, entries in covers.items():
        bucket = expanded.setdefault(section, [])
        for entry in entries:
            if not entry.startswith("range:"):
                bucket.append(entry)
                continue
            start, _, end = entry[len("range:"):].partition("..")
            if start not in position or end not in position:
                bucket.append(start)
                bucket.append(end)
                continue
            low, high = sorted((position[start], position[end]))
            bucket.extend(ident for ident in order[low:high + 1] if ident in kept_ids)
    covers = expanded
    for section, ids in covers.items():
        for ident in ids:
            cited[ident].append(section)
            row = known.get(ident)
            if row is None:
                problems.append(f"rule 7: section {section!r} cites unknown {ident}")
            elif row["status"] != "kept":
                problems.append(f"rule 7: section {section!r} cites {ident}, which is {row['status']}")
    for row in rows:
        if row["status"] != "kept" or row["effort"] == SELF_OWNER:
            continue
        where = cited.get(row["id"], [])
        if not where:
            problems.append(f"rule 7: {row['id']} is kept but no spec section covers it")
        elif len(where) > 1:
            problems.append(f"rule 7: {row['id']} is covered twice: {', '.join(where)}")
        elif row["spec"] and row["spec"] not in where:
            problems.append(
                f"rule 7: {row['id']} names spec section {row['spec']!r} but is covered by {where[0]!r}"
            )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", default="docs/v1/requirements-directory.csv")
    parser.add_argument("--parts", default="docs/v1/catalog/parts")
    parser.add_argument("--spec", default="docs/tasks/planner-v1/spec.md")
    args = parser.parse_args()

    directory = Path(args.csv)
    if not directory.is_file():
        print(f"cannot read {directory}", file=sys.stderr)
        return 2
    rows = read_rows(directory)
    problems: list[str] = []

    check_rows(rows, problems)
    expected = check_parts(Path(args.parts), rows, problems)
    if expected:
        check_catalog(Path("docs/v1/requirements-catalog.csv"), concatenate(Path(args.parts), []), problems)

    spec = Path(args.spec)
    if spec.is_file():
        check_spec(spec, rows, problems)
    else:
        print(f"rule 7 skipped: no specification at {spec}")

    print(f"{len(rows)} rows")
    print("status: " + ", ".join(f"{k} {v}" for k, v in sorted(Counter(r["status"] for r in rows).items())))
    owners = Counter(row["effort"] for row in rows if row["status"] == "kept")
    print("kept by effort: " + ", ".join(f"{k} {v}" for k, v in sorted(owners.items())))
    if expected:
        print(f"parts: {expected} rows checked")
    if problems:
        print(f"\n{len(problems)} problem(s):")
        for problem in problems:
            print(f"  {problem}")
        return 1
    print("\nall rules pass")
    return 0


if __name__ == "__main__":
    sys.exit(main())
