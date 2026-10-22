# tf-common

FishMatrix 统一异常处理、链路追踪与结构化日志基础库（Spring Boot 3.5 / Java 17）。

各 Java 服务通过该库获得一致的：

- **链路上下文**：从 W3C `traceparent` 提取 traceId，写入 MDC，回写 `X-Trace-ID` / `X-Request-ID`；
- **统一错误响应**：固定错误码 + `traceId` / `requestId` / `path` / `timestamp`，前端可展示“编号”；
- **全局异常处理**：参数校验、认证、404、超时、依赖不可用等映射为稳定错误码；
- **结构化日志**：单行 JSON（Logstash Encoder），固定字段与链路字段；
- **服务间传播**：`RestClient` 自动携带 `traceparent` 与 `X-Request-ID`；
- **异步上下文**：`Traces.wrap(...)` 把链路带到线程池任务中。

## 依赖

```xml
<dependency>
  <groupId>com.fishmatrix</groupId>
  <artifactId>tf-common</artifactId>
  <version>1.0.2</version>
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

## 主要组件

| 类 | 作用 |
|---|---|
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
