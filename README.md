# tf-common

FishMatrix 统一鉴权、异常处理、链路追踪、结构化日志、文件存储与消息发布基础库（Spring Boot 3.5 / Java 17）。

各 Java 服务通过该库获得一致的：

- **统一鉴权**：网关签名身份头校验、`CurrentUser` / `UserContext`、`@RequirePermission`、用户中台 introspection 回退；
- **链路上下文**：从 W3C `traceparent` 提取 traceId，写入 MDC，回写 `X-Trace-ID` / `X-Request-ID`；
- **统一错误响应**：固定错误码 + `traceId` / `requestId` / `path` / `timestamp`，前端可展示“编号”；
- **全局异常处理**：参数校验、认证、404、超时、依赖不可用等映射为稳定错误码；
- **结构化日志**：单行 JSON（Logstash Encoder），固定字段与链路字段；
- **服务间传播**：`RestClient` 自动携带 `traceparent` 与 `X-Request-ID`；
- **异步上下文**：`Traces.wrap(...)` 把链路带到线程池任务中；
- **文件存储**：`FileStorage` 抽象与本地卷实现，落盘带 SHA-256，库表只存 `storageKey`；
- **消息发布**：`EventPublisher` + RocketMQ 适配器，默认关闭、零连接；`RPC` 仍为预留开关。

## 依赖

```xml
<dependency>
  <groupId>com.fishmatrix</groupId>
  <artifactId>tf-common</artifactId>
  <version>1.2.0</version>
</dependency>
```

仓库配置：

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/take-fish/tf-common</url>
  </repository>
</repositories>
```

GitHub Packages 的 Maven 仓库即使对公开包也要求认证。CI 中使用 `actions/setup-java` 的 `server-id/server-username/server-password` 自动生成 `~/.m2/settings.xml`；本地开发在 `settings.xml` 的 `<servers>` 中配置：

```xml
<server>
  <id>github</id>
  <username>你的 GitHub 用户名</username>
  <password>${env.GITHUB_TOKEN}</password>
</server>
```

`GITHUB_TOKEN` 至少需要 `read:packages`。

## 快速接入

1. `logback-spring.xml` 引入统一编码器：

```xml
<configuration>
  <include resource="com/fishmatrix/common/fishmatrix-logback-base.xml"/>
</configuration>
```

2. `application.yml` 打开环境信息：

```yaml
app:
  environment: ${APP_ENV:production}
  version: ${APP_VERSION:dev}
```

3. 服务间调用使用 `RestClientTraceInterceptor`（或直接依赖自动配置注入的 `RestClient.Builder`）。

4. 启动类无需额外注解：`FishCommonAutoConfiguration` 通过 `AutoConfiguration.imports` 注册。

## 统一鉴权

业务服务开启鉴权（用户中台自身必须关闭，避免自调用）：

```yaml
fm:
  auth:
    enabled: ${FM_AUTH_ENABLED:true}
    user-service-url: ${TF_USER_URL:http://127.0.0.1:8101}
    identity-secret: ${FM_IDENTITY_SECRET:}
    allow-bearer-fallback: ${FM_AUTH_BEARER_FALLBACK:true}
    cache-ttl: 30s
```

`AuthInterceptor` 对 `/api/**` 按以下顺序解析身份：

1. 网关注入的 `X-FM-*` 身份头 → `IdentitySigner` 校验 HMAC 签名、有效期与时钟偏差；
2. 无身份头且 `allow-bearer-fallback=true` → 用 `Authorization: Bearer` 调用户中台 `GET /api/v1/user/auth/me`，结果按 token 的 SHA-256 缓存（默认 30 秒，内存不保留明文 token）；
3. 两者都失败 → 401；用户中台不可用 → 503 `AUTH_UNAVAILABLE`，**绝不降级为匿名**。

业务代码只从上下文取身份，禁止信任查询参数、请求体或客户端自造的用户标识：

```java
CurrentUser user = UserContext.require();
long id = user.id();
boolean admin = user.isAdmin();
```

接口级校验用 `@RequirePermission`（多个权限码满足任意一个即可）：

```java
@RequirePermission("reader.public.upload")
@PostMapping("/books")
public BookDto upload(...) { ... }
```

资源级归属校验（例如“只能删自己的”）仍由业务服务根据 `owner_id` 判断，通用库不替代 ACL。

## 文件存储

```yaml
fm:
  storage:
    enabled: true
    base-dir: ${FM_STORAGE_BASE_DIR:/app/data/files}
    max-file-bytes: 52428800
```

开启后自动注册 `FileStorage`（默认 `LocalFileStorage`）。`store()` 会流式落盘、限制大小、按白名单推导扩展名、用随机 UUID 生成 `storageKey` 并计算 SHA-256；数据库只保存返回的元数据：

```java
StoredFile file = fileStorage.store(input, filename, contentType, "reader", 8L * 1024 * 1024);
// file.storageKey() / file.size() / file.sha256() / file.contentType()
```

访问权限永远由数据库记录决定，不能依赖存储路径的不可猜测性。后续切换 MinIO/S3 只需替换 `FileStorage` 实现（`@ConditionalOnMissingBean`）。

## 消息与 RPC

### 事件发布（RocketMQ）

1.2.0 起内置 RocketMQ 适配器，默认仍不连接任何中间件：

```yaml
fm:
  messaging:
    enabled: false              # true 时创建生产者并校验连通性，失败则应用启动失败
    log-events: false           # true 时成功发布也记 INFO
    name-server: rocketmq-nameserver:9876
    topic-prefix: FM_           # 测试环境使用 FM_TEST_
    producer-group: fm-game     # 为空时按 fm-{spring.application.name} 推导
    send-timeout-ms: 3000
    diagnostics-on-startup: false
  rpc:
    enabled: false
    nacos-namespace: fm-prod
    group: FM
    timeout-ms: 3000
```

- `enabled=false`（默认）：使用 `NoopEventPublisher`，不建立任何连接。
- `enabled=true`：`name-server` 必填；启动时创建 `DefaultMQProducer`，broker 不可达会让应用启动失败，避免开关打开却静默不生效。
- topic = `topic-prefix + type`，tag = `type`，keys = 事件 `key`（为空则用 eventId），消息体是 `DomainEvent` 的 JSON。
- 发送为同步调用，失败抛 `EventPublishException` 并记 ERROR，重试/补偿由调用方决定。
- `diagnostics-on-startup=true` 时在启动完成后发布一条 `SYSTEM_PROBE` 事件，用于连通性验收。
- `type` 只允许 `[A-Za-z0-9_-]`，约定使用大写 + 下划线（如 `MATCH_FINISHED`）。

业务代码只依赖 `EventPublisher` 接口：

```java
eventPublisher.publish(DomainEvent.of("READER_BOOK_UPLOADED", "tf-reader",
        TraceIds.currentTraceId(), "book:" + id, Map.of("bookId", id)));
```

`DomainEvent` 的 `type` 与字段是跨服务契约，接入消费者后不能随意改名。`fm.rpc.enabled` 仅预留开关，当前没有 Dubbo 适配器。

## 主要组件

| 类 | 作用 |
|---|---|
| `AuthInterceptor` / `IdentitySigner` | 签名身份头校验、Bearer 回退、权限校验 |
| `CurrentUser` / `UserContext` / `RequirePermission` | 当前身份、上下文与接口权限注解 |
| `AuthClient` / `IdentityHeaders` | 用户中台 introspection（带缓存）、身份头常量 |
| `FileStorage` / `LocalFileStorage` / `StoredFile` | 文件存储抽象、本地卷实现与落盘元数据 |
| `EventPublisher` / `DomainEvent` | 领域事件发布口子与跨服务契约 |
| `NoopEventPublisher` / `RocketMqEventPublisher` | 默认无副作用实现与 RocketMQ 适配器 |
| `EventPublishException` | 发布失败异常（序列化或 broker 拒绝） |
| `TraceContextFilter` | MDC 写入、响应头回写、API/WebSocket 访问日志 |
| `TraceHeaders` / `TraceIds` / `Traces` | 编号生成与校验、读取当前上下文、异步包装 |
| `RestClientTraceInterceptor` | 出站请求传播 `traceparent` / `X-Request-ID` |
| `GlobalExceptionHandler` | 统一异常到 HTTP 状态与错误码的映射 |
| `ApiErrorResponse` | 统一错误响应体 |
| `ApiException` / `ErrorCodes` | 业务异常与稳定错误码 |
| `fishmatrix-logback-base.xml` | JSON 日志编码器与固定字段 |

## 日志字段

固定字段：`@timestamp`、`message`、`logger`、`thread`、`level`、`service`、`env`、`version`。
链路字段：`trace_id`、`span_id`、`request_id`。

访问日志由 `TraceContextFilter` 输出，`event=http.access`，包含 `http_method`、`http_path`、`http_status`、`duration_ms`，不记录查询串。

## 错误响应

```json
{
  "error": "VALIDATION_FAILED",
  "message": "用户名不能为空",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "path": "/api/v1/user/login",
  "timestamp": "2026-10-22T07:00:00Z"
}
```

`ErrorCodes` 中的字符串是对外契约，禁止随意改名。

## 发布

版本号在 `pom.xml` 中维护。合入 master 后打版本标签（例如 `v1.0.2`）触发 `publish` 工作流，构建并发布到 GitHub Packages。

```bash
git tag v1.0.2
git push origin v1.0.2
```

本地临时验证：

```bash
mvn -B -ntp verify
mvn -B -ntp install
```

## 相关文档

集中日志检索、Loki / Alloy / Grafana 运维与排查流程见 `tf-deploy/docs/日志与链路追踪.md`。
