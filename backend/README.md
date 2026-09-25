# 后端

Spring Boot 3.5.4，Java 21。为 Studio 和业务页面提供 REST 与 WebSocket，端口 **8181**。

接口文档：http://localhost:8181/swagger-ui.html  
OpenAPI：http://localhost:8181/v3/api-docs

## 运行

```bash
cd backend
mvn spring-boot:run
```

打包：

```bash
mvn clean package -DskipTests
java -jar target/backend-1.5.0.jar
```

数据库脚本在 `src/main/resources/db/migration/`，由 Flyway 在启动时执行。本地库名、账号以 `application.yml` 和环境变量为准。开发环境常用 MySQL `13390`、Redis `16380`。

带 Bearer 的请求不走 Cookie CSRF。浏览器 Cookie 会话仍做 CSRF 校验。

## 代码布局

```
src/main/java/com/jyfc/backend/
  Application.java
  core/          安全、租户、异常
  module/        账号、组织、知识、技能、渠道、商品、交易、内容、
                 签署、合同、证据、模板、租户配额
```

签章相关调用受套餐校验。控制平面地址由配置指定，不写死在业务代码里。

## 迁移

号段大致是：账号 `V001`，组织 `V010`，知识 `V020`，技能 `V030`，渠道 `V040`，商品 `V050`，交易 `V060`，营销 `V070`，内容 `V080`，互动 `V090`，版本 `V100`，同步 `V110`，法律与租户 `V130` 及以后。

已在旧库执行过、随后又改过校验和的脚本，不能在 `validate-on-migrate=true` 的库上直接启动。那种库需要按备份重建，或先处理 `flyway_schema_history`。新库直接启动即可。
