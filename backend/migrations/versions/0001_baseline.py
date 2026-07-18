"""Baseline Tempo schema.

Revision ID: 0001_baseline
"""
from alembic import op
from app.database import init_db

revision = "0001_baseline"
down_revision = None
branch_labels = None
depends_on = None

def upgrade() -> None:
    # Keeps existing development databases compatible while creating all tables on a fresh DB.
    init_db()

def downgrade() -> None:
    # Baseline downgrade is intentionally non-destructive.
    pass
