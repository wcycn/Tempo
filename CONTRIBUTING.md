# Tempo 协作开发说明

当前项目由个人开发维护，以下规则保持轻量；未来有新成员加入时，可以直接沿用。

## 开发前

```bash
git pull --rebase origin main
```

确认 Android Studio 可以打开 `android/`，并确认后端健康检查可以运行。

## 分支规则

个人开发时可以直接在 `main` 上提交小改动。功能开发或有合作者加入后，使用功能分支：

```text
feature/calendar-time-grid
feature/backend-api
fix/android-build
docs/update-readme
```

分支完成后合并回 `main`，再删除已经合并的分支。

## Commit 规则

使用简短、清晰的前缀：

```text
feat: 新增功能
fix: 修复问题
refactor: 重构代码
docs: 更新文档
chore: 工程配置或目录调整
test: 增加测试
```

示例：

```text
feat: add remote calendar repository
fix: resolve android jvm target mismatch
docs: update backend setup guide
```

## 提交前检查

Android：

```text
Android Studio → Build → Rebuild Project
```

后端：

```bash
python3 -m py_compile backend/server.py
python3 backend/server.py
curl http://127.0.0.1:8765/api/health
```

## 不要提交的内容

- `local.properties`
- `.idea/`
- `.gradle/`
- `build/`
- `*.db`
- `__pycache__/`
- 密码、Token、私钥和本机 IP 配置

这些规则已经写入根目录 `.gitignore`。

## 合作者加入后的流程

1. 从 `main` 创建功能分支。
2. 每个分支只处理一个明确功能。
3. 提交前完成构建和相关测试。
4. 发起 Pull Request，并写明改动、验证方式和已知问题。
5. 合并后删除功能分支。
