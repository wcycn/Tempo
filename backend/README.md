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
- `POST /api/auth/login`：用户名/邮箱登录，返回会话 Token；失败尝试按来源和账号限流
- `GET /api/auth/me`：获取当前用户资料
- `PATCH /api/auth/me`：修改当前用户资料
- `POST /api/auth/password`：修改密码并注销该用户的全部旧会话
- `POST /api/auth/delete-account`：使用当前密码注销账号并删除服务端资料
- `GET/POST /api/events`：查询和创建当前用户日程
- `PUT /api/events/{id}`：修改当前用户日程
- `DELETE /api/events/{id}`：删除当前用户日程
- `GET/POST /api/invites`：查询和发起邀约
- `PATCH /api/invites/{id}`：接收方同意或拒绝邀约
- `GET /api/sync/snapshot`：获取离线缓存所需的日程、已同意邀约和万年历缓存
- `PATCH /api/groups/{id}`：群主修改群名
- `POST /api/groups/{id}/members`：群主发出入群邀请，不直接添加成员
- 群组活动可由任意已确认入群成员发起；进入确认阶段后不可新加入，重算时排除明确拒绝者、保留超时未处理者，重算和取消按活动创建者/群主权限校验
- `GET/PATCH /api/groups/invitations`：查看并接受/拒绝入群邀请
- `GET /api/notifications`：读取未读站内通知；群组取消活动会通知全部群成员
- `POST /api/ai/calendar/parse-audio`：上传登录用户的短录音，返回未保存的日程草稿；未配置智谱密钥时返回 503

除健康检查和注册/登录外的接口，都需要请求头：

```http
Authorization: Bearer <access_token>
```

## 数据存储

首次启动自动创建 `calendar.db`，也可以手动执行：

```bash
python3 scripts/init_db.py
python3 scripts/migrate.py
```

当前包含用户、登录会话、日程、邀约、万年历缓存、好友关系、通知、群组和群成员表。

用户内部数据库主键与对外账号 ID 分离。对外账号 ID 为六位数字，从 `100001` 开始分配；密码只保存摘要，昵称可以通过认证接口修改；登录失败会按来源地址和账号组合限流，密码修改会注销全部旧会话。

`calendar.db` 只属于本机开发数据，已被 Git 忽略。不要把 `.env`、Token 或数据库文件提交到仓库。

## 生产化维护

数据库结构由 Alembic 管理，首次部署或升级后执行：

```bash
python3 scripts/migrate.py
```

SQLite 备份使用一致性备份，不要直接复制正在运行的 `calendar.db`：

```bash
python3 scripts/backup_db.py --keep-days 14
python3 scripts/restore_db.py backups/calendar-YYYYMMDD-HHMMSS.db
python3 scripts/cleanup_sessions.py
```

备份目录和日志级别通过 `.env` 配置：

```env
LOG_LEVEL=INFO
BACKUP_DIR=/srv/tempo/backups  # 本机开发可使用 ./backups
```

部署检查（只读，不修改数据库）：

```bash
python3 scripts/smoke_check.py http://127.0.0.1:8765
```

开发依赖和安全单元测试：

```bash
pip install -r requirements-dev.txt
pytest -q tests
```

服务器 systemd 部署使用 `backend/deploy/tempo-backend.service`（由管理员安装到 `/etc/systemd/system/`）。备份和会话清理 timer 文件也在同一目录。Nginx HTTPS 反向代理模板为 `backend/deploy/nginx-tempo.conf.example`，实际启用前必须替换域名并配置证书。

API 只记录请求方法、路径、状态码、耗时和请求 ID，不记录密码、Token、请求体或录音内容。正式部署应把备份目录放在应用目录之外，例如 `/srv/tempo/backups`，并通过 systemd timer 定期执行备份。当前服务器已启用每日备份和会话清理。

## AI 语音创建日程

AI 服务只运行在后端。Android 不保存或携带智谱 API Key，只将录音上传到 Tempo 后端。后端在内存中转发给智谱，识别完成后返回草稿，客户端必须经过用户确认才写入本地 Room。服务器不保存录音、原始转写文本或 AI 草稿。

在服务器的 `.env` 中配置，不要提交到 Git：

```env
ZHIPU_API_KEY=你的智谱密钥
ZHIPU_ASR_MODEL=glm-asr-2512
ZHIPU_TEXT_MODEL=glm-4-flash
ZHIPU_BASE_URL=https://open.bigmodel.cn/api/paas/v4
```

录音由 Android 限制为最多 30 秒，服务端限制为 25 MB。当前接口只支持登录用户调用。

## Android / 真机连接

使用 USB 调试时：

```bash
adb reverse tcp:8765 tcp:8765
```

Android 客户端随后可以访问 `http://127.0.0.1:8765`。模拟器使用 `http://10.0.2.2:8765`；局域网连接使用运行后端电脑的局域网 IP。

Android 已加入 Retrofit/OkHttp、登录会话保存和 Room 离线快照；日程页面已开始读取真实同步数据，好友、邀约和群组页面仍处于后续接入阶段。
### 群组活动与同步说明

- 群组活动使用独立的 8 位 `activity_code` 展示，接口内部仍使用数据库主键 `id`。
- `MATCHING` 超过 30 秒未更新时，下一次读取群组活动会自动恢复并重新匹配。
- 同步快照返回 `server_version`，客户端可据此判断服务器上的邀约、群组活动或日历缓存是否发生变化。
- 当前服务按东八区保存和解释无时区时间；后续如支持海外用户，再迁移为带偏移量的 UTC 时间。
