#!/usr/bin/env python3
"""手动初始化 Tempo 数据库：python3 scripts/init_db.py"""

import sys
from pathlib import Path

# 允许从 backend/ 目录直接执行脚本时导入同级 app 包。
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.database import init_db


if __name__ == "__main__":
    init_db()
    print("Tempo database initialized")
