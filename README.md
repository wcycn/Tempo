# Tempo · 互通日历

Tempo 是一款以“时间对齐”为核心的社交日程协调工具。它同时解决两件事：帮助用户管理个人日程，以及帮助好友/群组快速找到共同空闲时间。

项目当前处于 Android MVP 开发阶段，已具备可运行的 Compose 界面、本地时间段选择原型和本机 SQLite 后端雏形。

## 核心能力

- 月 / 周 / 日 / 日程视图
- 15 分钟粒度的时间段选择
- 红色硬性事务、绿色完全空闲、黄色机动时间
- 好友邀约与待应答卡片
- 群组接龙原型
- 本地日程和匹配 API
- 后端接口与 Android Repository 分层预留

## 项目结构

```text
Tempo/
├── android/                 # Android 客户端（Jetpack Compose）
│   ├── app/src/main/        # Kotlin 页面与 Android 入口
│   ├── build.gradle.kts
│   └── README.md
├── backend/                 # 本机开发后端（Python + SQLite）
│   ├── server.py
│   └── README.md
├── prototype/               # Web 视觉与交互参考稿
├── docs/                    # 产品、架构、接口文档
├── .gitignore
└── README.md
```

## 快速开始

### 1. 启动本地后端

要求 Python 3.10+：

```bash
cd backend
python3 server.py
```

健康检查：

```bash
curl http://127.0.0.1:8765/api/health
```

### 2. 打开 Android 客户端

用 Android Studio 打开仓库里的 `android/` 目录，等待 Gradle 同步完成后运行 `app` 模块。

建议环境：

- Android Studio Jellyfish 或更新版本
- JDK 17+
- Android SDK 35
- Android 手机开启 USB 调试，或使用 Android Emulator

使用真机连接本机后端时，可通过 ADB 反向代理：

```bash
adb reverse tcp:8765 tcp:8765
```

模拟器访问宿主机时使用 `http://10.0.2.2:8765`；真实手机通常使用电脑局域网 IP，或使用上面的 `adb reverse`。

### 3. 查看 Web 原型

直接用浏览器打开 `prototype/index.html`。

## 协作约定

1. 每个功能使用独立分支，例如 `feature/calendar-time-grid`。
2. 提交信息使用清晰的动词开头，例如 `feat: add 15-minute time picker`。
3. 不提交 `local.properties`、`.idea/`、`.gradle/`、数据库文件和密钥。
4. 提交前至少完成一次 Android `Rebuild Project`，并运行后端健康检查。
5. Android 页面只依赖 Repository，不直接操作数据库或 HTTP。

## 当前状态与下一步

当前后端是本机开发版，使用 SQLite 保存简单日程和邀约；Android 的部分页面仍使用本地示例数据。下一步将接入 Retrofit/OkHttp，让创建日程、匹配和邀约真正读写后端。

后续计划：

- 账号登录与 JWT
- PostgreSQL 数据库
- 完整好友关系和隐私过滤
- 绿色/黄色多人交集匹配
- 邀约状态机与并发锁定
- WebSocket / FCM 通知
- 群组接龙与二次重算
- iCal/CalDAV、Excel/CSV 导入

## 文档

- [产品与技术架构](docs/architecture.md)
- [本地后端 API](docs/api.md)
- [Android 客户端说明](android/README.md)
- [后端说明](backend/README.md)
