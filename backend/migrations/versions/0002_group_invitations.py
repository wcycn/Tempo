"""Add explicit group invitation workflow.

Revision ID: 0002_group_invitations
Revises: 0001_baseline
"""
from alembic import op
import sqlalchemy as sa

revision = "0002_group_invitations"
down_revision = "0001_baseline"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    if "group_invitations" in inspector.get_table_names():
        return
    op.create_table(
        "group_invitations",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("group_id", sa.Integer(), sa.ForeignKey("groups.id", ondelete="CASCADE"), nullable=False),
        sa.Column("inviter_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("target_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False, server_default="PENDING"),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.Column("responded_at", sa.DateTime(), nullable=True),
        sa.UniqueConstraint("group_id", "target_id", name="uq_group_invitation_target"),
    )
    op.create_index("ix_group_invitations_group_id", "group_invitations", ["group_id"])
    op.create_index("ix_group_invitations_inviter_id", "group_invitations", ["inviter_id"])
    op.create_index("ix_group_invitations_target_id", "group_invitations", ["target_id"])
    op.create_index("ix_group_invitations_status", "group_invitations", ["status"])


def downgrade() -> None:
    op.drop_index("ix_group_invitations_status", table_name="group_invitations")
    op.drop_index("ix_group_invitations_target_id", table_name="group_invitations")
    op.drop_index("ix_group_invitations_inviter_id", table_name="group_invitations")
    op.drop_index("ix_group_invitations_group_id", table_name="group_invitations")
    op.drop_table("group_invitations")
