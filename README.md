# Tempo · 社交日程协调工具

<p align="center">
  <img src="logo.png" alt="Tempo Logo" width="180" />
</p>

## 使用说明

Tempo 当前仅支持 Android 系统。

- 暂不支持 iOS
- 暂不支持鸿蒙系统
- Android 用户可以直接下载 GitHub Releases 中的 APK 安装包进行体验

当前最新测试版为 `Tempo v0.4.0-beta`，主要用于 Android 内测，欢迎大家试用并提出建议。如果你熟悉 iOS、鸿蒙、Android、后端、UI/UX、测试或产品设计，欢迎联系我一起开发；当然，也欢迎所有对项目感兴趣的朋友参与贡献。

当前版本说明见 [`docs/releases/tempo-v0.4.0.md`](docs/releases/tempo-v0.4.0.md)，历史版本说明见 [`docs/releases/tempo-v0.3.0.md`](docs/releases/tempo-v0.3.0.md)。

### Android 安装

1. 打开项目的 GitHub `Releases` 页面。
2. 选择最新版本，例如 `Tempo v0.4.0-beta`。
3. 下载其中的 `app-debug.apk`。
4. 在 Android 手机上打开 APK 并完成安装。
5. 如果系统提示权限，请允许当前浏览器或文件管理器“安装未知来源应用”。

### 当前内测范围

当前版本可以体验：

- 月、周、日、日程视图
- 日期切换和农历显示
- 周视图按系统当前日期计算，并从周一开始显示完整七天
- 月、周、日视图支持左右滑动切换
- 本地创建、编辑、删除日程
- 在“我的 → 数据导入”中导入 CSV、XLSX、ICS 模板；导入前可预览、统一选择分类，导入后作为本地普通日程保存
- 15 分钟时间段选择
- 工作、学习、游戏、聚会、会议和自定义标签
- 深色、浅色、跟随系统主题
- 游客离线使用日历
- 好友间真实邀约候选扫描：先匹配双方绿色空闲，找不到时再纳入黄色机动和未标记时间
- 邀约候选以完整开始/结束时间段显示；接收方同意后写入本地日历，其他重叠待应答邀约自动拒绝
- 发起邀约只需选择活动持续时间，系统自动扫描未来 7 天的双方空闲时间段
- 活动时长使用滚轮选择器，可选择 15 分钟至 8 小时
- 可在未来 7 天周表格中粗略框选允许匹配的日期和时间范围，避免晚饭被匹配到上午
- 月视图显示多条日程数量提示，周视图显示日程色块
- AI 语音填写日程：录音上传服务器识别为草稿，确认后保存到本地
- AI 语音填写需要先在“我的 → AI 内测”输入受邀密码，验证通过后才可使用。

群组多人匹配、好友关系、单人邀约和 AI 语音接口已接入后端内测链路；个人日程仍以本地 Room 为准。通知入口已从当前 Android 客户端移除，邀约状态直接在“找时间”页面查看。

### 当前开发状态

非手动测试所需的工程工作已经完成：后端迁移、日志、备份、会话清理、群组 API、AI 代理、Android 群组页面和可配置服务地址均已落地。接下来主要剩下真机回归、设备兼容性验证、域名/HTTPS 申请和第三方服务联调。

反馈时建议提供：手机型号、Android 版本、操作步骤、实际结果、期望结果以及截图或录屏。

Tempo 是一款以“时间对齐”为核心的社交日程协调工具。它同时解决两件事：帮助用户管理个人日程，以及帮助好友/群组快速找到共同空闲时间。

项目当前处于 Android MVP 内测阶段，前端核心日历功能已经可以在 Android 真机上使用，并具备 Compose 界面、15 分钟时间段选择、本地 Room 日程存储和本机后端基础。当前目标是收集真实使用反馈，再逐步完成多人邀约、联网同步和正式发布能力。

## 核心能力

- 月 / 周 / 日 / 日程视图
- 15 分钟粒度的时间段选择
- 红色硬性事务、绿色完全空闲、黄色机动时间
- “找时间”中的好友搜索、好友申请与待应答卡片
- 群组接龙原型
- 本地日程和匹配 API
- 后端接口与 Android Repository 分层预留

## 数据与安全边界

- Android 日程、标签、主题和游客数据保存在本机 Room，不作为个人日历上传到服务器。
- 服务器只保存账号必要信息、登录会话和后续需要多人协作的邀约数据。
- 密码不会明文保存，服务端使用带随机盐的 PBKDF2-SHA256 摘要；登录 Token 只以哈希形式保存在服务端，Android 使用 Keystore 加密保存本地会话。
- 当前服务器内测仍使用 HTTP 公网地址。HTTPS 配置完成前，只使用专用测试账号和测试密码，不要使用其他网站重复密码。
- Token 和 AI 临时凭据使用 Android EncryptedSharedPreferences 保存；Release 禁止系统自动备份。
- 日历 Room 数据目前依赖 Android 设备锁保护，尚未启用 SQLCipher；如进入正式商用阶段，再安排数据库密钥迁移，避免破坏现有离线数据。

## 项目结构

```text
Tempo/
├── android/                 # Android 客户端（Jetpack Compose）
│   ├── app/src/main/        # Kotlin 页面与 Android 入口
│   ├── build.gradle.kts
│   └── README.md
├── backend/                 # 本机开发后端（FastAPI + SQLAlchemy + SQLite）
│   ├── app/                 # 配置、数据库模型、认证和 API 路由
│   ├── scripts/             # 数据库初始化脚本
│   ├── requirements.txt
│   └── README.md
├── prototype/               # Web 视觉与交互参考稿
├── deploy/                  # 服务器部署配置（systemd）
├── docs/                    # 产品、架构、接口文档
├── .gitignore
└── README.md
```

## 快速开始

### 1. 启动本地后端

要求 Python 3.10+：

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8765 --reload
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

如果后端暂时无法开放公网端口，也可以使用 SSH 隧道进行 USB 测试：

```bash
ssh -N -L 8765:127.0.0.1:3001 zbh
adb reverse tcp:8765 tcp:8765
```

当前 Debug 和 Release 版本都会直接访问公网服务器；SSH 隧道仅作为备用测试方式。

模拟器访问宿主机时使用 `http://10.0.2.2:8765`；真实手机通常使用电脑局域网 IP，或使用上面的 `adb reverse`。

### 3. 查看 Web 原型

直接用浏览器打开 `prototype/index.html`。

### 4. Tempo 服务器部署

当前开发服务器使用独立的 `/srv/tempo` 目录和 `tempo` 系统用户运行后端，服务配置见 [`backend/deploy/tempo-backend.service`](backend/deploy/tempo-backend.service)。服务器内部健康检查地址为：

```text
http://127.0.0.1:3001/api/health
```

当前内测公网地址为 `http://1.95.175.42:3001`。长期使用应配置域名和 HTTPS，避免直接使用公网 IP + HTTP。

## 内测方式

当前可以邀请朋友参与 Android 内测，但建议先明确这是“前端可用性内测”，不是正式联网版本。

### 方式 A：发送 APK

在 Android Studio 中选择 `Build → Generate App Bundle(s) / APK(s) → Generate APK(s)`，生成的 APK 通常位于：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

将 APK 通过文件传输工具发送给测试者。测试者需要允许安装来自此来源的应用，然后安装并打开 Tempo。测试完成后可以在手机设置中关闭该权限。

### 方式 B：使用 USB 直接安装

如果朋友的手机连接在当前电脑上，可以执行：

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

### 当前内测范围与限制

- 日历查看、月/周/日/日程切换、日期选择、创建/编辑/删除日程、标签和主题选择可以测试。
- 日程数据保存在测试者手机本地 Room 中，游客状态也可以使用基本日历功能。
- 登录/注册依赖开发机上的 FastAPI 服务；朋友手机不能直接访问你电脑的 `127.0.0.1`。如果要测试账户功能，需要让手机和电脑处于同一局域网，并配置电脑局域网 IP，或部署到树莓派。
- 好友真实关系、多人邀约和 AI 代理已接入内测后端；推送通知、正式 HTTPS 和跨设备同步仍未启用，不要把当前版本当作正式产品使用。

### 建议反馈模板

请测试者按以下内容反馈，便于定位问题：

```text
手机型号 / Android 版本：
测试功能：
操作步骤：
实际结果：
期望结果：
是否可以稳定复现：
截图或录屏：
```

## 协作约定

1. 每个功能使用独立分支，例如 `feature/calendar-time-grid`。
2. 提交信息使用清晰的动词开头，例如 `feat: add 15-minute time picker`。
3. 不提交 `local.properties`、`.idea/`、`.gradle/`、数据库文件和密钥。
4. 提交前至少完成一次 Android `Rebuild Project`，并运行后端健康检查。
5. Android 页面只依赖 Repository，不直接操作数据库或 HTTP。
6. AI 密钥只允许存在服务器环境变量，Android 只访问 Tempo 的 AI 代理接口。

## TODO（按执行顺序）

### 1. 工程与协作

- [X] 分支、Issue、提交信息、版本号和协作规范。
- [X] README、架构文档、API 文档和开发节点记录。
- [X] Android 客户端、FastAPI 后端、数据库迁移和部署目录分离。

### 2. Android 前端与本地数据

- [X] 月、周、日、日程视图、农历、系统日期和周一开周。
- [X] 15 分钟时间段、创建/编辑/删除日程、标签与主题管理。
- [X] 00:00–24:00 周课程表、展开/收缩、滑动切换和日程色块。
- [X] Room 本地日程、游客模式、按账号隔离的离线数据。
- [X] 登录/注册、Token 会话保存、资料编辑、好友资料和手机号拨号。
- [X] AI 录音结束/取消、后端解析草稿、用户确认后本地保存。
- [ ] 真机回归与不同厂商设备兼容性验证（需要开发者实际操作）。

### 3. 后端与协作业务

- [X] FastAPI 路由、SQLAlchemy 模型、认证和密码摘要。
- [X] 账号、会话、好友、邀约、群组活动和参与者数据库表。
- [X] 单人邀约候选扫描、同意/拒绝/取消/过期和冲突释放。
- [X] 群组固定时间、最近时间、人数峰值、二次确认、重算和取消；任何已入群成员都可发起活动，并显示真实发起人。
- [X] 群组活动时间选择器：固定时间使用日期 + 15 分钟时间格，自动匹配支持 7 天矩形范围，响应截止支持日期 + 时分滚轮。
- [X] 群组活动可靠性：MATCHING 超时恢复、独立 8 位活动编号、服务端同步版本检查和账号隔离的本地 revision。
- [X] 群组人数模式：支持最低人数和确定人数；自动匹配必须先框选可约时间范围。
- [X] 群组官方使用说明：Android 群组页面提供说明入口，完整文档见 [`docs/group_usage_guide.md`](docs/group_usage_guide.md)。
- [X] 分类标签集中管理：我的页面支持新增、删除、长按拖动排序，并与创建日程联动。
- [X] 重复日程：支持每天、每周、工作日和节假日，并展开保存到本地日历。
- [X] 每日抽签和资料折叠。
- [x] 模板文件导入：支持 CSV/XLSX/ICS 选择、预览、统一分类、本地 Room 写入和重叠冲突报告。
- [X] 匹配/确认阶段自动关闭接龙入口，确认页面显示时间、规则和确认进度；拒绝者重算时排除，超时者保留。
- [X] 活动取消增加站内通知记录和弹窗读取，已确认活动只删除群组类别日程。
- [X] Alembic 基线迁移、健康检查、请求 ID、生产日志、备份恢复和过期会话清理。
- [X] 服务器 systemd 服务、每日备份与会话清理 timer。
- [X] 后端只读 smoke check 脚本、OpenAPI 文档和安全单元测试骨架。
- [X] 认证安全增强：登录限流、密码修改、全部会话失效、账号注销和安全响应头。

### 4. 配置、安全与发布

- [X] Android API 地址支持 `-PTEMPO_API_BASE_URL=...` 覆盖，不再必须修改 Kotlin 源码。
- [X] AI 密钥只存服务器环境变量，录音不落盘，服务端日志不记录敏感请求内容。
- [X] 密码使用 PBKDF2-SHA256 摘要，Token 服务端只保存哈希。
- [X] 登录失败按来源和账号限流；支持修改密码、注销全部会话和注销账号。
- [X] API 返回安全响应头，认证响应禁止缓存。
- [X] Android Release 禁止明文网络和系统备份；仅 Debug 允许本机 HTTP 内测。
- [X] 提供 Nginx HTTPS 反向代理模板；实际证书需要域名、DNS 和服务器管理权限。
- [X] 提供生产备份、迁移、恢复和会话清理操作说明。
- [ ] 实际申请域名、TLS 证书和配置公网安全组（需要开发者提供域名并操作证书/云平台）。
- [ ] PostgreSQL 迁移（当前 SQLite 已满足内测；正式扩容前需要确定数据库资源和迁移窗口）。
- [ ] FCM/WebSocket、外部日历导入和第三方推送（需要明确外部服务与产品策略）。

详细开发过程见 [project_nodes.md](docs/project_nodes.md)。

详细开发过程见 [project_nodes.md](docs/project_nodes.md)。

## 构建与服务地址

## 发布前工程检查

仓库已配置 GitHub Actions。每次推送或提交 Pull Request 后，会自动执行后端测试、Android Kotlin 编译和敏感配置扫描。发布前请按照 [`docs/release_checklist.md`](docs/release_checklist.md) 完成检查。

本地执行敏感配置检查：

```bash
bash tools/check_secrets.sh
```

Android 默认使用当前内测服务器地址。切换本机、局域网或 HTTPS 地址时，在 `android/gradle.properties` 或 Gradle 命令中传入：

```bash
./gradlew assembleDebug -PTEMPO_API_BASE_URL=http://127.0.0.1:8765/
```

后端部署后可执行只读检查：

```bash
cd backend
python3 scripts/smoke_check.py http://127.0.0.1:8765
```

HTTPS 模板位于 [`backend/deploy/nginx-tempo.conf.example`](backend/deploy/nginx-tempo.conf.example)。它不能替代域名和证书申请；没有域名时只能继续使用 HTTP 或 SSH/局域网测试。

## 文档

- [产品与技术架构](docs/architecture.md)
- [本地后端 API](docs/api.md)
- [Android 客户端说明](android/README.md)
- [后端说明](backend/README.md)
- [开发节点记录](docs/project_nodes.md)
- [协作开发说明](CONTRIBUTING.md)
