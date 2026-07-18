#!/usr/bin/env python3
"""Create a consistent SQLite backup and remove old backups."""
import argparse
import sqlite3
import sys
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.config import settings

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--keep-days", type=int, default=14)
    args = parser.parse_args()
    prefix = "sqlite:///"
    if not settings.database_url.startswith(prefix):
        raise SystemExit("backup_db.py currently supports SQLite only; use pg_dump for PostgreSQL")
    source_path = Path(settings.database_url[len(prefix):])
    if not source_path.is_absolute():
        source_path = Path.cwd() / source_path
    if not source_path.exists():
        raise SystemExit(f"database not found: {source_path}")
    backup_dir = Path(settings.backup_dir)
    if not backup_dir.is_absolute():
        backup_dir = Path.cwd() / backup_dir
    backup_dir.mkdir(parents=True, exist_ok=True)
    destination = backup_dir / f"calendar-{datetime.utcnow():%Y%m%d-%H%M%S}.db"
    with sqlite3.connect(source_path) as source, sqlite3.connect(destination) as target:
        source.backup(target)
    cutoff = datetime.utcnow() - timedelta(days=args.keep_days)
    for item in backup_dir.glob("calendar-*.db"):
        if datetime.fromtimestamp(item.stat().st_mtime) < cutoff:
            item.unlink()
    print(destination)

if __name__ == "__main__":
    main()
