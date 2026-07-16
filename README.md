# Tempo · 互通日历

Tempo 是一款以“时间对齐”为核心的社交日程协调工具。它同时解决两件事：帮助用户管理个人日程，以及帮助好友/群组快速找到共同空闲时间。

项目当前处于 Android MVP 开发阶段，已具备 Compose 界面、15 分钟时间段选择原型和本机 SQLite 后端雏形。当前目标是按本文件的阶段计划逐步完成从单机原型到可联机使用的版本。

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

## TODO（按执行顺序）

### 1. 工程与协作

- [X] 约定分支、Issue、代码评审和版本号规则。
- [X] 确认合作者可以独立克隆、构建 Android 客户端并启动后端。

### 2. Android 前端完善

- [X] 完善创建、编辑、删除日程流程。
- [X] 去除页面中的写死示例数据，统一交给 ViewModel 和 Repository 管理。
- [X] 完善红色硬性、绿色空闲、黄色机动状态的交互反馈。
- [X] 接入准确的农历、节气和法定节假日数据。
- [X] 增加加载中、空数据、网络失败和重试状态。

### 3. 后端基础框架

- [ ] 拆分路由、服务层、数据访问层和配置文件。
- [ ] 完成账户、日程、好友、邀约、通知和群组数据库表。
- [ ] 增加数据库初始化、迁移、参数校验和统一错误格式。
- [ ] 增加接口测试和基础日志。
- [ ] 在树莓派上完成稳定启动和数据持久化。

### 4. Android 与后端联调

- [ ] Android 增加 Retrofit/OkHttp 和网络权限。
- [ ] 实现 `RemoteCalendarRepository`。
- [ ] 创建、查询、修改、删除日程改为真实 API 调用。
- [ ] 发起邀约和匹配扫描改为真实 API 调用。
- [ ] 支持服务器地址配置，不把 IP 写死在页面中。

### 5. 账户、好友与隐私

- [ ] 用户注册、登录、退出和 Token 刷新。
- [ ] 用户资料、头像和好友申请。
- [ ] 好友同意、删除和好友列表。
- [ ] 服务端只返回红/绿/黄状态色块。
- [ ] 确保活动名称、描述和个人分类不会越权泄露。

### 6. 单人匹配与邀约

- [ ] 实现纯绿色交集扫描。
- [ ] 无结果时纳入黄色机动尾巴。
- [ ] 最多 3 个推荐和至少 3 个二次组合方案。
- [ ] 实现发起方锁定、接收方叠加邀约。
- [ ] 实现同意后自动拒绝其他邀约。
- [ ] 实现接收方改红后的自动失效。
- [ ] 完成撤回、拒绝、超时、取消和冲突释放。

### 7. 群组接龙

- [ ] 群组创建、成员管理和群主权限。
- [ ] 固定时间、最近时间、人数峰值三种匹配规则。
- [ ] 接龙达到最低人数后生成拟定方案。
- [ ] 所有拟定成员二次确认。
- [ ] 确认失败后支持群主重新匹配或取消。

### 8. 通知与外部同步

- [ ] App 内通知中心。
- [ ] WebSocket 实时更新和 FCM 推送。
- [ ] 日程提醒和健康提醒。
- [ ] Excel/CSV 导入和冲突报告。
- [ ] iCal/CalDAV 只读同步。

### 9. 兼容性、安全与发布

- [ ] 验证 Android 版本和荣耀、华为、小米、OPPO、vivo 等设备。
- [ ] 验证 USB、局域网、模拟器和树莓派通信。
- [ ] 后端迁移到 PostgreSQL。
- [ ] 增加 HTTPS、JWT、权限校验、限流和日志。
- [ ] 完成数据库备份、恢复和发布流程。

详细开发过程见 [project_nodes.md](docs/project_nodes.md)。

## 文档

- [产品与技术架构](docs/architecture.md)
- [本地后端 API](docs/api.md)
- [Android 客户端说明](android/README.md)
- [后端说明](backend/README.md)
- [开发节点记录](docs/project_nodes.md)
- [协作开发说明](CONTRIBUTING.md)
