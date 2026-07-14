# 本地后端

无需安装第三方库，直接运行：

```bash
cd 想法构思/backend
python3 server.py
```

健康检查：`http://127.0.0.1:8765/api/health`

主要接口：

- `GET /api/events?user_id=me`
- `POST /api/events`
- `POST /api/matching/scan`
- `GET /api/invites?user_id=me`
- `POST /api/invites`

数据库文件 `calendar.db` 会在首次启动时自动生成。Android 模拟器访问当前电脑时使用 `http://10.0.2.2:8765`；真实手机需要把 `127.0.0.1` 换成电脑在局域网中的 IP，并确保手机和电脑在同一 Wi-Fi。
