# 本地 API

Base URL：`http://127.0.0.1:8765`

## 健康检查

```http
GET /api/health
```

## 日程

```http
GET /api/events?user_id=me
POST /api/events
```

创建请求示例：

```json
{
  "user_id": "me",
  "title": "羽毛球",
  "start": "2026-07-14T14:00:00",
  "end": "2026-07-14T16:00:00",
  "category": "健身",
  "status": "FLEXIBLE",
  "flexible_tail_minutes": 30
}
```

## 匹配扫描

```http
POST /api/matching/scan
```

```json
{
  "user_id": "me",
  "start": "2026-07-14T09:00:00",
  "end": "2026-07-14T23:00:00",
  "duration_minutes": 90
}
```

当前 MVP 会按 15 分钟步长扫描，并排除硬性事务冲突。多人绿色/黄色交集、发起方锁定和接收方自动拒绝将在后续版本加入事务逻辑。

## 邀约

```http
GET  /api/invites?user_id=me
POST /api/invites
```

```json
{
  "sender_id": "me",
  "receiver_id": "user_002",
  "title": "晚餐",
  "start": "2026-07-14T19:00:00",
  "end": "2026-07-14T20:30:00"
}
```
