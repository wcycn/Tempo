# Tempo 后端

当前后端采用 FastAPI + SQLAlchemy + SQLite。SQLite 适合本机和树莓派开发阶段；正式多人部署时迁移到 PostgreSQL，接口层不需要重写。

目录按职责划分：`app/config.py` 配置，`app/database.py` 数据库连接，`app/models.py` 数据表，`app/routes/` HTTP 接口，`app/security.py` 密码和会话安全。业务规则继续增长后放入 `app/services/`，避免把复杂逻辑堆在路由函数中。

## 本机启动

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8765 --reload
```

打开 `http://127.0.0.1:8765/docs` 可以查看 Swagger 接口文档。

## 当前接口

- `GET /api/health`：健康检查
- `POST /api/auth/register`：注册，服务端使用 PBKDF2-SHA256 保存密码摘要，不保存明文密码
- `POST /api/auth/login`：用户名/邮箱登录，返回会话 Token
- `GET /api/auth/me`：获取当前用户资料
- `GET/POST /api/events`：查询和创建当前用户日程
- `PUT /api/events/{id}`：修改当前用户日程
- `DELETE /api/events/{id}`：删除当前用户日程
- `GET/POST /api/invites`：查询和发起邀约
- `PATCH /api/invites/{id}`：接收方同意或拒绝邀约
- `GET /api/sync/snapshot`：获取离线缓存所需的日程、已同意邀约和万年历缓存

除健康检查和注册/登录外的接口，都需要请求头：

```http
Authorization: Bearer <access_token>
```

## 数据存储

首次启动自动创建 `calendar.db`，也可以手动执行：

```bash
python3 scripts/init_db.py
```

当前包含用户、登录会话、日程、邀约、万年历缓存、好友关系、通知、群组和群成员表。

`calendar.db` 只属于本机开发数据，已被 Git 忽略。不要把 `.env`、Token 或数据库文件提交到仓库。

## Android / 真机连接

使用 USB 调试时：

```bash
adb reverse tcp:8765 tcp:8765
```

Android 客户端随后可以访问 `http://127.0.0.1:8765`。模拟器使用 `http://10.0.2.2:8765`；局域网连接使用运行后端电脑的局域网 IP。

Android 已加入 Retrofit/OkHttp、登录会话保存和 Room 离线快照；日程页面已开始读取真实同步数据，好友、邀约和群组页面仍处于后续接入阶段。
