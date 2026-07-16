# Tempo API

开发环境地址：`http://127.0.0.1:8765`。完整接口可在启动后打开 `/docs` 查看。

## 认证流程

1. `POST /api/auth/register` 注册，或 `POST /api/auth/login` 登录。
2. 保存返回的 `access_token`，后续请求统一加入：

```http
Authorization: Bearer <access_token>
```

3. App 启动时先从本地会话读取 Token；Token 有效时调用 `/api/auth/me`。日历基本数据仍从 Room 读取。
4. 网络不可用时只读本地缓存，不把离线修改直接伪装成服务器已保存；后续增加待同步队列后再支持离线写入。

## 账户

```http
POST /api/auth/register
Content-Type: application/json
```

```json
{
  "username": "linxiaoman",
  "email": "user@example.com",
  "password": "至少八位密码",
  "display_name": "林小满"
}
```

```http
POST /api/auth/login
```

```json
{
  "account": "linxiaoman",
  "password": "至少八位密码"
}
```

密码只在注册/登录请求中传输，服务端使用 PBKDF2-SHA256 摘要保存，不保存明文密码。生产环境还必须使用 HTTPS，并增加刷新 Token、限流和异常登录保护。

## 日程（服务端预留）

以下接口暂时保留给未来多人匹配服务使用。Android 的个人日历不调用这些接口，个人日程只保存在 Room 本地数据库。

```http
GET  /api/events
POST /api/events
PUT  /api/events/{id}
DELETE /api/events/{id}
```

创建示例：

```json
{
  "title": "羽毛球",
  "description": "滨江馆",
  "start_at": "2026-07-14T14:00:00",
  "end_at": "2026-07-14T16:00:00",
  "category": "健身",
  "status": "FLEXIBLE",
  "flexible_tail_minutes": 30
}
```

用户 ID 从 Token 获取，客户端不能通过请求体替换所有者。服务端创建 `HARD` 日程时会拒绝与本人已有硬性事务重叠的写入。

## 邀约

```http
GET   /api/invites
POST  /api/invites
PATCH /api/invites/{id}
```

接收方响应：

```json
{ "status": "ACCEPTED" }
```

当前基础版已保存邀约状态；“同意后自动拒绝同一时段其他邀约”、发起方锁定和机动匹配将在匹配状态机阶段实现。

## 离线同步（服务端预留）

```http
GET /api/sync/snapshot
```

该接口暂时保留给未来服务端匹配和跨设备同步使用。当前 Android 基础日历不依赖它；万年历和个人日程由本地 Room 管理。
