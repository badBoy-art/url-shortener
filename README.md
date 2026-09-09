# UrlShortener — 高可用高并发短链生成系统（Java）

参考 [ohUrlShortener](https://github.com/badBoy-art/ohUrlShortener) 的短链设计思路，用 Java 重新实现的高可用、高并发短链服务：

- **确定性短码**：`Base58(SHA-256(destUrl))` 前 6 位，同一 URL 恒生成同一短码；冲突时加盐重试，DB 唯一索引兜底
- **多目标短链**：一个短码可绑定多个 destUrl，每个目标带一个 `label` 标识，访问/查询时通过 `X-Dest-Label` 请求头选择对应目标（PC 端、移动端各取所需），无请求头回退主目标，完全兼容旧单目标短链
- **三级读缓存**：Caffeine 本地缓存（热点直读内存）→ Redis → MySQL，Redis 故障自动降级直查 MySQL，重定向不中断
- **异步写路径**：访问日志有界队列批量落库；点击计数 Redis INCR + GETDEL 原子批量回写，多实例不丢不重
- **管理员体系**（与 ohUrlShortener 对齐）：JWT 登录 + 失败锁定、管理员账号管理、短链启用/停用/列表搜索、访问日志查询与 Excel 导出、昨日/近 7 天/本月多维统计
- **打开方式定向**（ohUrlShortener v2.0 特性）：`openType` 按 User-Agent 限制打开客户端（微信/钉钉/iPhone/Android/iPad/Safari/Chrome/Firefox）
- **限流与鉴权**：创建/验证/登录接口按 IP 滑动窗口限流（Redis Lua）；管理接口 JWT 或 X-API-Key 鉴权
- **高可用**：应用完全无状态，双实例 + Nginx 负载均衡，liveness/readiness 探活
- **高并发**：Java 21 虚拟线程、HikariCP 大连接池、热点短链内存命中、创建幂等

## 架构

```
                        ┌─────────────────────────────┐
                        │        Nginx (LB)           │
                        └──────────────┬──────────────┘
                     ┌─────────────────┴─────────────────┐
                     ▼                                   ▼
              ┌────────────┐                     ┌────────────┐
              │ App 实例 1 │                     │ App 实例 2 │   无状态，可水平扩容
              │ 虚拟线程    │                     │ 虚拟线程    │
              └─────┬──────┘                     └─────┬──────┘
        ┌───────────┼──────────────────────────────────┼───────────┐
        ▼           ▼                                  ▼           ▼
   ┌─────────┐ ┌─────────┐                      ┌──────────────────────┐
   │  Redis  │ │  Redis  │  二级缓存/限流/计数    │ MySQL (主)           │  持久化存储
   │ Caffeine│ │         │                      │ t_short_link         │
   │ (本地)   │ │         │                      │ t_access_log         │
   └─────────┘ └─────────┘                      │ t_admin_user         │
                                                └──────────────────────┘

读路径:  Caffeine → Redis → MySQL（未命中做 1 分钟负缓存防穿透）
写路径:  创建同步落库 + 缓存回写；访问日志/点击计数异步批量落库
```

## 快速开始（Docker Compose，一键起全栈）

```bash
docker compose up -d --build
```

- 服务入口：`http://localhost`（Nginx 负载均衡到两个应用实例）
- Swagger UI：`http://localhost/swagger-ui.html`
- 应用容器端口不对外暴露（仅 Nginx 80 端口对外），本机调试见下文
- 数据库：宿主机 MySQL（127.0.0.1:3306 / `study` 库），应用容器经 `host.docker.internal` 访问；应用启动自动建表。库地址/账号可通过 `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_DB`/`MYSQL_USER`/`MYSQL_PASSWORD` 环境变量覆盖（见 compose 文件）

### 本地开发模式

数据库使用本机 MySQL（127.0.0.1:3306 / `study` 库），应用启动时自动执行 schema.sql 建表（`CREATE TABLE IF NOT EXISTS`，不影响库中已有表）。

```bash
# 只起 Redis（端口 6379）
docker compose up -d redis

# 用 JDK 21 启动应用
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home
MYSQL_DB=study MYSQL_USER=root MYSQL_PASSWORD=zhaoZ1230 mvn spring-boot:run
```

环境变量可覆盖默认配置：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USER`、`MYSQL_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`APP_BASE_URL`、`APP_API_KEY`。

## API

| 方法 | 路径 | 说明 |
|---|---|---|---|
| POST | `/api/url` | 创建短链，按 IP 限流（默认 10 次/秒） |
| GET | `/api/url/{code}` | 查询短链信息 |
| DELETE | `/api/url/{code}` | 删除（需 JWT 或 `X-API-Key`，访问日志保留） |
| PUT | `/api/url/{code}/change_state` | 启用/停用短链（需 JWT 或 `X-API-Key`） |
| GET | `/api/url/{code}/stats` | 单链统计：总点击、今日/昨日/近 7 天/本月 PV/UV、分时 PV |
| GET | `/api/stats/overview` | 全局统计（需 JWT 或 `X-API-Key`） |
| GET | `/{code}` | 302 跳转；密码保护返回验证页；打开方式不匹配 404；过期/停用 410 |
| POST | `/{code}/verify` | 密码验证（限流保护） |
| POST | `/api/login` | 管理员登录，成功返回 JWT；失败锁定（限流保护） |
| GET | `/api/urls` | 短链列表/关键词搜索（分页，需 JWT 或 `X-API-Key`） |
| GET | `/api/logs` | 访问日志查询（短码 + 时间范围，分页，含独立 IP 数） |
| GET | `/api/logs/export` | 访问日志导出 Excel（.xlsx） |
| GET/POST | `/api/account` | 管理员账号列表 / 新增（需 JWT 或 `X-API-Key`） |
| PUT | `/api/account/{account}/update` | 重置管理员密码（需 JWT 或 `X-API-Key`） |
| GET | `/health` `/ready` | LB 探活 |

> 以上 `/api/*` 管理接口（除创建与查询外）需 `Authorization: Bearer <token>` 或 `X-API-Key` 请求头。
> 默认管理员：账号 `admin`，密码 `ohUrlShortener`（首次启动自动创建，可用 `APP_ADMIN_ACCOUNT`/`APP_ADMIN_PASSWORD` 覆盖，**生产必改**）。

### 管理员登录与 JWT

```bash
# 登录获取 token（连续失败 5 次锁定 10 分钟，可配置）
curl -X POST http://localhost/api/login \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin","password":"ohUrlShortener"}'

# 携带 token 调用管理接口
curl -H 'Authorization: Bearer <token>' 'http://localhost/api/urls?keyword=example'
```

JWT 有效期默认 12 小时；令牌校验时实时核对账号启用状态，停用账号立即失效。

### 打开方式（openType）

创建短链时可指定 `openType`，访问时按 User-Agent 判定，不匹配返回提示页（404）：

| 值 | 打开方式 | 值 | 打开方式 |
|---|---|---|---|
| 0 | 全部（默认） | 5 | iPad |
| 1 | 微信 | 6 | Safari |
| 2 | 钉钉 | 7 | Chrome |
| 3 | iPhone | 8 | Firefox |
| 4 | Android | | |

### 创建示例

```bash
curl -X POST http://localhost/api/url \
  -H 'Content-Type: application/json' \
  -d '{
    "destUrl": "https://www.example.com/very/long/path?a=1",
    "password": "optional",
    "description": "可选备注",
    "expiredAt": "2026-12-31T23:59:59",
    "shortCode": "custom1"
  }'
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "shortCode": "custom1",
    "shortUrl": "http://localhost/custom1",
    "destUrl": "https://www.example.com/very/long/path?a=1",
    "description": "可选备注",
    "expired": false,
    "clickCount": 0,
    "createdAt": "2026-09-07 20:00:00",
    "expiredAt": "2026-12-31 23:59:59"
  }
}
```

- `destUrl` 仅允许 http/https；`shortCode` 为 4-16 位 Base58 字符（不含 `0 O I l`），被占用返回 409
- 同一 `destUrl` 重复创建返回同一短码（幂等），并发创建安全

### 多目标短链（X-Dest-Label）

一个短码可绑定多个 destUrl，每个目标带一个 `label` 标识；访问（`GET /{code}`、`POST /{code}/verify`）或查询信息（`GET /api/url/{code}`）时通过 `X-Dest-Label` 请求头指定想要的目标：

```bash
# 创建：pc 与 mobile 各一个目标
curl -X POST http://localhost/api/url \
  -H 'Content-Type: application/json' \
  -d '{
    "destUrl": "https://www.example.com/pc/page",
    "destinations": [
      {"label": "pc",     "destUrl": "https://www.example.com/pc/page"},
      {"label": "mobile", "destUrl": "https://m.example.com/page"}
    ]
  }'

# PC 端访问：请求头 X-Dest-Label: pc
curl -i -H 'X-Dest-Label: pc' http://localhost/{code}      # 302 → https://www.example.com/pc/page

# 移动端访问：请求头 X-Dest-Label: mobile
curl -i -H 'X-Dest-Label: mobile' http://localhost/{code}  # 302 → https://m.example.com/page

# 不带请求头 → 回退主目标（destUrl 字段，未提供时取 destinations 第一个）
curl -i http://localhost/{code}                            # 302 → https://www.example.com/pc/page
```

- 请求头 key 固定为 `X-Dest-Label`，value 为创建时指定的 `label`；value 无匹配时回退主目标，不报错
- `destUrl` 与 `destinations` 至少提供一个；只提供 `destinations` 时第一个目标即主目标（决定短码与默认跳转）
- 每个 `label` 在该短码内唯一，重复返回 400；最多 20 个目标
- 老短链（单 destUrl）完全兼容：无 destinations 数据，请求头被忽略
- 查询信息接口 `GET /api/url/{code}` 同样支持 `X-Dest-Label`，响应中 `destinations` 列出全部目标

## 核心设计

### 短码生成（确定性哈希 + 冲突重试）

`code = Base58(SHA-256(destUrl))[0..6]`（长度可配 `app.short-code.length`）。同 URL 同码，天然去重；码被其它 URL 占用时以 `destUrl + ":" + i` 加盐重试（最多 5 次）。DB `uk_short_code` 唯一索引是最终仲裁者，并发下捕获 `DuplicateKeyException` 后核对占用者 URL：相同则幂等返回，不同则继续重试。创建成功后缓存 `url:code:{sha256}`，同 URL 高频并发创建只算一次哈希。

### 读路径（三级缓存 + 降级）

1. Caffeine 本地缓存（TTL 10 分钟、1 万条）：热点短链（爆款链接）直接内存命中，零网络开销
2. Redis（TTL 24 小时）：跨实例共享
3. MySQL：兜底；未命中短码本地负缓存 1 分钟，防随机码探测打穿 DB

Redis 故障自动降级（fail-open）：本地缓存 + MySQL 照常服务，后台每 30s 探测自动恢复。

删除/停用等变更通过 Redis pub/sub 广播失效（`link:evict`），所有实例的本地缓存毫秒级同步剔除；Redis 故障时退化为仅本实例失效，读路径不受影响。

### 写路径（异步、批量、不丢不重）

- **访问日志**：请求线程仅 `offer` 到有界队列（10 万），定时 1s/500 条批量单语句插入；队列满丢弃日志，主链路不受影响
- **点击计数**：请求内仅 `INCR click:{code}` + `SADD click:dirty`（O(1)）；定时任务 `GETDEL` 原子取回计数并批量 `UPDATE click_count += n` 回写。GETDEL 保证多实例场景同一计数只被取走一次；INCR→SADD 的顺序保证无丢失；Redis 故障时降级直写 DB

### 限流与鉴权

- 创建/密码验证/登录接口按 IP 滑动窗口限流（Redis ZSET + Lua，原子），默认 10 次/秒，超限 429 + Retry-After；Redis 故障时 fail-open
- 管理接口（`/api/*` 除创建与查询外、删除、全局统计）鉴权：`X-API-Key`（常量时间比较）或 Bearer JWT（HS256，校验签名 + 账号启用状态）
- 登录失败锁定：同一账号或同一 IP 连续失败 5 次锁定 10 分钟（Redis 计数，可配置）；替代 ohUrlShortener 的图形验证码，Redis 故障时锁定失效但不阻断登录
- 管理员密码 BCrypt 存储；默认账号 `admin`/`ohUrlShortener` 首次启动自动初始化

### 访问日志与统计

- 访问日志支持按短码 + 时间范围查询（分页，含独立 IP 数）与导出 Excel（`.xlsx`，行数受 `app.admin.export-max-rows` 限制）
- 统计维度：单链与全局均提供总点击、今日/昨日/近 7 天/本月 PV/UV、今日分时 PV。与 ohUrlShortener 预计算表不同，本实现按需 SQL 聚合实时计算，免去定时汇总任务

### 高可用

- 应用无状态：本地缓存仅加速，可任意扩缩容；任意实例宕机 Nginx 自动剔除
- Redis 降级、MySQL 连接池超时 3s 快速失败，保证故障时响应及时
- `/health`（存活）与 `/ready`（依赖就绪）供 LB 探活

## 配置项（application.yml，均可用环境变量覆盖）

| 配置 | 默认 | 说明 |
|---|---|---|
| `app.short-code.length` | 6 | 短码长度（62^6 ≈ 568 亿空间） |
| `app.short-code.max-retries` | 5 | 冲突加盐重试上限 |
| `app.cache.local-ttl-minutes` | 10 | 本地缓存 TTL |
| `app.cache.local-max-size` | 10000 | 本地缓存容量 |
| `app.cache.redis-ttl-hours` | 24 | Redis 缓存 TTL |
| `app.rate-limit.limit` / `window-seconds` | 10 / 1 | 创建/登录等接口限流 |
| `app.access-log.batch-size` / `flush-interval-ms` | 500 / 1000 | 日志批量写入 |
| `app.count-flush-interval-ms` | 30000 | 计数回写间隔 |
| `app.api-key` | change-me-admin-key | 管理接口密钥（**生产必改**） |
| `auth.jwt-secret` | 内置示例值 | JWT 签名密钥（≥32 字节，**生产必改**） |
| `auth.jwt-expire-hours` | 12 | JWT 有效期（小时） |
| `auth.login-max-fails` | 5 | 登录失败锁定阈值 |
| `auth.login-lock-minutes` | 10 | 锁定窗口（分钟） |
| `admin.init-account` / `init-password` | admin / ohUrlShortener | 默认管理员（仅库为空时初始化，**生产必改**） |
| `admin.export-max-rows` | 50000 | 日志导出 Excel 行数上限 |

## 测试

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home
mvn test   # 需本机 Docker 运行（Testcontainers 拉起 MySQL/Redis）
```

覆盖：Base58/短码生成/UA 判定单元测试；集成测试（Testcontainers）覆盖创建/重定向/幂等/自定义码冲突/非法 URL/404/410/密码流程/并发创建同码/统计/限流 429/管理员登录与失败锁定/管理接口鉴权/短链启停/打开方式定向/日志查询与 Excel 导出/管理员账号管理。

## 压测

```bash
brew install hey   # 或使用 ab
./scripts/bench.sh http://localhost:8080 100 10000
```

## 生产部署建议

- MySQL 主从 + Redis 哨兵/集群；应用实例数按流量水平扩容
- 访问日志表按月分区或定期归档，防止无限增长
- `APP_BASE_URL` 配置为公网域名；`APP_API_KEY`、`APP_JWT_SECRET`、`APP_ADMIN_PASSWORD` 使用强随机值
- 如流量极大，可进一步引入 Kafka 解耦访问日志、布隆过滤器拦截无效短码查询
