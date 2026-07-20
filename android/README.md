# Tempo Android 原型

这是面向 Android 手机的 Jetpack Compose 原生原型，视觉采用黑/深灰卡片和 Tempo 蓝色主题，支持浅色、深色和跟随系统。

Android 应用标识为 `cn.wcylab.tempo`，源码包统一位于 `cn.wcylab.tempo`。

## 打开方式

用 Android Studio 打开仓库中的 `android/`，等待 Gradle 同步后运行 `app` 模块即可。建议使用 Android Studio Jellyfish 或更新版本，JDK 17，Android SDK 35。

## 已实现的页面

- 日程：月历、状态图例、今日时间线、创建日程弹窗
- 找时间：待应答邀约、同意/换时间入口
- 群组：接龙人数、截止倒计时、成团进度
- 找时间：好友关系、状态查看、候选时间和双人邀约处理
- 群组：成员管理、接龙、三种匹配规则、二次确认和群主重算
- 我的：资料编辑、标签管理、主题选择和 AI 内测入口

## 当前边界

- 日程和标签保存在本机 Room，按账号隔离；游客也可以离线使用。Token 使用 Android 加密存储，Release 禁止明文 HTTP 和系统备份。
- 账号、好友、邀约、群组活动和 AI 解析请求走后端 API。
- 推送通知、系统级日程提醒、外部日历导入和跨设备个人日程同步尚未启用。
- Android 服务地址可通过 `-PTEMPO_API_BASE_URL=https://你的域名/` 覆盖。
