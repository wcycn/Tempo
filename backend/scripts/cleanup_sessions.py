#!/usr/bin/env python3
"""Delete expired login sessions."""

import sys
from datetime import datetime
from pathlib import Path

from sqlalchemy import delete

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.database import SessionLocal
from app.models import SessionToken


if __name__ == "__main__":
    with SessionLocal() as db:
        result = db.execute(delete(SessionToken).where(SessionToken.expires_at <= datetime.utcnow()))
        db.commit()
        print(f"deleted_sessions={result.rowcount or 0}")
