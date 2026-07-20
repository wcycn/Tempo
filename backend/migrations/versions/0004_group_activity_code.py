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
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    columns = {column["name"] for column in inspector.get_columns("group_activities")}
    if "activity_code" not in columns:
        op.add_column("group_activities", sa.Column("activity_code", sa.String(length=8), nullable=True))
    rows = list(bind.execute(sa.text("SELECT id FROM group_activities ORDER BY id")))
    for row in rows:
        code = f"{10000000 + int(row[0]):08d}"
        bind.execute(sa.text("UPDATE group_activities SET activity_code = :code WHERE id = :id"), {"code": code, "id": row[0]})
    inspector = sa.inspect(bind)
    indexes = {index["name"] for index in inspector.get_indexes("group_activities")}
    with op.batch_alter_table("group_activities") as batch:
        batch.alter_column("activity_code", nullable=False)
    if "ix_group_activities_activity_code" not in indexes:
        op.create_index("ix_group_activities_activity_code", "group_activities", ["activity_code"], unique=True)


def downgrade():
    bind = op.get_bind()
    indexes = {index["name"] for index in sa.inspect(bind).get_indexes("group_activities")}
    columns = {column["name"] for column in sa.inspect(bind).get_columns("group_activities")}
    if "ix_group_activities_activity_code" in indexes:
        op.drop_index("ix_group_activities_activity_code", table_name="group_activities")
    if "activity_code" in columns:
        op.drop_column("group_activities", "activity_code")
