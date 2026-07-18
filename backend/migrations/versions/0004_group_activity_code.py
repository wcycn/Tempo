"""add public eight digit activity code

Revision ID: 0004_group_activity_code
Revises: 0003_group_activity_windows
"""
from alembic import op
import sqlalchemy as sa

revision = "0004_group_activity_code"
down_revision = "0003_group_activity_windows"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("group_activities", sa.Column("activity_code", sa.String(length=8), nullable=True))
    bind = op.get_bind()
    rows = list(bind.execute(sa.text("SELECT id FROM group_activities ORDER BY id")))
    for row in rows:
        code = f"{10000000 + int(row[0]):08d}"
        bind.execute(sa.text("UPDATE group_activities SET activity_code = :code WHERE id = :id"), {"code": code, "id": row[0]})
    with op.batch_alter_table("group_activities") as batch:
        batch.alter_column("activity_code", nullable=False)
    op.create_index("ix_group_activities_activity_code", "group_activities", ["activity_code"], unique=True)


def downgrade():
    op.drop_index("ix_group_activities_activity_code", table_name="group_activities")
    op.drop_column("group_activities", "activity_code")
