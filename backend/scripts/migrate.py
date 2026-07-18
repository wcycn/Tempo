#!/usr/bin/env python3
"""Apply database migrations: python3 scripts/migrate.py"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from alembic import command
from alembic.config import Config

if __name__ == "__main__":
    config = Config(str(Path(__file__).resolve().parents[1] / "alembic.ini"))
    command.upgrade(config, "head")
    print("Tempo database migrations applied")
