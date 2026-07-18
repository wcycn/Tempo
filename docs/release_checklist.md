# Tempo 发布前工程清单

## 自动检查

- GitHub Actions 的 backend、android、security 三个任务全部通过。
- 后端迁移、单元测试和 `smoke_check.py` 通过。
- Android Debug/Release 编译成功，版本号与 Release 页面一致。

## 安全检查

- API Key、AI Key、服务器密码和 Token 不在仓库中。
- `.env`、数据库、签名文件和 `local.properties` 未被提交。
- 正式 Release 使用 HTTPS 地址；HTTP 仅用于 Debug/内测。
- APK 使用独立签名密钥，签名文件和密码不上传 GitHub。
- 服务器已完成数据库备份，并确认可以恢复。

## 发布材料

- APK 或 AAB 文件。
- 版本号、更新说明和已知限制。
- 安装说明、隐私说明和问题反馈模板。
- 测试账号说明；不要在文档中放真实密码。
