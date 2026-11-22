#!/usr/bin/env python3
"""Deterministic lookup for the requirements directory.

Agents and people must use this script for every requirements lookup instead of
reading, grepping, or filtering `requirements-directory.csv` by hand. Both
commands print CSV with the register's header on the first line.

    python3 docs/v1/requirements-lookup.py effort 01-scaffolding
    python3 docs/v1/requirements-lookup.py id T01-022

The first prints every row whose `effort` column equals the given name. The
second prints the single row whose `id` equals the given value.

Exit codes: 0 success, 1 no match (or more than one match for an id), 2 bad
usage. A lookup this script does not offer is added to the script, with the
operator's approval, rather than performed by hand.
"""

from __future__ import annotations

import argparse
import csv
import os
import sys
from pathlib import Path

REGISTER = Path(__file__).resolve().with_name("requirements-directory.csv")
EFFORT_COLUMN = "effort"
ID_COLUMN = "id"


def read_register() -> tuple[list[str], list[dict[str, str]]]:
    with REGISTER.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        rows = list(reader)
        fields = reader.fieldnames or []
    if not fields:
        raise SystemExit(f"{REGISTER.name} has no header row")
    for required in (ID_COLUMN, EFFORT_COLUMN):
        if required not in fields:
            raise SystemExit(f"{REGISTER.name} has no {required!r} column")
    return fields, rows


def emit(fields: list[str], rows: list[dict[str, str]]) -> None:
    writer = csv.DictWriter(sys.stdout, fieldnames=fields, lineterminator="\n")
    writer.writeheader()
    for row in rows:
        writer.writerow({field: row.get(field, "") for field in fields})


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    commands = parser.add_subparsers(dest="command", required=True)

    by_effort = commands.add_parser(
        "effort", help="print every row whose effort column equals the name"
    )
    by_effort.add_argument("name", help="the effort slug, for example 01-scaffolding")

    by_id = commands.add_parser("id", help="print the single row with the id")
    by_id.add_argument("value", help="the row id, for example T01-022")

    args = parser.parse_args()
    fields, rows = read_register()

    if args.command == "effort":
        matches = [row for row in rows if row.get(EFFORT_COLUMN) == args.name]
        if not matches:
            print(f"no rows have effort {args.name!r}", file=sys.stderr)
            return 1
    else:
        matches = [row for row in rows if row.get(ID_COLUMN) == args.value]
        if len(matches) != 1:
            print(
                f"expected exactly one row with id {args.value!r}, found {len(matches)}",
                file=sys.stderr,
            )
            return 1

    emit(fields, matches)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except BrokenPipeError:
        # A reader such as `head` closed the pipe; exit quietly, as a filter does.
        os.dup2(os.open(os.devnull, os.O_WRONLY), sys.stdout.fileno())
        raise SystemExit(0)
