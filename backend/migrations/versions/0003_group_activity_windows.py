"""add optional matching windows to group activities

Revision ID: 0003_group_activity_windows
Revises: 0002_group_invitations
"""
from alembic import op
import sqlalchemy as sa

revision = "0003_group_activity_windows"
down_revision = "0002_group_invitations"
branch_labels = None
depends_on = None


def upgrade():
    inspector = sa.inspect(op.get_bind())
    columns = {column["name"] for column in inspector.get_columns("group_activities")}
    if "window_start_at" not in columns:
        op.add_column("group_activities", sa.Column("window_start_at", sa.DateTime(), nullable=True))
    if "window_end_at" not in columns:
        op.add_column("group_activities", sa.Column("window_end_at", sa.DateTime(), nullable=True))


def downgrade():
    inspector = sa.inspect(op.get_bind())
    columns = {column["name"] for column in inspector.get_columns("group_activities")}
    if "window_end_at" in columns:
        op.drop_column("group_activities", "window_end_at")
    if "window_start_at" in columns:
        op.drop_column("group_activities", "window_start_at")
