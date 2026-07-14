# 产品与技术架构

## 产品模块

```text
日历底座
├── 月 / 周 / 日 / 日程视图
├── 15 分钟时间网格
├── 农历、节气、法定节假日
└── 红 / 绿 / 黄状态

社交协调
├── 好友可用状态
├── 单人匹配与邀约
├── 不对称邀约锁定
└── 群组接龙成团
```

## 技术分层

```text
Compose UI
    ↓
ViewModel / UI State
    ↓
CalendarRepository
    ↓
Remote API（Retrofit）或本地 Fake Repository
    ↓
Python MVP / 后续正式服务端
```

页面不直接访问数据库。所有日程、好友、邀约操作经过 Repository，便于先使用本地数据，再切换为网络数据。

## 数据隐私

好友查询日历时，后端只返回状态色块、时间范围和可匹配信息，不返回活动名称、描述或个人分类。隐私过滤必须放在服务端，不能只依赖 Android 客户端隐藏。

## 版本路线

### MVP

本机 Python + SQLite，完成日程、时间段扫描和邀约接口。

### Beta

FastAPI/Spring Boot + PostgreSQL + Redis，接入登录、好友、真实匹配、事务锁和 WebSocket。

### Production

HTTPS、JWT、推送通知、数据库迁移、监控、备份、限流和多端同步。
