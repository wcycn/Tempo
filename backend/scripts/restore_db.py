#!/usr/bin/env python3
"""Restore a SQLite backup; stop the API service before running this script."""
import argparse
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.config import settings

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("backup", type=Path)
    args = parser.parse_args()
    if not settings.database_url.startswith("sqlite:///"):
        raise SystemExit("restore_db.py currently supports SQLite only")
    target = Path(settings.database_url[len("sqlite:///"):])
    if not target.is_absolute():
        target = Path.cwd() / target
    if not args.backup.exists():
        raise SystemExit(f"backup not found: {args.backup}")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(args.backup, target)
    print(f"restored {args.backup} -> {target}")

if __name__ == "__main__":
    main()
