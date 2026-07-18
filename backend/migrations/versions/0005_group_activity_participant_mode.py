"""add participant selection mode to group activities

Revision ID: 0005_group_activity_participant_mode
Revises: 0004_group_activity_code
"""
from alembic import op
import sqlalchemy as sa

revision = "0005_group_activity_participant_mode"
down_revision = "0004_group_activity_code"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("group_activities", sa.Column("participant_mode", sa.String(length=16), nullable=False, server_default="MINIMUM"))


def downgrade():
    op.drop_column("group_activities", "participant_mode")
