#!/usr/bin/env bash
set -euo pipefail

# Run as root on a fresh Alibaba Cloud Linux/Debian-like server after the
# backend source has been copied to /srv/tempo/app.
APP_DIR=/srv/tempo/app
VENV_DIR=/srv/tempo/venv
BACKUP_DIR=/srv/tempo/backups

if [[ "$(id -u)" != "0" ]]; then
  echo "请使用 root 执行此脚本" >&2
  exit 1
fi

id tempo >/dev/null 2>&1 || useradd --system --home-dir /srv/tempo --shell /usr/sbin/nologin tempo
install -d -o tempo -g tempo -m 750 "$APP_DIR" "$BACKUP_DIR"
python3 -m venv "$VENV_DIR"
"$VENV_DIR/bin/python" -m pip install --upgrade pip
"$VENV_DIR/bin/pip" install -r "$APP_DIR/requirements.txt"

chown -R tempo:tempo /srv/tempo
chmod 750 "$APP_DIR"

install -m 644 "$APP_DIR/deploy/tempo-backend.service" /etc/systemd/system/tempo-backend.service
install -m 644 "$APP_DIR/deploy/tempo-backup.service" /etc/systemd/system/tempo-backup.service
install -m 644 "$APP_DIR/deploy/tempo-backup.timer" /etc/systemd/system/tempo-backup.timer
install -m 644 "$APP_DIR/deploy/tempo-cleanup-sessions.service" /etc/systemd/system/tempo-cleanup-sessions.service
install -m 644 "$APP_DIR/deploy/tempo-cleanup-sessions.timer" /etc/systemd/system/tempo-cleanup-sessions.timer

systemctl daemon-reload
systemctl enable tempo-backend.service tempo-backup.timer tempo-cleanup-sessions.timer
echo "安装完成。请先配置 $APP_DIR/.env，再执行 scripts/migrate.py，最后启动 tempo-backend。"
