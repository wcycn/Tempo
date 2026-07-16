# Tempo 开发节点记录

这个文件记录项目的实际开发过程。每次完成一个明确节点后，更新“状态、变更、验证结果和下一步”，并与对应代码一起提交。

## 节点状态定义

- `done`：代码已完成，并通过对应验证。
- `partial`：有可运行实现，但仍有明确缺口。
- `blocked`：受到环境、设备或外部依赖阻塞。
- `planned`：已设计，尚未开始。

## 当前节点

### N-001 · 工程结构整理

- 状态：`done`
- 内容：将原始目录整理为 `android/`、`backend/`、`prototype/`、`docs/`。
- 结果：增加根 README、`.gitignore`、架构文档和 API 文档。
- 验证：Git 仓库已完成首个正式版本提交。

### N-002 · Android Compose 原型

- 状态：`partial`
- 内容：完成竖屏入口、底部导航、月/周/日/日程页面、日期切换、创建日程弹窗和 15 分钟选择器。
- 已知缺口：部分好友、通知和邀约数据仍为示例数据；尚未连接后端。
- 下一步：抽离 ViewModel 和 Repository，去除页面内硬编码数据。

### N-003 · 本地后端 MVP

- 状态：`partial`
- 内容：Python 标准库 HTTP 服务 + SQLite，支持健康检查、日程、邀约和基础时间扫描。
- 验证：`python3 -m py_compile backend/server.py` 通过；健康检查接口曾验证可返回成功结果。
- 已知缺口：没有账户认证、好友关系、完整多人匹配和事务锁。
- 下一步：拆分路由/服务/数据库层，增加接口测试。

### N-004 · Android 构建与设备验证

- 状态：`partial`
- 内容：解决 JVM target 不一致问题，项目曾在 Android Studio 中完成 Gradle 同步并构建。
- 当前阻塞：命令行重新构建需要下载 Gradle，受当前网络/权限环境影响，尚未在本次整理后再次独立验证。
- 下一步：在开发机 Android Studio 中执行 `Sync Project with Gradle Files` 和 `Build → Rebuild Project`。

### N-005 · 前后端联调

- 状态：`planned`
- 目标：Android 通过 Retrofit/OkHttp 调用本机或树莓派后端。
- 完成标准：创建日程后重启 App 数据仍存在；后端接口返回错误时页面能显示重试状态。

### N-006 · 账户与好友

- 状态：`planned`
- 目标：完成用户、登录、好友关系和服务端隐私过滤。
- 完成标准：好友只能获取状态色块和时间范围，不能获取标题、描述和分类。

### N-007 · 单人匹配与邀约状态机

- 状态：`planned`
- 目标：完成绿色扫描、黄色机动扫描、发起方锁定和接收方比价机制。
- 完成标准：完成发起、待应答、同意、拒绝、撤回、失效和冲突释放全链路。

### N-008 · 群组接龙

- 状态：`planned`
- 目标：完成群主、成员、最低人数、二次确认和失败重算。
- 完成标准：多人可以完成一次成功成团和一次失败重算。

### N-009 · 通知与兼容性

- 状态：`planned`
- 目标：完成 App 内通知、推送、Android 设备矩阵、树莓派部署和网络稳定性验证。
- 完成标准：目标设备能稳定连接后端，核心状态变化可及时同步。

### N-010 · Android 前端状态层重构

- 状态：`partial`
- 目标：让页面只负责展示，日程数据由 ViewModel 和 Repository 统一管理。
- 已完成：新增 `CalendarViewModel`、`MockCalendarDataSource`；创建日程通过 ViewModel 更新，月/周/日/日程视图共享同一份前端演示状态。
- 已完成：补充 `lifecycle-viewmodel-compose` 依赖。
- 已完成：已有日程可以点击进入编辑，修改名称、时间和状态；删除操作增加二次确认，并通过 ViewModel 从所有视图移除。
- 验证结果：Android Studio 从 `android/` 目录打开后，Gradle 编译成功，当前修改未发现编译错误。
- 已完成：好友、待应答邀约、通知和群组演示数据移出页面，统一由 `MockContentDataSource` 提供。
- 已调整：移除 Android 前端的 `SharedPreferences` 和本地持久化；前端只保留内存 Mock 数据，正式数据统一由后端 API 提供。
- 已完成：时间选择器打开时会回显已有起止时间。
- 已知缺口：页面组件仍集中在 `MainActivity.kt`；API 客户端尚未接入。
- 下一步：拆分页面文件，并实现纯 API 边界的 Remote Repository。

## 每次开发节点的记录模板

```markdown
### N-XXX · 节点名称

- 状态：`planned`
- 目标：
- 变更文件：
- 已完成：
- 验证命令：
- 验证结果：
- 已知问题：
- 下一步：
```

## 变更日志

### 2026-07-16

- 建立完整阶段计划。
- 新增项目开发节点记录。
- 明确当前 Android 原型、后端 MVP 和联网联调的边界。
- 开始 Android 前端状态层重构，新增 ViewModel 和本地 Repository。
