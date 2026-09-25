# 部署

Ubuntu 22.04 或 24.04。浏览器和桌面客户端只访问 Nginx 的 443。业务进程留在本机。

```
客户端
  → Nginx :443
       → Web 宿主 :8788（可选）
       → 后端 :8181
            → MySQL、Redis
            → 控制平面 :8281 → e签宝
```

| 进程 | 端口 | 产物 |
|------|------|------|
| Nginx | 80 / 443 | 站点与反代 |
| Web 宿主 | 8788 | `studio-web` 构建结果 |
| 后端 | 8181 | `backend/target/backend-1.5.0.jar` |
| 控制平面 | 8281 | 控制平面 jar |
| MySQL | 3306 | 库 `jy_financial` |
| Redis | 6379 | Session |

桌面安装包不部署在这台机器上。客户安装后连接这里的 API。包内已含 OpenClaw，服务器不必再装一份给桌面客户端。

密钥、e签宝应用号和数据库密码只放在服务器环境变量或 systemd 单元里，不要写回仓库。下面用占位符。

## 1. 安装系统包

```bash
apt update && apt install -y openjdk-21-jdk mysql-server redis-server nginx certbot python3-certbot-nginx
```

需要网页版时再装 Node.js 22，并在服务器上构建或上传 `studio-web` 的产物。

## 2. 数据库与 Redis

```bash
systemctl enable --now mysql redis-server
mysql -u root <<'EOF'
CREATE DATABASE IF NOT EXISTS jy_financial
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'jy_app'@'localhost' IDENTIFIED BY '换成强密码';
GRANT ALL PRIVILEGES ON jy_financial.* TO 'jy_app'@'localhost';
FLUSH PRIVILEGES;
EOF
```

## 3. 放置 jar

```text
/opt/jyparalegal/backend/backend-1.5.0.jar
/opt/jyparalegal/control-plane/control-plane-1.0.0.jar
```

systemd 示例只列出必须替换的变量。`User=root` 可以改成专用账号。

控制平面：工作目录 `/opt/jyparalegal/control-plane`，启动 `java -Xmx256m -jar control-plane-1.0.0.jar`。环境变量包括 `ESIGN_APP_ID`、`ESIGN_APP_SECRET`、`CP_SERVICE_USERNAME`、`CP_SERVICE_PASSWORD`、`CP_JWT_SECRET`、`DP_BASE_URL=http://127.0.0.1:8181`、`CP_ESIGN_CALLBACK_TOKEN`。

后端：工作目录 `/opt/jyparalegal/backend`，在 MySQL 与 Redis 之后启动，`java -Xmx768m -jar backend-1.5.0.jar`。环境变量包括 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`CP_MODE=required`、`CP_BASE_URL=http://127.0.0.1:8281`、与控制平面一致的服务账号，以及 `JWT_SECRET`、`ESIGN_CALLBACK_TOKEN`。

健康检查：

```bash
curl -fsS http://127.0.0.1:8181/actuator/health
curl -fsS http://127.0.0.1:8281/actuator/health
```

## 4. Nginx 与证书

用 certbot 为站点申请证书。反代 `/api` 到 `127.0.0.1:8181`。若启用网页版，再把页面和 `/ws` 转到 `127.0.0.1:8788`。只开放 80 和 443。

Flyway 在后端第一次启动时建表。库若是按更早的迁移号建的，校验和不一致时要先处理 `flyway_schema_history` 或按备份重建，见 [backend/README.md](../backend/README.md)。
