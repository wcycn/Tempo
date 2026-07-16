from collections.abc import Generator

from sqlalchemy import create_engine, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import settings


connect_args = {"check_same_thread": False} if settings.database_url.startswith("sqlite") else {}
engine = create_engine(settings.database_url, connect_args=connect_args)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


class Base(DeclarativeBase):
    pass


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db() -> None:
    """初始化开发环境数据库；生产环境后续由迁移工具接管。"""
    from . import models  # noqa: F401

    Base.metadata.create_all(bind=engine)
    with engine.begin() as connection:
        if settings.database_url.startswith("sqlite"):
            connection.execute(text("PRAGMA foreign_keys=ON"))
            columns = {row[1] for row in connection.execute(text("PRAGMA table_info(users)"))}
            if "account_id" not in columns:
                connection.execute(text("ALTER TABLE users ADD COLUMN account_id INTEGER"))
            for column, definition in {
                "phone": "VARCHAR(30)",
                "hobbies": "VARCHAR(240)",
                "signature": "VARCHAR(240)",
            }.items():
                if column not in columns:
                    connection.execute(text(f"ALTER TABLE users ADD COLUMN {column} {definition}"))
            connection.execute(text("UPDATE users SET account_id = 100000 + id WHERE account_id IS NULL"))
            connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_account_id ON users(account_id)"))
            session_columns = {row[1] for row in connection.execute(text("PRAGMA table_info(sessions)"))}
            if "session_key" not in session_columns:
                connection.execute(text("ALTER TABLE sessions ADD COLUMN session_key VARCHAR(64)"))
            connection.execute(text("UPDATE sessions SET session_key = substr(token_hash, 1, 32) WHERE session_key IS NULL"))
            connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_sessions_session_key ON sessions(session_key)"))


def check_db() -> bool:
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
        return True
    except Exception:
        return False
