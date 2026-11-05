# car-seller 轻量级电商微服务系统

**架构设计与开发指南（Architecture & Development Guide）**

> 本文件是整个 `car-seller` 工程的**唯一架构基线**：所有模块的端口、包名、接口路径、数据模型、消息协议、响应格式均以此为准。
> **当文档与代码不一致时，以本文档描述的目标态为准**，并在同一次提交中同步修正代码或回填本文档。
>
> | 文档版本 | 最后更新 | 适用分支 | 当前阶段 |
> |---|---|---|---|
> | v1.0 | 2026-11-05 | `master` | Phase 0 之前（工程骨架已就绪） |

---

## 目录

- [1. 项目简介](#1-项目简介)
- [2. 技术栈与版本矩阵](#2-技术栈与版本矩阵)
- [3. 系统架构总览](#3-系统架构总览)
- [4. 服务模块拆解与端口规划](#4-服务模块拆解与端口规划)
- [5. 工程目录结构](#5-工程目录结构)
- [6. 数据模型设计](#6-数据模型设计)
- [7. 核心业务流程](#7-核心业务流程)
- [8. API 设计规范](#8-api-设计规范)
- [9. 开发规范](#9-开发规范)
- [10. 安全设计](#10-安全设计)
- [11. 中间件与分布式能力](#11-中间件与分布式能力)
- [12. 本地开发与运行指南](#12-本地开发与运行指南)
- [13. 分阶段实施路线图](#13-分阶段实施路线图)
- [14. 接口自测与冒烟用例](#14-接口自测与冒烟用例)
- [15. 可观测性与运维](#15-可观测性与运维)
- [16. CI/CD 与容器化](#16-cicd-与容器化)
- [17. 常见问题 FAQ](#17-常见问题-faq)
- [18. 待办事项与文档维护](#18-待办事项与文档维护)

---

## 1. 项目简介

`car-seller` 是一套**面向汽车用品零售场景的轻量级电商微服务系统**，用于完整演示「微服务拆分 + 服务治理 + 消息驱动 + 缓存 + 分布式一致性」的工程落地方式。

### 1.1 业务范围

系统提供电商闭环的最小可用能力：

1. **商品目录**：商品详情、分页检索（关键字 / 分类）、商品维护。
2. **购物车**：按用户维度维护购物车条目（加购、改数量、删除、选中）。
3. **订单**：下单、订单状态流转、订单查询。
4. **库存**：可售库存扣减、恢复、变动流水。
5. **支付**：模拟支付、支付结果回调、支付状态通知。

### 1.2 设计目标

| 目标 | 说明 |
|---|---|
| 服务自治 | 每个服务独立进程、独立端口、独立数据库 Schema，禁止跨库直连 |
| 单一入口 | 所有外部请求统一经过 `api-gateway`，由网关完成路由、鉴权、限流 |
| 消息解耦 | 订单与库存、支付与订单之间通过 RocketMQ 异步通信，禁止强耦合同步调用链 |
| 最终一致 | 跨服务数据一致性采用「本地事务 + 消息重试 + 幂等消费」，不引入重型分布式事务 |
| 可演进 | 公共能力（响应包装、异常、JWT、MQ 常量）收敛到 `car-seller-common`，避免复制粘贴 |

### 1.3 非目标（明确不做）

- 不做真实支付渠道对接（支付服务为**模拟实现**，仅驱动状态流转）。
- 不做前端工程（本仓库只含后端服务；接口以 REST + JSON 形式提供）。
- 不做多租户、复杂营销（优惠券 / 秒杀 / 拼团）、商品 SPU-SKU 复杂模型。
- 不做分库分表的物理实施（保留设计说明，见 [6.4](#64-数据库优化约定)）。
- 不做真实生产级高可用部署（K8s 只给编排要点，见 [16.3](#163-kubernetes-编排要点)）。

### 1.4 阅读指引

| 你的角色 | 建议阅读顺序 |
|---|---|
| 新加入的开发者 | 1 → 2 → 4 → 5 → 9 → 12（跑起来） |
| 负责某个服务的开发 | 4 → 6 → 7 → 8 → 9 + 对应服务的目录 |
| 架构 / 评审 | 3 → 7 → 10 → 11 → 13 |
| 运维 / 部署 | 12 → 15 → 16 → 17 |

### 1.5 术语表

| 术语 | 含义 |
|---|---|
| 可售库存 | `inventory_db.inventories.quantity`，**唯一真源**，见 [6.3](#63-库存唯一真源约定) |
| 展示库存 | `product_db.products.stock`，商品列表/详情页展示用的冗余字段 |
| 业务状态码 | 响应体 `R.code`（200/400/401/403/404/500…），与 HTTP 状态码解耦 |
| 订单号 | `orders.id`（雪花/自增主键）同时作为 MQ 消息的业务唯一键（Key） |


## 2. 技术栈与版本矩阵

### 2.1 版本矩阵（当前实际 vs 目标）

下表「当前实际」列与根 `pom.xml` 完全一致，是**今天能编译运行的版本**；「目标版本」是本项目的**目标态**，由 [Phase 0](#phase-0工程基建加固) 完成升级。

| 组件 | 当前实际（`pom.xml`） | 目标版本 | 状态 |
|---|---|---|---|
| JDK | 17（`maven.compiler.source/target=17`） | 17 | ✅ 已达成 |
| Spring Boot | **3.0.7** | **3.1.5** | 🚧 Phase 0 升级 |
| Spring Cloud | **2022.0.0** | **2022.0.4** | 🚧 Phase 0 升级 |
| Spring Cloud Alibaba | **2022.0.0.0** | 2022.0.0.0（随 Boot 3.1.5 一并回归） | ⚙️ 保持不变，需回归 Nacos 注册/配置 |
| MyBatis-Plus | 3.5.5（`mybatis-plus-spring-boot3-starter`） | 3.5.5 | ✅ 已达成 |
| MySQL Connector/J | 8.0.33（`com.mysql:mysql-connector-j`） | 8.0.33 | ✅ 已达成 |
| RocketMQ | `rocketmq-spring-boot-starter` 2.2.3 | 2.2.3 | ✅ 已达成 |
| MySQL Server | — | 8.0.x | ⚙️ 外部依赖 |
| Redis | — | 7.x（最低 6.x） | ⚙️ 外部依赖 |
| Nacos Server | — | 2.2.x | ⚙️ 外部依赖 |
| RocketMQ Server | — | 4.9.x / 5.x | ⚙️ 外部依赖 |
| Spring Cloud Gateway | 随 Cloud 2022.0.x | 同左 | ✅ 已达成 |
| Spring Cloud LoadBalancer | 随 Cloud 2022.0.x | 同左 | ✅ 已达成 |
| Spring Validation | `spring-boot-starter-validation`（product-service 已引入） | 同左 | 🚧 待推广到全部服务 |
| OpenFeign | 未引入 | 随 Cloud 2022.0.x | 🚧 Phase 2 引入 |
| Sentinel | 未引入 | 随 Alibaba 2022.0.0.0 | 🚧 Phase 5 引入 |
| Redisson | 未引入 | 3.27.x | 🚧 Phase 3 引入 |
| jjwt | 未引入 | 0.11.5（`jjwt-api/impl/jackson`） | 🚧 Phase 1 引入 |

### 2.2 版本兼容铁律

1. **Boot 3.x ↔ Cloud 2022.0.x ↔ Alibaba 2022.0.x ↔ JDK 17** 是绑定的兼容矩阵，任何单点升级都必须整体验证，禁止只改其中一个版本号。
2. Boot 3.x 使用 **Jakarta EE 命名空间**：所有 Servlet / Validation / Persistence 注解必须来自 `jakarta.*`，**禁止出现 `javax.*`**（例如必须 `jakarta.validation.constraints.NotBlank`）。
3. `spring-boot-dependencies`、`spring-cloud-dependencies`、`spring-cloud-alibaba-dependencies` 三个 BOM **只允许在根 pom 的 `<dependencyManagement>` 中声明**，子模块不得声明版本号。
4. 子模块 `pom.xml` 中，除 BOM 未管理的第三方（如 `rocketmq-spring-boot-starter`、`mysql-connector-j`）外，**不得写死 `<version>`**。
5. `spring-boot-maven-plugin` 统一由根 pom 的 `<pluginManagement>` 管理，子模块只引用坐标。

### 2.3 Phase 0 升级执行清单

```xml
<!-- 根 pom.xml，改这三处即可 -->
<properties>
  <java.version>17</java.version>
  <spring-boot.version>3.1.5</spring-boot.version>
  <spring-cloud-alibaba.version>2022.0.0.0</spring-cloud-alibaba.version>
</properties>
<!-- spring-cloud-dependencies 的 version 由 2022.0.0 改为 2022.0.4 -->
```

验收命令：

```powershell
mvn -q -DskipTests clean install
mvn -q dependency:tree -Dincludes=org.springframework.boot:spring-boot
```


## 3. 系统架构总览

### 3.1 逻辑架构图

```text
                                   ┌──────────────────────────┐
                                   │  客户端 (Web / App / CLI) │
                                   └────────────┬─────────────┘
                                                │ HTTPS + JWT
                                                ▼
                     ┌────────────────────────────────────────────────┐
                     │           api-gateway  :9000                    │
                     │  Spring Cloud Gateway                           │
                     │  ├─ 路由（/api/** → lb://<service>）            │
                     │  ├─ JWT 鉴权（Phase 1）→ 透传 X-User-Id        │
                     │  └─ Sentinel 限流熔断（Phase 5）                │
                     └───┬────────┬────────┬────────┬────────┬────────┘
                         │        │        │        │        │
        /api/products/** │        │/api/cart/**     │/api/orders/**  │
                         ▼        ▼        ▼        ▼        ▼
              ┌──────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────┐
              │product-service│ │cart-svc  │ │order-svc │ │inventory-svc │ │payment-svc   │
              │    :8081      │ │  :8082   │ │  :8083   │ │   :8084      │ │   :8085      │
              │ MyBatis-Plus  │ │  Redis   │ │  MQ 生产 │ │  MQ 消费     │ │  MQ 生产     │
              └──────┬────────┘ └────┬─────┘ └──┬────┬──┘ └──┬────────┬──┘ └──┬───────────┘
                     │               │          │    │       │        │       │
                     ▼               ▼          ▼    ▼       ▼        ▼       ▼
              ┌────────────┐  ┌──────────┐ ┌─────────┐ ┌────────────────┐ ┌──────────┐
              │ product_db │  │  Redis   │ │order_db │ │  inventory_db  │ │(无库)    │
              └────────────┘  └──────────┘ └─────────┘ └────────────────┘ └──────────┘
                     │                        │  ▲              │  ▲
                     │                        │  │              │  │
                     │                        ▼  │              ▼  │
                     │        ┌──────────────────────────────────────┴──────────┐
                     └───────►│        RocketMQ  (NameServer :9876)              │
                              │  order-created-topic / order-paid-topic          │
                              │  inventory-deduct-result-topic                   │
                              │  order-cancelled-topic                           │
                              └──────────────────────────────────────────────────┘

        所有服务（含 api-gateway）启动时注册到 ┌──────────────────┐
                                               │ Nacos :8848      │
                                               │ 注册中心 + 配置中心│
                                               └──────────────────┘
```

### 3.2 分层说明

| 层 | 组成 | 职责 | 禁止事项 |
|---|---|---|---|
| 接入层 | `api-gateway` | 统一入口、路由、鉴权、限流、跨域 | 禁止写业务逻辑、禁止直连数据库 |
| 业务层 | product / cart / order / inventory / payment | 领域逻辑、事务、数据读写 | 禁止跨库直连、禁止反向调用网关 |
| 消息层 | RocketMQ | 异步事件、最终一致、削峰 | 禁止用 MQ 承载强一致、实时查询 |
| 基础设施层 | MySQL / Redis / Nacos | 持久化、缓存、注册与配置 | 禁止业务代码拼接裸 IP |

### 3.3 一次典型请求的完整链路

以「经网关查询商品详情」为例：

```text
客户端 GET /api/products/1
  → api-gateway:9000       匹配路由 product-service（Path=/api/products/**）
                           （Phase 1 起：校验 JWT，注入 X-User-Id）
  → lb://product-service   通过 Nacos 服务列表 + LoadBalancer 选出实例
  → product-service:8081   ProductController#getProductDetail(id=1)
                           → ProductServiceImpl#getProductDetail
                           → (Phase 5) 先查 Redis product:detail:1，未命中再查库
                           → MySQL product_db.products
  ← R<Product>             统一响应包装返回，HTTP 200
```

### 3.4 关键架构约定

1. **网关不做前缀裁剪**：`api-gateway` 的 `application.yml` 未配置 `StripPrefix`，因此**直连服务与经网关访问的路径完全一致**（都是 `/api/products/**`）。所有服务自身 `@RequestMapping` 必须带 `/api/<域>` 前缀。
2. **服务间只通过 Nacos 服务名访问**（`lb://<application-name>`），`spring.application.name` 即服务名，禁止修改。
3. **同步调用 vs 异步消息的边界**：查询类（查商品、查库存）用同步调用；状态变更类（下单→扣库存、支付→改订单）用 RocketMQ 异步消息。


## 4. 服务模块拆解与端口规划

### 4.1 模块总览（端口与完成度）

> 下表端口与包名须与各模块 `application.yml`、`XxxApplication.java` **完全一致，不得随意改动**。

| # | 模块 | 服务名（`spring.application.name`） | 端口 | 包名 | 主类 | 数据存储 | 关键依赖 | 完成度 |
|---|---|---|---|---|---|---|---|---|
| 0 | （外部）Nacos Server | — | 8848 | — | — | — | 注册中心 + 配置中心 | ⚙️ 外部启动 |
| 1 | `api-gateway` | `api-gateway` | **9000** | `com.carseller.gateway` | `ApiGatewayApplication` | 无 | Gateway、Nacos Discovery、LoadBalancer | ✅ 路由 ／ 🚧 鉴权·限流 |
| 2 | `product-service` | `product-service` | **8081** | `com.carseller.product` | `ProductServiceApplication` | MySQL `product_db` | Web、Nacos、MyBatis-Plus、Validation | ✅ CRUD 全量实现 |
| 3 | `cart-service` | `cart-service` | **8082** | `com.carseller.cart` | `CartServiceApplication` | Redis（`cart_db` 可选） | Web、Nacos、Spring Data Redis、OpenFeign、Validation | ✅ 购物车全量实现 |
| 4 | `order-service` | `order-service` | **8083** | `com.carseller.order` | `OrderServiceApplication` | MySQL `order_db` | Web、Nacos、MyBatis-Plus、OpenFeign、Validation、RocketMQ Producer | ✅ 下单／详情／分页／取消 ／ 🚧 消费端·超时关单 |
| 5 | `inventory-service` | `inventory-service` | **8084** | `com.carseller.inventory` | `InventoryServiceApplication` | MySQL `inventory_db` | Web、Nacos、JPA、RocketMQ Consumer | 🚧 仅骨架 + MQ 配置 |
| 6 | `payment-service` | `payment-service` | **8085** | `com.carseller.payment` | `PaymentServiceApplication` | 无（模拟） | Web、Nacos、RocketMQ Producer | 🚧 仅骨架 + MQ 配置 |
| 7 | `auth-service` | `auth-service` | **8086** | `com.carseller.auth` | `AuthApplication` | MySQL `auth_db` | Web、Nacos、MyBatis-Plus、Validation、spring-security-crypto、jjwt | ✅ 注册/登录/用户信息 |

### 4.2 模块职责详述

#### 4.2.1 api-gateway（`:9000`）

- **唯一入口**：外部流量只能访问 9000，业务服务端口不对外暴露。
- **路由**：6 条路由已配置，全部使用 `lb://<服务名>`（见 [8.2](#82-网关路由表)）。
- **鉴权（Phase 1）**：校验 `Authorization: Bearer <JWT>`，解析后将 `X-User-Id`、`X-User-Name` 写入下游请求头；白名单路径直接放行。
- **限流（Phase 5）**：接入 Sentinel 做接口级 QPS 限流与熔断降级。
- **禁止**：不得引入 `spring-boot-starter-web`（与 WebFlux 冲突）、不得连接数据库。

#### 4.2.2 product-service（`:8081`）

- **职责**：商品的增删改查、分页检索（关键字模糊 + 分类精确）、商品详情缓存（Phase 5）。
- **对外端点**：`/api/products/**`（详见 [8.6](#86-各服务-rest-端点清单)）。
- **数据**：`product_db.products`，ORM 使用 MyBatis-Plus（`BaseMapper` + `LambdaQueryWrapper`）。
- **本服务已作为其他服务的分层范式**：`controller → service(接口) → service.impl → mapper → entity`，`dto` 负责入参，`common` 负责统一响应与异常。

#### 4.2.3 cart-service（`:8082`）

- **职责**：按 `userId` 维护购物车（加购、修改数量、删除、选中/取消选中、清空、查询）。
- **数据**：**以 Redis 为准**，不设强依赖数据库：
  - `cart:{userId}` —— Hash，field=`productId`，value=数量；
  - `cart:selected:{userId}` —— Set，member=已选中的 `productId`；
  - TTL 7 天且每次写入续期；购物车为空时回收选中集合 Key。

  `sql/cart_db.sql` 仅为可选的持久化备份/审计用途，**Phase 2 不接入数据源**（模块内无 JDBC / MyBatis 依赖）。
- **依赖**：通过 OpenFeign（`ProductClient`）调用 `product-service` 的 `GET /api/products/{id}` 校验商品存在性并取最新价格；
  购物车**不保存价格快照**，查询时实时补全；商品已删除或商品服务不可用时该条目降级为 `available=false`，不影响整体查询。
- **业务限制**：单商品数量 ≤ 99（`30002`）、条目（商品种类）≤ 50（`30003`）、条目不存在 `30001`；`X-User-Id` 缺失一律按 401 处理。

#### 4.2.4 order-service（`:8083`）

- **职责**：下单（写订单主表 + 明细表）、订单查询、订单状态机流转、下单成功后发送 `order-created-topic` 消息、订阅 `order-paid-topic` / `inventory-deduct-result-topic` 更新状态。
- **对外端点**：`/api/orders/**`（详见 [8.6](#86-各服务-rest-端点清单)）：`POST /api/orders`、`GET /api/orders/{id}`、`GET /api/orders`、`POST /api/orders/{id}/cancel`。
- **数据**：`order_db.orders`、`order_db.order_items`，ORM 使用 MyBatis-Plus（`BaseMapper` + `LambdaQueryWrapper` + 分页插件）；明细表保存 `productName`、`price` 下单快照。
- **依赖**：通过 OpenFeign（`ProductClient`）调用 `product-service` 的 `GET /api/products/{id}` 取商品名称与最新价格，**事务内禁止远程调用**。
- **一致性**：写操作拆为 `OrderServiceImpl`（编排 + 远程查询 + 发消息）与 `OrderTxService`（纯本地事务：主表 + 明细一次提交）；事务提交后再发 MQ，发送失败按指数退避重试 3 次，消费端以 `orderId` 幂等兜底（本阶段**不使用** RocketMQ 事务消息）。
- **状态机**：状态变更一律幂等条件更新（如 `UPDATE orders SET status='CANCELLED' WHERE id=? AND user_id=? AND status='PENDING'`），影响 0 行即 ACK，不抛异常重试。
- **MQ**：`rocketmq.producer.group=order-producer-group`，发送 `order-created-topic`（tag `ORDER_CREATED`）与 `order-cancelled-topic`（tag `ORDER_CANCELLED`），消息 Key 统一为 `orderId`。

#### 4.2.5 inventory-service（`:8084`）

- **职责**：可售库存扣减（幂等）、库存恢复、库存变动流水、库存查询。
- **数据**：`inventory_db.inventories`（唯一真源）、`inventory_db.inventory_logs`（变动流水）。
- **MQ**：`rocketmq.consumer.group=inventory-consumer-group`（已在 yml 配置），订阅 `order-created-topic`。
- **并发控制**：Redis 分布式锁 `lock:inventory:{productId}`（Phase 3 引入 Redisson）+ 数据库乐观/条件更新 `UPDATE ... SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?`。

#### 4.2.6 payment-service（`:8085`）

- **职责**：接收支付请求（模拟）、生成支付流水号、异步「支付成功」结果、发送 `order-paid-topic` 消息。
- **数据**：**当前无数据库**；Phase 4 视需要新增 `payment_db.payments`（见 [6.5](#65-待新增数据表规划)）。
- **MQ**：`rocketmq.producer.group=payment-producer-group`（已在 yml 配置）。

#### 4.2.7 auth-service（`:8086`）

- **职责**：用户注册、账号密码登录并颁发 JWT、当前用户信息查询与修改；登录行为留痕审计。
- **对外端点**：`/api/auth/**`（详见 [8.6](#86-各服务-rest-端点清单)），其中 `/api/auth/login`、`/api/auth/register` 在网关白名单内免鉴权。
- **数据**：`auth_db.users`（唯一账号来源）、`auth_db.user_login_logs`（登录审计），ORM 使用 MyBatis-Plus。
- **密码**：只存 BCrypt（cost=10）哈希，校验用 `PasswordEncoder.matches`，明文与哈希均禁止写日志。
- **JWT**：签发复用 `car-seller-common` 的 `JwtUtils`（`jwt.secret` 必须与 api-gateway 完全一致，环境变量 `JWT_SECRET` 覆盖）；**本服务不复制 JWT 工具类、不做 Token 校验**，校验统一在网关完成。
- **安全**：登录失败统一返回「用户名或密码错误」（防账号枚举）；`/api/auth/me` 的用户身份只取网关透传的 `X-User-Id`，禁止从请求体取 `userId`（防越权）。

### 4.3 端口分配规则

| 区段 | 用途 | 约定 |
|---|---|---|
| 9000 | 网关 | 固定，不参与自动分配 |
| 8081 ~ 8089 | 业务服务 | 按上表静态分配，新增服务从 8087 起顺延 |
| 8848 | Nacos | 外部中间件，不计入业务端口 |
| 9876 | RocketMQ NameServer | 外部中间件 |
| 6379 / 3306 | Redis / MySQL | 外部中间件 |

### 4.4 服务启动依赖顺序

```text
Nacos:8848 → MySQL:3306 / Redis:6379 → RocketMQ(NameServer:9876 + Broker:10911) → 6 个业务服务 → api-gateway:9000
```

业务服务之间**无启动顺序要求**（互不阻塞，通过 Nacos + MQ 松耦合）；网关可随时启动，下游未就绪时请求返回 503。


## 5. 工程目录结构

### 5.1 仓库根结构（现状）

```text
car-seller/
├── pom.xml                     # 聚合父工程：com.carseller:car-seller:1.0-SNAPSHOT（packaging=pom）
├── README.md                   # 本文档（架构基线）
├── sql/                        # 数据库初始化脚本
│   ├── product_db.sql          # products 表 + 10 条测试数据
│   ├── cart_db.sql             # carts 表（Redis 方案的备用持久化）
│   ├── order_db.sql            # orders / order_items + 5 条测试数据
│   ├── inventory_db.sql        # inventories / inventory_logs + 10 条测试数据
│   └── auth_db.sql             # users / user_login_logs + 3 条测试账号
├── logs/                       # 本地运行日志输出目录
├── api-gateway/                # 网关 :9000
├── car-seller-common/          # 公共 jar 模块（无端口、无启动类）：R / ResultCode / PageResult / 异常
├── product-service/            # 商品 :8081（唯一已完整实现的业务服务）
├── cart-service/               # 购物车 :8082
├── order-service/              # 订单 :8083
├── inventory-service/          # 库存 :8084
├── payment-service/            # 支付 :8085
└── auth-service/               # 认证 :8086（注册 / 登录 / 用户信息）
```

### 5.2 单个服务的标准分层结构

以 `product-service` 为标杆（其他服务按同一结构补齐）：

```text
product-service/
├── pom.xml
└── src/main/
    ├── java/com/carseller/product/
    │   ├── ProductServiceApplication.java      # 启动类（@SpringBootApplication + @ComponentScan 覆盖 common）
    │   ├── controller/                         # 只做参数接收 + 调用 service + 包装 R
    │   │   └── ProductController.java
    │   ├── service/                            # 业务接口
    │   │   ├── ProductService.java
    │   │   └── impl/ProductServiceImpl.java    # 业务实现 + @Transactional
    │   ├── mapper/                             # MyBatis-Plus BaseMapper
    │   │   └── ProductMapper.java
    │   ├── entity/                             # 数据库实体（@TableName）
    │   │   └── Product.java
    │   ├── dto/                                # 入参对象（含 Validation 注解）
    │   │   ├── ProductRequest.java
    │   │   └── ProductQuery.java
    │   └── config/                             # 配置类
    │       └── MybatisPlusConfig.java
    └── resources/
        ├── application.yml                     # 端口、Nacos、数据源、MyBatis-Plus、日志级别
        └── mapper/                             # （可选）自定义 SQL XML，mapper-locations 已配置
```

> **Phase 0 已完成**：原 `product-service/.../product/common/`（`R`、`ResultCode`、`PageResult`、`BusinessException`、`GlobalExceptionHandler`）已全部迁移到 `car-seller-common`，业务模块内**不再保留** `common/` 包。

### 5.2.1 公共模块 `car-seller-common` 结构（已落地）

```text
car-seller-common/
├── pom.xml                                     # packaging=jar，无 spring-boot-maven-plugin（不被 repackage）
└── src/main/java/com/carseller/common/
    ├── result/
    │   ├── R.java                              # 统一响应 {code,message,data,timestamp}
    │   ├── ResultCode.java                     # 统一状态码（基础段 + 域扩展段）
    │   └── PageResult.java                     # 分页结果 {records,total,pageNum,pageSize,pages}
    └── exception/
        ├── BusinessException.java              # 业务异常（code + 中文 message）
        └── GlobalExceptionHandler.java         # @RestControllerAdvice 统一转 R
```

依赖约定：`spring-boot-starter-web`、`spring-boot-starter-validation` 在 common 中为 `provided`（仅编译需要，不向下传递），业务服务自行引入 web 能力；业务服务启动类需显式 `@ComponentScan({"com.carseller.<包名>", "com.carseller.common"})`。

### 5.3 分层职责与依赖方向

```text
Controller  →  Service(接口)  →  ServiceImpl  →  Mapper/Repository  →  DB
     │              │                  │
     └── DTO ───────┘            Entity / 领域对象
```

| 层 | 允许做 | 禁止做 |
|---|---|---|
| `controller` | 参数绑定、`@Valid` 校验、调用 service、`R.ok(...)` 包装、打请求日志 | 写业务判断、直接操作 Mapper、`try-catch` 吞异常 |
| `service` | 业务规则、事务边界、事务内调用多个 mapper | 出现 `HttpServletRequest`、`ResponseEntity` |
| `mapper` | 单表 CRUD、自定义 SQL | 返回 DTO、写业务逻辑 |
| `entity` | 与表结构一一对应 | 加业务行为方法、加 `@JsonIgnore` 之外的展示逻辑 |
| `dto` | 入参/出参结构 + Validation 注解 | 直接被持久化 |

### 5.4 包名规划（全模块统一）

```text
com.carseller.gateway     api-gateway
com.carseller.product     product-service
com.carseller.cart        cart-service
com.carseller.order       order-service
com.carseller.inventory   inventory-service
com.carseller.payment     payment-service
com.carseller.auth        auth-service
com.carseller.common      car-seller-common（Phase 0 新增）
```

> `car-seller-common` 的包名固定为 `com.carseller.common`，内部按 `result / exception / security / mq / util` 分子包，供其余 6 个模块依赖。

### 5.5 命名规范速查

| 对象 | 规范 | 正例 | 反例 |
|---|---|---|---|
| 包 | 全小写，单数 | `com.carseller.order.controller` | `com.carseller.Orders.Controllers` |
| 类 | 大驼峰 | `OrderServiceImpl` | `orderServiceImpl` |
| 接口实现类 | `XxxServiceImpl` | `ProductServiceImpl` | `ProductImpl` |
| DTO | `XxxRequest` / `XxxQuery` / `XxxResponse` | `ProductRequest` | `ProductDTO1` |
| 表名 | 小写下划线，复数 | `order_items` | `OrderItem` |
| 字段 | 列名下划线，Java 属性驼峰 | `image_url` ↔ `imageUrl` | `imageURL` |
| 常量 | 全大写下划线 | `MAX_PAGE_SIZE` | `maxPageSize` |
| Redis Key | `域:对象:标识` | `cart:1001`、`product:detail:1` | `cartKey1001` |
| MQ Topic | `域-事件-topic` | `order-created-topic` | `orderTopic` |


## 6. 数据模型设计

### 6.1 数据库划分（Database per Service）

| 数据库 | 归属服务 | 初始化脚本 | 说明 |
|---|---|---|---|
| `product_db` | product-service | `sql/product_db.sql` | 商品数据 |
| `order_db` | order-service | `sql/order_db.sql` | 订单主表 + 明细 |
| `inventory_db` | inventory-service | `sql/inventory_db.sql` | 可售库存 + 变动流水 |
| `cart_db` | cart-service | `sql/cart_db.sql` | **可选**，Redis 方案的备份 |
| `auth_db` | auth-service | `sql/auth_db.sql` | 用户账号 + 登录审计 |
| `payment_db` | payment-service | 暂无 | Phase 4 视需要新增 |

**铁律**：服务只能访问自己的库，**禁止跨库 JOIN、禁止跨库直连**；需要其他域的数据时，通过 OpenFeign 调用对方接口或消费对方消息。

所有库统一：`utf8mb4` / `utf8mb4_unicode_ci` / `InnoDB`。

### 6.2 现有数据表

#### products（product_db）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 商品 ID |
| `name` | VARCHAR(255) | NOT NULL | 商品名称 |
| `description` | TEXT | 可空 | 商品描述 |
| `price` | DECIMAL(10,2) | NOT NULL DEFAULT 0.00 | 价格 |
| `stock` | INT | NOT NULL DEFAULT 0 | **展示用**库存，见 [6.3](#63-库存唯一真源约定) |
| `category` | VARCHAR(100) | 可空 | 分类 |
| `image_url` | VARCHAR(500) | 可空 | 图片地址 |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间（数据库维护） |
| `updated_at` | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | 更新时间（数据库维护） |

索引：`idx_name(name)`、`idx_category(category)`。

#### carts（cart_db，可选）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 主键 |
| `user_id` | BIGINT | NOT NULL | 用户 ID |
| `product_id` | BIGINT | NOT NULL | 商品 ID |
| `quantity` | INT | NOT NULL DEFAULT 1 | 数量 |
| `selected` | BOOLEAN | DEFAULT TRUE | 是否选中 |
| `created_at` / `updated_at` | TIMESTAMP | 自动维护 | 时间戳 |

索引：唯一键 `uk_user_product(user_id, product_id)`、`idx_user_id`。

#### orders / order_items（order_db）

`orders`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 订单号（同时作为 MQ Key） |
| `user_id` | BIGINT NOT NULL | 下单用户 |
| `total_price` | DECIMAL(10,2) NOT NULL | 订单总额 |
| `status` | VARCHAR(50) NOT NULL DEFAULT 'PENDING' | 订单状态，见 [7.2.1](#721-订单状态机) |
| `shipping_address` | VARCHAR(500) | 收货地址 |
| `contact_phone` | VARCHAR(20) | 联系电话 |
| `payment_method` | VARCHAR(50) | `ALIPAY` / `WECHAT` |
| `created_at` / `updated_at` | TIMESTAMP | 时间戳 |

索引：`idx_user_id`、`idx_status`、`idx_created_at`。

`order_items`：`id`、`order_id`、`product_id`、`product_name`（下单快照）、`price`（下单快照）、`quantity`；
索引 `idx_order_id`、`idx_product_id`；外键 `order_id → orders(id) ON DELETE CASCADE`（**仅在同一个库内允许使用外键**）。

#### inventories / inventory_logs（inventory_db）

`inventories`：`id` PK、`product_id` BIGINT **UNIQUE**、`quantity` INT NOT NULL DEFAULT 0、`updated_at`；索引 `idx_product_id`。

`inventory_logs`：`id`、`product_id`、`change_quantity`、`change_type`（`INCREASE` / `DECREASE`）、`remark`、`created_at`；索引 `idx_product_id`。

#### users / user_login_logs（auth_db）

`users`：`id` PK、`username` VARCHAR(64) **UNIQUE**、`password` VARCHAR(100) **BCrypt 哈希**、`nickname`、`phone` VARCHAR(20) **UNIQUE**、`email` VARCHAR(128) **UNIQUE**、`avatar_url`、`role`（`USER` / `ADMIN`）、`status`（1 启用 / 0 禁用）、`last_login_at`、`last_login_ip`、`password_updated_at`、`created_at`、`updated_at`、`deleted`（逻辑删除 0/1）；索引 `uk_username` / `uk_phone` / `uk_email` / `idx_status` / `idx_created_at`。

`user_login_logs`：`id`、`user_id`（账号不存在时为 `NULL`）、`username`、`login_ip`、`user_agent`、`login_result`（1 成功 / 0 失败）、`fail_reason`、`created_at`；索引 `idx_user_id` / `idx_username` / `idx_created_at`。

> `auth_db` 是**账号唯一来源**：其他服务需要用户资料时只能经网关调用 `auth-service`，禁止跨库读取 `users`。
> 初始账号（`sql/auth_db.sql`，与 cart/order 演示数据的 `user_id` 对齐）：`admin`/`admin123`、`zhangsan`/`user123456`、`lisi`/`user123456`，**上线前必须重置或删除**。

### 6.3 库存唯一真源约定

> **这是本项目最容易出错的地方，必须严格遵守。**

| 字段 | 角色 | 写方 | 一致性策略 |
|---|---|---|---|
| `inventory_db.inventories.quantity` | **可售库存唯一真源** | inventory-service 独占写入 | 扣减/恢复的**唯一依据**，任何"能不能下单"的判断只看它 |
| `product_db.products.stock` | **展示用冗余** | product-service 单向写入 | 由 inventory-service 通过 MQ 事件反向同步（Phase 3），允许短暂不一致 |

规则：

1. **禁止** product-service 直接改写 `products.stock` 来"扣库存"——它不参与扣减逻辑。
2. 商品列表/详情页展示 `products.stock`；下单前的库存校验由 order-service 调用 `inventory-service` 的 `/api/inventory/{productId}` 完成。
3. 冗余字段的同步是**单向且幂等**的：inventory → product。
4. 若两者出现长期漂移，以 `inventories` 为准进行对账修复（Phase 6 可加对账任务）。

### 6.4 数据库优化约定

1. 所有查询列必须有对应索引；分页排序字段（如 `id DESC`）需有索引支撑。
2. 禁止 `SELECT *`（MyBatis-Plus 默认已按实体字段列举）。
3. 单表数据量预计超过 1000 万时才考虑分库分表（按 `user_id` 哈希分片是首选方案），**当前阶段不实施**。
4. `DECIMAL` 存金额，禁止 `FLOAT/DOUBLE`；Java 侧对应 `BigDecimal`。
5. 时间字段统一 `TIMESTAMP`，应用侧 `LocalDateTime`，对外 JSON 格式 `yyyy-MM-dd HH:mm:ss`（见 `Product#createdAt` 的 `@JsonFormat`）。
6. 禁止在业务代码里写死库名/表名拼接；表名只在实体 `@TableName` 出现。

### 6.5 待新增数据表规划

| 库 | 表 | 用途 | 阶段 |
|---|---|---|---|
| `order_db` | `order_message_outbox` | 本地消息表（保证"订单落库"与"消息发送"最终一致），✅ 已落地到 `sql/order_db.sql` | Phase 3 |
| `payment_db` | `payments` | 支付流水（`id`、`order_id`、`amount`、`status`、`trade_no`、`created_at`） | Phase 4 |
| `inventory_db` | `inventory_deduct_records` | 扣减幂等表（`order_id` + `product_id` 唯一） | Phase 3 |

### 6.6 初始化脚本使用说明

```powershell
# 依次执行（顺序无强制要求，但建议按此顺序便于核对）
mysql -uroot -p < sql/product_db.sql
mysql -uroot -p < sql/order_db.sql
mysql -uroot -p < sql/inventory_db.sql
mysql -uroot -p < sql/cart_db.sql     # 可选的 Redis 备份表
```

> 脚本内含中文注释；若在 UTF-8 终端下显示为乱码，请用 IDEA / Navicat / DBeaver 打开执行，不影响 SQL 本身正确性。


## 7. 核心业务流程

### 7.1 主流程一：订单创建 → 异步扣减库存

**目标**：下单接口只做本地事务并快速返回，库存扣减异步化，避免下单链路被库存服务阻塞。

```text
客户端          api-gateway        order-service(:8083)        inventory-service(:8084)   RocketMQ
  │               :9000                    │                            │                  :9876
  │─POST /api/orders─►│──JWT/X-User-Id──►│                            │                    │
  │ {items,address}   │                  │─① OpenFeign 查商品价/名──► product-service:8081 │
  │                   │                  │◄──────── product / 404 ────────────────────────│
  │                   │                  │─② OpenFeign 校验可售库存(可选)──►│              │
  │                   │                  │◄──────── quantity ──────────────│              │
  │                   │                  │══③ 本地事务 BEGIN ═════════════════════════════════
  │                   │                  │  INSERT orders(status='PENDING', total_price)     │
  │                   │                  │  INSERT order_items(price/name 快照)              │
  │                   │                  │  INSERT order_message_outbox(待发消息)            │
  │                   │                  │══ 本地事务 COMMIT ═════════════════════════════════
  │                   │                  │─④ 发送 order-created-topic(key=orderId)──────────►
  │◄─R{orderId,status}│◄─────────────────│  （HTTP 200 立即返回，不等待库存结果）            │
  │                   │                  │                                   ┌──⑤ 消费消息───┤
  │                   │                  │                                   ▼               │
  │                   │                  │                    幂等校验（消息 Key / 扣减记录表）
  │                   │                  │                    加锁 lock:inventory:{productId}  │
  │                   │                  │                    UPDATE inventories              │
  │                   │                  │                      SET quantity=quantity-?      │
  │                   │                  │                      WHERE product_id=?           │
  │                   │                  │                        AND quantity>=?            │
  │                   │                  │                    INSERT inventory_logs          │
  │                   │                  │                                   │               │
  │                   │                  │◄──⑥ inventory-deduct-result-topic ─┘              │
  │                   │                  │     tag=DEDUCT_SUCCESS / DEDUCT_FAILED             │
  │                   │                  │─⑦ DEDUCT_SUCCESS → 订单保持 PENDING（待支付）      │
  │                   │                  │   DEDUCT_FAILED  → 订单置 CANCELLED(库存不足)      │
```

**关键点**

1. 下单接口只做本地事务，返回 `R<{orderId, totalPrice, status}>`；库存结果通过 `GET /api/orders/{id}` 查询。
2. 即使第 ② 步同步校验过库存，第 ⑤ 步仍可能因并发失败，因此**条件更新 `WHERE quantity >= ?` 是防超卖的最终防线**，分布式锁只是降低冲突率。
3. 超时未支付（默认 **30 分钟**）：order-service 定时任务扫描 `status='PENDING' AND created_at < now()-30m`，置 `CANCELLED` 并发送 `order-cancelled-topic`，由 inventory-service 回补库存。
4. `order_message_outbox`（本地消息表）用于消除「订单已落库但消息发送失败」的窗口；Phase 3 落地前退化为「事务提交后立即发送 + 失败重试 3 次 + 告警」。
5. 全链路以 `orderId` 作为业务幂等键：库存扣减记录、消息 Key、订单状态更新都以它为准。

### 7.2 主流程二：支付 → 订单状态更新

#### 7.2.1 订单状态机

```text
    [下单成功]
        │
        ▼
    PENDING ──── 支付成功（order-paid-topic）────► PAID ────► SHIPPED ────► DELIVERED
        │                                            │
        │ 库存扣减失败 / 超时未支付 / 用户取消          │ 退款（Phase 6 预留，本期不实现）
        ▼                                            ▼
    CANCELLED ◄────────────────────────────── REFUNDING
```

| 状态 | 含义 | 允许的后续动作 | 变更方 |
|---|---|---|---|
| `PENDING` | 待支付（库存已异步扣减或正在扣减） | 支付、取消、超时关闭 | order-service |
| `PAID` | 已支付 | 发货 | order-service（消费 `order-paid-topic`） |
| `SHIPPED` | 已发货 | 确认收货 | order-service（管理端） |
| `DELIVERED` | 已完成（终态） | 无 | order-service |
| `CANCELLED` | 已取消（终态） | 无 | order-service |

**流转铁律**

1. 状态**只能单向流转**，禁止回退（`PAID → PENDING` 不允许）。
2. 每次流转必须写成**幂等条件更新**：`UPDATE orders SET status='PAID' WHERE id=? AND status='PENDING'`。
   影响行数 = 0 表示已处理过或状态非法 → **直接 ACK 消息，不抛异常**（否则会无限重试）。
3. 状态值以数据库 `VARCHAR(50)` 存储，Java 侧使用枚举 `OrderStatus`，禁止魔法字符串。

#### 7.2.2 支付 → 订单状态更新时序

```text
客户端        api-gateway     payment-service(:8085)        RocketMQ(:9876)      order-service(:8083)
  │             :9000                │                            │                    │
  │─POST /api/payments──►│───┬──►│                                │                    │
  │ {orderId, method}    │   │    │─① OpenFeign 查订单 status ──────────────────────►│
  │                      │   │    │◄── status=PENDING（否则 400，拒绝支付）───────────│
  │                      │   │    │─② 生成 payNo/tradeNo，模拟渠道支付成功            │
  │                      │   │    │   （Phase 4：写 payment_db.payments 流水）        │
  │                      │   │    │─③ 发送 order-paid-topic ───────────────────────►│
  │                      │   │    │   key=orderId, tag=ORDER_PAID                    │
  │◄─R{payNo,status}─────│◄──┴────│                                │                    │
  │                      │        │                                │  ┌──④ 消费消息────┤
  │                      │        │                                │  ▼                 │
  │                      │        │                    幂等条件更新 status: PENDING→PAID
  │                      │        │                    记录 payment_method / pay_time
```

**关键点**

1. 支付服务**不修改订单库**，只发消息；订单状态由 order-service **独占**修改（单一写方）。
2. 支付前置校验：`orderId` 存在且 `status == PENDING`，否则返回 `400`（`40002 订单状态不允许该操作` / `60001 订单已支付`）。
3. 真实渠道回调必须幂等：同一 `tradeNo` 重复回调只发送一次 `order-paid-topic`（用 `payNo` 做幂等键）。
4. 若订单已被超时取消（支付先到 vs 超时先到的竞争），order-service 条件更新失败 → 记录退款流水并 **ACK**，进入补偿流程，绝不抛异常重试。
5. 支付成功后 order-service 需要更新冗余信息时，通过消息或 Feign 通知，不得直连 `payment_db`。

### 7.3 Topic / Tag / ConsumerGroup 定义

**命名规则**：`Topic = <域>-<事件>-topic`；`Tag = <事件大写>`；`Key = orderId`（保证同一订单的消息落到同一队列，便于顺序与幂等）。

| Topic | Producer（group） | Consumer（group） | Tag | 触发时机 | 状态 |
|---|---|---|---|---|---|
| `order-created-topic` | order-service（`order-producer-group`） | inventory-service（`inventory-consumer-group`） | `ORDER_CREATED` | 订单落库成功 | ✅ 两端 group 已配置 |
| `inventory-deduct-result-topic` | inventory-service（`inventory-producer-group`） | order-service（`order-deduct-result-group`） | `DEDUCT_SUCCESS` / `DEDUCT_FAILED` | 库存扣减完成 | ✅ 生产端已实现（`InventoryEventPublisher`），消费端待 Phase 3 收尾 |
| `order-paid-topic` | payment-service（`payment-producer-group`） | order-service（`order-paid-consumer-group`） | `ORDER_PAID` | 支付成功 | ✅ producer 已配置 |
| `order-cancelled-topic` | order-service（`order-producer-group`） | inventory-service（`inventory-restore-group`） | `ORDER_CANCELLED` | 超时/用户取消 | ✅ 两端已实现（`OrderEventPublisher` / `OrderCancelledConsumer`） |

**已有配置（各模块 `application.yml`，不要改动 group 名）**

```yaml
# order-service
rocketmq:
  name-server: localhost:9876
  producer:
    group: order-producer-group

# inventory-service
rocketmq:
  name-server: localhost:9876
  consumer:
    group: inventory-consumer-group

# payment-service
rocketmq:
  name-server: localhost:9876
  producer:
    group: payment-producer-group
```

**消息体：`order-created-topic`**

```json
{
  "orderId": 1001,
  "userId": 1,
  "totalPrice": 7999.00,
  "items": [
    { "productId": 1, "quantity": 1 }
  ],
  "createdAt": 1762308000000
}
```

**消息体：`inventory-deduct-result-topic`**

```json
{
  "orderId": 1001,
  "success": false,
  "reason": "库存不足: productId=1",
  "items": [
    { "productId": 1, "required": 1, "before": 0, "after": 0 }
  ],
  "finishedAt": 1762308005000
}
```

**消息体：`order-paid-topic`**

```json
{
  "orderId": 1001,
  "payNo": "PAY20261105000001",
  "tradeNo": "MOCK_TRADE_88237612",
  "amount": 7999.00,
  "paymentMethod": "ALIPAY",
  "paidAt": 1762308010000
}
```

**约定**

1. 消息体统一用 `JSON`（`RocketMQMessageConverter` 默认 Jackson），字段名与上表一致，**只增不改**。
2. 金额用 `BigDecimal`（JSON number，两位小数），时间用毫秒时间戳（`Long`）。
3. 发送必须显式指定 `topic`、`tag`、`key`：`rocketMQTemplate.syncSend(topic + ":" + tag, MessageBuilder.withPayload(event).setHeader(RocketMQHeaders.KEYS, orderId).build())`。
4. 消费端使用 `@RocketMQMessageListener(topic = ..., selectorExpression = ..., consumerGroup = ...)`，**`consumerGroup` 必须与 yml 中配置的一致**。

### 7.4 消息可靠性设计

| 环节 | 风险 | 对策 |
|---|---|---|
| 生产端 | 订单已提交、消息未发出 | 事务提交后发送 + 发送失败重试 3 次（指数退避）+ `order_message_outbox` 定时补偿扫描 |
| 生产端 | 消息重复发送 | 消费端幂等（见下），重复无副作用 |
| 消费端 | 重复消费 | ① Redis `SETNX` 幂等键 TTL 24h（inventory-service 用业务键 `inventory:deduct:{orderId}` / `inventory:restore:{orderId}`，比 `msgId` 更稳，重投时 `msgId` 会变）；② 业务唯一键落库（`inventory_deduct_records(order_id, product_id)` UNIQUE）；两者**都要有**，Redis 不能当唯一防线 |
| 消费端 | 消费失败 | 抛出异常让 RocketMQ 重试（默认 16 次，间隔递增）；不吞异常、不 `return` 假装成功 |
| 消费端 | 重试耗尽 | 进入死信队列 `%DLQ%<consumerGroup>`，告警 + 人工/脚本补偿，**必须有看板** |
| 顺序性 | 同一订单消息乱序 | 发送时 `key=orderId`（同一队列），消费模式用 `ConsumeMode.ORDERLY`（该 Topic 消费者并发度受队列数限制，可按需开启） |
| 积压 | 消费能力不足 | 监控 `Diff Total` 堆积量；扩容消费者实例（增加队列数需重建 Topic） |
| 业务补偿 | 消息彻底丢失 | 定时对账：`PENDING` 超 5 分钟且无扣减记录 → 重发 `order-created-topic`；`PAID` 但库存未扣 → 人工介入 |

**幂等消费模板**

```java
@RocketMQMessageListener(
        topic = TopicConstants.ORDER_CREATED_TOPIC,
        selectorExpression = TagConstants.ORDER_CREATED,
        consumerGroup = "${rocketmq.consumer.group}")
public class OrderCreatedListener implements RocketMQListener<OrderCreatedEvent> {

    @Override
    public void onMessage(OrderCreatedEvent event) {
        log.info("收到订单创建事件, orderId={}", event.getOrderId());
        inventoryService.deductForOrder(event);   // 内部做幂等 + 条件更新
    }
}
```

**禁止事项**

1. 禁止在消费逻辑里 `try { ... } catch (Exception e) { log.error(...); }` 后正常返回——这会让消息被 ACK，失败即永久丢失。
2. 禁止在消费端做长时间阻塞（如同步等待其他 MQ 回复），单条消息处理应 < 1 秒。
3. 禁止消费端反向同步调用「消息的生产方」形成环（order → inventory → order 的同步回调），需要结果时用新消息回传。
4. 禁止用 MQ 做实时查询（如"下单后立刻查库存"），查询走 Feign 同步接口。





## 8. API 设计规范

### 8.1 RESTful 约定

| 规则 | 说明 | 示例 |
|---|---|---|
| 路径前缀 | `/api/<资源复数>`，与网关路由一一对应 | `/api/products`、`/api/orders` |
| 资源标识 | 路径变量用主键 ID | `/api/products/{id}` |
| 动作映射 | 查询 GET、新增 POST、全量/部分更新 PUT、删除 DELETE | `POST /api/orders` |
| 状态变更 | 用**子资源 + 动作**表达，不用动词路径 | `POST /api/orders/{id}/cancel`、`POST /api/payments` |
| 分页 | 查询串 `pageNum`（从 1 开始）+ `pageSize`（默认 10，上限 100） | `?pageNum=1&pageSize=10` |
| 筛选 | 查询串直接使用字段名 | `?keyword=iPhone&category=手机` |
| 用户身份 | **不从请求体/参数取 `userId`**，只从网关透传的 `X-User-Id` 取 | 见 [10.2](#102-网关鉴权与用户上下文) |
| 版本 | 当前不做 URL 版本号；如未来演进使用请求头 `X-Api-Version` | — |

### 8.2 网关路由表

以下与 `api-gateway/src/main/resources/application.yml` **完全一致**（无 `StripPrefix`，前缀原样透传）：

| 路由 ID | 断言（Path） | 目标 | 鉴权（Phase 1 后） | 直连地址 |
|---|---|---|---|---|
| `product-service` | `/api/products/**` | `lb://product-service` | GET 放行；写操作需登录 | `http://localhost:8081` |
| `cart-service` | `/api/cart/**` | `lb://cart-service` | 必须登录 | `http://localhost:8082` |
| `order-service` | `/api/orders/**` | `lb://order-service` | 必须登录 | `http://localhost:8083` |
| `inventory-service` | `/api/inventory/**` | `lb://inventory-service` | 查询放行；写操作需登录 | `http://localhost:8084` |
| `payment-service` | `/api/payments/**` | `lb://payment-service` | 必须登录 | `http://localhost:8085` |
| `auth-service` | `/api/auth/**` | `lb://auth-service` | `/api/auth/login`、`/api/auth/register` 白名单放行；其余必须登录 | `http://localhost:8086` |

新增服务时须同时修改：`api-gateway/application.yml` 的 `routes`、本节表格、[4.1](#41-模块总览端口与完成度) 端口表。

### 8.3 统一响应格式 `R<T>`

所有接口（含异常）返回体统一为 `com.carseller.product.common.R`（Phase 0 迁至 `com.carseller.common.result.R`），四个字段与实现完全对应：

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | Integer | 业务状态码，`200` 表示成功（见 [8.5](#85-错误码表)） |
| `message` | String | 提示信息，可直接展示给用户 |
| `data` | T | 业务数据，无数据时为 `null` |
| `timestamp` | Long | 服务器时间戳（毫秒） |

**成功（带数据）**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "iPhone 15 Pro",
    "description": "Apple 最新旗舰手机，A17 Pro 芯片",
    "price": 7999.00,
    "stock": 50,
    "category": "手机",
    "imageUrl": "/images/iphone15.jpg",
    "createdAt": "2026-11-05 10:00:00",
    "updatedAt": "2026-11-05 10:00:00"
  },
  "timestamp": 1762308000000
}
```

**成功（自定义提示）**：`POST /api/products` 返回 `R.ok("商品创建成功", product)` → `"message": "商品创建成功"`。

**失败（业务异常）**

```json
{
  "code": 404,
  "message": "商品不存在，id=999",
  "data": null,
  "timestamp": 1762308000000
}
```

**失败（参数校验）**

```json
{
  "code": 400,
  "message": "商品名称不能为空; 商品价格不能小于 0",
  "data": null,
  "timestamp": 1762308000000
}
```

工厂方法与约定（与 `R.java` 一致）：

| 方法 | 结果 |
|---|---|
| `R.ok()` | `code=200, message=操作成功, data=null` |
| `R.ok(data)` | `code=200, message=操作成功, data=data` |
| `R.ok(message, data)` | `code=200, message=自定义, data=data` |
| `R.fail(message)` | `code=500, message=自定义` |
| `R.fail(code, message)` | 指定业务码 |
| `R.fail(ResultCode)` / `R.fail(ResultCode, message)` | 使用预定义码 |

> **重要**：业务结果放在 `body.code` 中，HTTP 状态码统一为 **200**（`GlobalExceptionHandler` 未加 `@ResponseStatus`）。
> 例外：网关层的鉴权失败 / 限流 / 无可用实例由网关直接返回 `401 / 429 / 503`，此时响应体为网关默认错误体，**不保证是 `R` 结构**。

### 8.4 分页响应 `PageResult<T>`

分页接口的 `data` 统一为 `PageResult`（与 `PageResult.java` 一致）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `records` | List\<T\> | 当前页数据 |
| `total` | long | 总记录数 |
| `pageNum` | long | 当前页码（从 1 开始） |
| `pageSize` | long | 每页条数 |
| `pages` | long | 总页数 = `ceil(total / pageSize)` |

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [ { "id": 1, "name": "iPhone 15 Pro", "price": 7999.00 } ],
    "total": 10,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 1
  },
  "timestamp": 1762308000000
}
```

分页参数兜底规则（与 `ProductServiceImpl` 实现一致）：`pageNum < 1 → 1`；`pageSize < 1 → 10`；`pageSize > 100 → 100`；MyBatis-Plus 单页最大 500（`PaginationInnerInterceptor#setMaxLimit(500)`），页码越界返回空列表（`overflow=false`）。

### 8.5 错误码表

`ResultCode` 当前定义（与 `ResultCode.java` 完全一致）：

| code | 常量 | message | 使用场景 |
|---|---|---|---|
| 200 | `SUCCESS` | 操作成功 | 一切成功响应 |
| 400 | `BAD_REQUEST` | 请求参数错误 | 参数缺失/类型错误/校验失败/ID 非法 |
| 401 | `UNAUTHORIZED` | 未认证或登录已过期 | 网关鉴权失败、Token 过期 |
| 403 | `FORBIDDEN` | 无访问权限 | 越权访问他人资源 |
| 404 | `NOT_FOUND` | 资源不存在 | 商品/订单/库存记录不存在 |
| 500 | `FAILURE` | 操作失败 | 业务失败兜底、未知系统异常 |

**扩展码段规划**（Phase 0 起按需追加到 `ResultCode`，禁止各服务自定义散落数字）：

| 码段 | 归属 | 示例 |
|---|---|---|
| `1xxxx` | 通用/网关 | `10001` 令牌缺失、`10002` 令牌无效、`10003` 请求过于频繁 |
| `2xxxx` | 商品 | `20001` 商品名称重复 |
| `3xxxx` | 购物车 | `30001` 购物车条目不存在、`30002` 超出单商品上限、`30003` 购物车条目数量已达上限 |
| `4xxxx` | 订单 | `40001` 订单不存在、`40002` 订单状态不允许该操作 |
| `5xxxx` | 库存 | `50001` 库存不足、`50002` 重复扣减 |
| `6xxxx` | 支付 | `60001` 订单已支付、`60002` 支付单不存在 |

**已落地扩展码**（`ResultCode` 中已定义并使用）：`30001` `CART_ITEM_NOT_FOUND`、`30002` `CART_QUANTITY_EXCEEDED`、`30003` `CART_ITEM_LIMIT_EXCEEDED`、`40001` `ORDER_NOT_FOUND`、`40002` `ORDER_STATUS_INVALID`；其余码段仍为规划占位。

### 8.6 各服务 REST 端点清单

#### product-service（✅ 已实现，`ProductController`）

| 方法 | 路径 | 入参 | 返回 | 校验 |
|---|---|---|---|---|
| GET | `/api/products/{id}` | `id` 路径变量 | `R<Product>` | 不存在返回 404 |
| GET | `/api/products` | `pageNum`、`pageSize`、`keyword`、`category` | `R<PageResult<Product>>` | 分页兜底见 8.4 |
| POST | `/api/products` | `ProductRequest`（JSON） | `R<Product>`，`message=商品创建成功` | `@Valid` |
| PUT | `/api/products/{id}` | `ProductRequest`（JSON） | `R<Product>`，`message=商品更新成功` | `@Valid` |
| DELETE | `/api/products/{id}` | `id` 路径变量 | `R<Void>`，`message=商品删除成功` | 不存在返回 404 |

`ProductRequest` 字段约束：`name` 非空 ≤255；`description` ≤2000；`price` 非空 ≥0、8 位整数 2 位小数；`stock` 非空 ≥0；`category` ≤100；`imageUrl` ≤500。

查询语义（与 `ProductQuery` / `ProductServiceImpl` 一致）：`pageNum` 默认 1、`pageSize` 默认 10；`keyword` 对 **`name` 或 `description` 做模糊匹配**；`category` **精确匹配**；结果统一按 `id` 倒序（最新在前）。

#### cart-service（✅ Phase 2 已实现）

| 方法 | 路径 | 入参 | 返回 | 说明 |
|---|---|---|---|---|
| GET | `/api/cart` | 请求头 `X-User-Id` | `R<CartResponse>` | 数量/金额实时汇总，空车返回空结构 |
| POST | `/api/cart` | `{productId, quantity}` | `R<CartItemResponse>`，`message=加入购物车成功` | `quantity` ≥1，累加后 ≤99；新条目默认选中 |
| PUT | `/api/cart/{productId}` | `{quantity}` | `R<CartItemResponse>` | 覆盖为最终数量，≥1 且 ≤99 |
| PUT | `/api/cart/{productId}/selected` | `{selected}` | `R<CartItemResponse>` | 选中 / 取消选中 |
| DELETE | `/api/cart/{productId}` | `productId` 路径变量 | `R<Void>` | 条目不存在返回 `30001` |
| DELETE | `/api/cart` | 请求头 `X-User-Id` | `R<Void>` | 幂等清空（购物车 + 选中集合） |

`CartResponse` 字段：`userId`、`itemCount`、`totalQuantity`、`selectedQuantity`、`totalAmount`、`selectedAmount`、`items[]`；
`CartItemResponse` 字段：`productId`、`productName`、`imageUrl`、`price`、`stock`、`quantity`、`selected`、`available`、`subtotal`（= price × quantity）。

参数校验：`productId`、`quantity` 为必填正整数，`selected` 必填（`@Valid` 违规由 `GlobalExceptionHandler` 统一转 400）。
商品不存在：加购/改数量返回 `404`（透传 product-service 结果）；仅查询时降级为 `available=false`。

#### order-service（✅ 下单/查询/取消已实现；消费端 🚧 Phase 3）

| 方法 | 路径 | 入参 | 返回 | 校验 / 说明 |
|---|---|---|---|---|
| POST | `/api/orders` | `CreateOrderRequest`（JSON）+ `X-User-Id` | `R<OrderCreateResponse>`，`message=下单成功` | `items` 至少 1 条、最多 50 种且 `productId` 不可重复；`quantity` 1~99；`shippingAddress` 非空；`paymentMethod` ∈ `ALIPAY` / `WECHAT`；总金额按商品**最新价**计算 |
| GET | `/api/orders/{id}` | 路径变量 `id` + `X-User-Id` | `R<OrderDetailResponse>` | 订单不存在 `40001`；非本人订单 `403` |
| GET | `/api/orders` | `?pageNum&pageSize&status` + `X-User-Id` | `R<PageResult<OrderSummaryResponse>>` | `pageNum` 默认 1，`pageSize` 默认 10、上限 100（超出按 100）；`status` 非法返回 `400`；按 `id` 倒序 |
| POST | `/api/orders/{id}/cancel` | 路径变量 `id` + `X-User-Id` | `R<Void>`，`message=订单取消成功` | 仅 `PENDING` 可取消，其他状态 `40002`；幂等（重复取消返回成功，不重复发消息） |

`OrderCreateResponse` 字段：`orderId`、`totalPrice`、`status`（初始 `PENDING`）；
`OrderDetailResponse` 字段：`id`、`totalPrice`、`status`、`statusText`、`shippingAddress`、`contactPhone`、`paymentMethod`、`createdAt`、`updatedAt`、`items[]`（`OrderItemResponse`：`id`、`productId`、`productName`、`price`、`quantity`、`subtotal`）；
`OrderSummaryResponse` 字段：`id`、`totalPrice`、`status`、`statusText`、`paymentMethod`、`createdAt`（列表不携带明细，避免 N+1 与超大响应体）。

下单流程：`校验 → Feign 取商品最新价 → 本地事务落库（orders + order_items）→ 提交后发送 order-created-topic（Key=orderId）`；
消息发送失败不影响下单结果（库存扣减最终一致，由重试/补偿兜底）。

#### inventory-service（✅ 扣减 / 恢复 / 流水已实现）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/inventory/{productId}` | 查询可售库存 |
| POST | `/api/inventory/deduct` | 同步扣减（供 Feign 兜底/管理端） `{orderId, items:[{productId, quantity}]}`，同一 `orderId` 幂等 |
| POST | `/api/inventory/restore` | 按订单恢复库存 `{orderId}`（回补该订单全部已扣减记录，同一 `orderId` 幂等） |
| GET | `/api/inventory/logs` | 库存变动流水分页 |

#### payment-service（🚧 Phase 4）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/payments` | 发起支付 `{orderId, paymentMethod}`，返回 `{payNo, tradeNo, status}` |
| GET | `/api/payments/{payNo}` | 查询支付状态 |

#### auth-service（✅ 已实现，`AuthController`）

| 方法 | 路径 | 入参 | 返回 | 校验 / 说明 |
|---|---|---|---|---|
| POST | `/api/auth/register` | `RegisterRequest`（JSON） | `R<UserResponse>`，`message=注册成功` | 网关白名单；`@Valid` |
| POST | `/api/auth/login` | `LoginRequest`（JSON） | `R<String>`，`data` 为 JWT，`message=登录成功` | 网关白名单；失败返回 `401` 业务码 |
| GET | `/api/auth/me` | 请求头 `X-User-Id`（网关注入） | `R<UserResponse>` | 无身份返回 401，用户不存在返回 404 |
| PUT | `/api/auth/me` | `UserUpdateRequest`（JSON）+ `X-User-Id` | `R<UserResponse>`，`message=用户信息更新成功` | `@Valid`；仅更新非空字段 |

`RegisterRequest` 字段约束：`username` 非空 4~64 且只允许字母、数字、下划线；`password` 非空 6~72；`nickname` ≤64；`phone` 可选，符合手机号格式；`email` 可选，符合邮箱格式。用户名/手机号/邮箱三者均唯一，重复注册返回 `400`。

`UserUpdateRequest` 字段约束：`nickname` ≤64；`phone` 可选，符合手机号格式；`email` 可选，符合邮箱格式；`avatarUrl` ≤500。**请求体不含 `userId`，身份只取 `X-User-Id`。**

`UserResponse` 字段（**永不返回密码**）：`id`、`username`、`nickname`、`phone`、`email`、`avatarUrl`、`role`、`status`、`lastLoginAt`、`createdAt`。

### 8.7 接口变更与兼容性约定

1. **只增不改**：已发布的接口路径、方法语义、字段名不得删除或改名；新增能力用新端点或可选字段。
2. 响应体**新增字段**视为兼容变更，可以接受；**删除/重命名字段**视为破坏性变更，必须升级网关路由或加请求头版本号。
3. 分页参数、时间格式（`yyyy-MM-dd HH:mm:ss`）、金额类型（`BigDecimal` / JSON number）属于全局契约，改动需全量回归。
4. 每个服务的 `XxxRequest` / `XxxResponse` 必须与 [8.6](#86-各服务-rest-端点清单) 保持同步；文档先行，代码随后。
5. 跨服务 Feign 客户端接口以**被调用方的 OpenAPI 语义**为准，禁止在 Feign 侧自行增删字段。




## 9. 开发规范

### 9.1 编码红线

1. 文件编码统一 **UTF-8**，缩进 4 空格，行尾 `LF`（Windows 开发亦保持 LF，避免 diff 噪音）。
2. 禁止 `System.out.println`，一律 `Logger`（SLF4J，声明式 `LoggerFactory.getLogger(X.class)`）。
3. 禁止 `catch (Exception e) {}` 空实现或只打印不处理；业务可预期错误抛 `BusinessException`。
4. 禁止在 Controller 直接注入 `Mapper/Repository`。
5. 禁止魔法值：状态、Topic、Tag、Redis Key 前缀必须定义为常量或枚举。
6. 禁止把 `Entity` 直接当作对外响应（本期为简化沿用现有实现，Phase 0 后新增接口须使用 `XxxResponse`）。
7. 依赖注入优先**构造器注入**（现有 `ProductController` 即构造器注入），禁止 `@Autowired` 字段注入。
8. 单方法不超过 50 行，`if` 嵌套不超过 3 层。

### 9.2 统一响应与异常的使用规范

| 场景 | 正确做法 | 错误做法 |
|---|---|---|
| 查询成功 | `return R.ok(data)` | 直接返回实体 |
| 新增成功 | `return R.ok("商品创建成功", product)` | 返回 `Map` 拼装 |
| 资源不存在 | `throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在，id=" + id)` | 返回 `null` 让前端猜 |
| 参数非法 | `throw new BusinessException(ResultCode.BAD_REQUEST, "...")` | 返回 `R.fail("...")` 但 HTTP 200 混用语义 |
| 参数校验失败 | 声明 `@Valid`，由 `GlobalExceptionHandler` 统一转换 | 在 Controller 里手写 `if (x == null)` |
| 未知异常 | 直接抛出，由兜底 `Exception` handler 记录并返回"系统繁忙，请稍后重试" | 把堆栈信息透出给客户端 |

**异常处理清单**（`GlobalExceptionHandler` 已实现，各服务须保持一致）：

| 异常 | 处理结果 |
|---|---|
| `BusinessException` | `R.fail(e.code, e.message)` |
| `MethodArgumentNotValidException` / `BindException` | `code=400`，拼接所有字段错误信息（`"; "` 分隔） |
| `ConstraintViolationException` | `code=400`，拼接所有约束冲突信息 |
| `MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException` / `HttpMessageNotReadableException` | `code=400`，`"请求参数格式不正确：xxx"` |
| `Exception` | `code=500`，`"系统繁忙，请稍后重试"`，并 `log.error` 打印堆栈 |

> **Phase 0 要求**：将该类与 `R`、`ResultCode`、`PageResult`、`BusinessException` 一并抽取到 `car-seller-common`，各服务通过依赖引入，**禁止复制粘贴**。引入方式：在启动类所在包 `com.carseller.<svc>` 之上保证 `com.carseller.common` 可被扫描（`@SpringBootApplication` 默认扫描 `com.carseller.<svc>`，因此需在启动类上显式加 `@ComponentScan({"com.carseller.<svc>", "com.carseller.common"})`，或把公共包放在 `com.carseller` 根下由各服务继承扫描）。

### 9.3 参数校验规范

**技术栈**：`spring-boot-starter-validation`（Jakarta Bean Validation 3.0）+ `@Valid` / `@Validated`。

| 对象 | 用法 | 说明 |
|---|---|---|
| 请求体 | `@Valid @RequestBody XxxRequest` | 校验失败抛 `MethodArgumentNotValidException` |
| 查询参数对象 | `@Valid XxxQuery`（无 `@RequestBody`） | 校验失败抛 `BindException` |
| 单参数 | `@RequestParam @Min(1) Long id` + 类上 `@Validated` | 校验失败抛 `ConstraintViolationException` |

**注解使用清单**

| 注解 | 用途 | 本项目示例 |
|---|---|---|
| `@NotBlank` | 字符串非空且非空白 | `ProductRequest#name`："商品名称不能为空" |
| `@NotNull` | 对象非空 | `ProductRequest#price`："商品价格不能为空" |
| `@Size(max=)` | 长度上限 | `name` ≤ 255、`description` ≤ 2000、`imageUrl` ≤ 500 |
| `@Min` | 数值下限 | `stock` ≥ 0 |
| `@DecimalMin` / `@Digits` | 金额下限与精度 | `price` ≥ 0.00，`@Digits(integer=8, fraction=2)` |
| `@Pattern` | 正则 | 手机号 `^1[3-9]\\d{9}$`（订单收货信息，Phase 3） |
| `@Positive` | 正整数 | 购物车数量、分页大小 |

**规范**

1. `message` 必须写**中文用户可读文案**，不允许留默认英文。
2. 校验只做"格式/存在性"，**业务规则校验（如库存不足、状态非法）放 Service 层**，抛 `BusinessException`。
3. DTO 内不做跨字段校验时优先使用注解；跨字段（如 `startTime <= endTime`）使用类级自定义约束或 Service 校验。
4. 分组校验：新增用 `Create.class`、修改用 `Update.class`，通过 `@Validated(Create.class)` 指定；本期 `ProductRequest` 未分组（新增/修改共用），可以接受。
5. `@Valid` 与 `@Validated` 的区分：方法参数对象校验用 `@Valid`；类级/分组校验用 `@Validated`。

### 9.4 事务规范

1. **事务边界在 Service 层**，`@Transactional(rollbackFor = Exception.class)`（现有 `ProductServiceImpl` 已按此写法）。**禁止**标在 Controller 或 Mapper 上。
2. `@Transactional` 只在**公有方法**上生效；同类内部方法自调用不走代理，事务失效。
3. 查多写少的方法加 `@Transactional(readOnly = true)`，利于数据库优化。
4. **禁止长事务**：事务内不得调用 HTTP（OpenFeign）、不得发 MQ、不得做大量循环；消息发送必须放在事务提交**之后**（`TransactionSynchronizationManager#afterCommit` 或本地消息表）。
5. **禁止跨服务的本地事务**：跨服务一致性一律走 [7.4](#74-消息可靠性设计) 的最终一致方案。
6. 事务内异常必须抛出（`BusinessException` 继承 `RuntimeException`，可自动回滚）；吞掉异常会导致事务误提交。
7. `ROLLBACK` 后返回给前端的仍是统一 `R` 结构（由 `GlobalExceptionHandler` 转换），不要手动回滚后返回 `R.ok`。

### 9.5 日志规范

| 项 | 规范 |
|---|---|
| 框架 | SLF4J + Logback（Spring Boot 默认），`private static final Logger log = LoggerFactory.getLogger(Xxx.class);` |
| 占位符 | 必须用 `{}` 占位，禁止字符串拼接：`log.info("查询商品详情, id={}", id);` |
| 级别 | `error` 系统异常；`warn` 业务异常/参数校验失败；`info` 关键业务动作（下单、扣库存、支付）；`debug` 详细流程与 SQL（`com.carseller.product.mapper: debug` 已配置） |
| 位置 | Controller 记录接收入参；Service 记录状态变更；`GlobalExceptionHandler` 记录异常 |
| 必带信息 | 关键链路日志必须包含 `orderId` / `productId` / `userId` 等业务主键 |
| 脱敏 | 手机号中间 4 位打码（`138****8000`）、身份证/银行卡不打印、密码与 Token 永不打印 |
| 禁止 | `e.printStackTrace()`、打印大对象 `toString()` 全量（`Product#toString` 仅用于调试） |
| 文件 | 生产环境输出到 `logs/`（本仓库已建该目录），按天滚动；本地开发以控制台为主 |

### 9.6 公共模块复用策略（Phase 0 落地）

**方案**：新建 `car-seller-common`（`jar`，无 `spring-boot-maven-plugin`，不被 repackage 成可执行包），由根 pom `<modules>` 引入，其余 6 个模块 `dependency` 依赖它。

| 子包 | 内容 | 说明 |
|---|---|---|
| `com.carseller.common.result` | `R`、`ResultCode`、`PageResult` | 从 product-service 迁移 |
| `com.carseller.common.exception` | `BusinessException`、`GlobalExceptionHandler` | 迁移 + 需 `spring-boot-starter-web`、`validation` 为 `provided` 依赖 |
| `com.carseller.common.security` | `JwtUtils`、`UserContext`、`X-User-Id` 常量 | Phase 1 新增 |
| `com.carseller.common.mq` | `TopicConstants`、`TagConstants`、`MqEvent` 基类 | Phase 3 新增 |
| `com.carseller.common.util` | `PageUtils`、`DateUtils` | 按需新增 |

迁移验收：product-service 删除本地 `common` 包后仍能编译通过，且 5 个接口行为、响应体**完全不变**（用 [14](#14-接口自测与冒烟用例) 的用例回归）。

> 备选方案（不推荐）：各服务复制 `R` 等类。缺点是契约漂移、异常处理不一致，故不采用。

### 9.7 代码提交与评审规范

**提交信息**（Conventional Commits 简化版）：

```text
<type>(<scope>): <subject>

type  : feat | fix | refactor | docs | test | chore | perf
scope : gateway | product | cart | order | inventory | payment | common | docs | sql
示例  : feat(order): 下单接口返回 orderId 与订单状态
        docs(readme): 补充库存唯一真源约定
```

**提交前自检**：

1. `mvn -q -DskipTests clean install` 通过。
2. 新增/修改接口的响应体为 `R` / `PageResult`，异常走 `BusinessException`。
3. 新增配置项已在 [12](#12-本地开发与运行指南) 的配置表中补充说明。
4. 涉及端口、路由、表结构、Topic 的变更**必须同步更新本 README**（见 [18.2](#182-文档维护规范)）。
5. 不提交 `target/`、`.idea/`、`logs/` 与任何含真实密码的文件。

### 9.8 测试规范

| 层级 | 范围 | 工具 | 要求 |
|---|---|---|---|
| 单元测试 | Service 层业务规则（分页兜底、状态流转、金额计算） | JUnit 5 + Mockito（`spring-boot-starter-test`） | 核心 Service 方法必须覆盖正常 + 异常分支 |
| Web 层测试 | Controller + Validation + 异常转换 | `@WebMvcTest` + `MockMvc` | 至少覆盖"参数非法返回 400 且 message 中文" |
| 集成测试 | 真实 MySQL / Redis / RocketMQ 链路 | `@SpringBootTest` | Phase 3 起对「下单→扣库存」补 1 条端到端用例 |
| 接口冒烟 | 手工/脚本回归 | curl + PowerShell（见 [14](#14-接口自测与冒烟用例)） | 每次发布前全量跑一遍 |

约定：

1. 测试类命名 `XxxServiceTest` / `XxxControllerTest`，与被测类同包（`src/test/java` 下同路径）。
2. 测试方法命名 `methodName_condition_expectedResult`，如 `pageProducts_pageSizeOverLimit_limitedTo100`。
3. 单元测试**不启动 Spring 容器**（纯 Mockito），保证秒级执行；需要容器的放集成测试并加 `@Disabled` 或独立 profile。
4. 禁止在测试中连接生产库；测试数据自建自清。




## 10. 安全设计

### 10.1 安全总览

```text
外部请求
   │  ① TLS 终止（生产：Nginx / Ingress；本地：直连 HTTP）
   ▼
api-gateway  ② JWT 校验（签名 + 过期）→ 解析 userId → 注入 X-User-Id
   │         ③ 白名单放行（商品 GET、支付回调）
   │         ④ Sentinel 限流/熔断（防刷）
   ▼
业务服务     ⑤ 只信任 X-User-Id（来自网关），做资源归属校验（他人订单 403）
             ⑥ Spring Validation 校验参数（拒绝非法输入）
             ⑦ MyBatis 参数绑定（防 SQL 注入）
   ▼
MySQL / Redis / MQ   ⑧ 密码与密钥不进代码库；手机号等敏感信息脱敏
```

| 威胁 | 对策 |
|---|---|
| 未授权访问 | 网关统一 JWT 校验，业务端口不对外暴露 |
| 越权访问（拿别人订单） | Service 层按 `X-User-Id` 过滤，`WHERE id=? AND user_id=?` |
| 参数注入 | 全部使用 MyBatis 占位符 / JPA 参数绑定，禁止字符串拼接 SQL |
| 刷接口 | 网关 Sentinel 限流 + 业务侧幂等 |
| 重放攻击 | JWT 短有效期（2h）+ 关键操作（支付）幂等键 |
| 内部服务被绕过 | 生产环境业务服务只暴露在集群内网（K8s Service 不建 NodePort） |

### 10.2 网关鉴权与用户上下文

**Token 传递格式**

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidXNlck5hbWUiOiJ0b20iLCJleHAiOjE3NjIzMTUyMDB9.xxx
```

**网关处理流程（Phase 1 实现）**

1. 白名单直接放行：`GET /api/products/**`、`POST /api/payments/**`（真实渠道回调，另用签名校验）、`POST /api/auth/login`、`POST /api/auth/register`、健康检查端点。
2. 其余路径解析 `Authorization` 头：缺失 → `401`；签名错误/过期 → `401`。
3. 校验通过后**移除**原始 `Authorization` 之外的可疑头，注入受信任头：

   | 请求头 | 内容 | 说明 |
   |---|---|---|
   | `X-User-Id` | JWT `sub`（用户 ID） | 下游唯一用户身份来源 |
   | `X-User-Name` | JWT `userName` | 展示用 |
   | `X-Request-Id` | 网关生成的 UUID | 全链路 traceId |

4. 网关必须**先删除客户端自带的 `X-User-Id`** 再注入，防止伪造。可用 `RemoveRequestHeader=X-User-Id` 或自定义 `GlobalFilter` 清理。

**下游服务约定**

```java
// 正确：只从网关注入的请求头取用户身份
Long userId = Long.valueOf(request.getHeader("X-User-Id"));
// 错误：从请求体/查询参数取 userId —— 可被任意伪造
```

> `X-User-Id` 缺失时（例如直连服务调试），业务服务应返回 `401` 并提示"缺少用户上下文"，**不允许默认值为 1 之类的兜底**。
> 本地调试可临时通过直连 + 手工加头 `-H "X-User-Id: 1"` 绕过网关。

### 10.3 JWT 规范

| 项 | 约定 |
|---|---|
| 库 | `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` **0.11.5**（Phase 1 引入到 `car-seller-common`） |
| 算法 | `HS256`（对称，单体密钥分发简单）；如后续需多方校验可换 `RS256` |
| Claims | `sub`=userId（字符串）、`userName`、`iat`、`exp`、`jti`（Token 唯一 ID，用于黑名单） |
| 有效期 | Access Token **2 小时**；刷新由前端重新登录（本期不做 refresh token） |
| 密钥 | `jwt.secret`（≥32 字节），从 **Nacos 配置中心**或环境变量注入，**禁止硬编码进代码/提交仓库** |
| 存储端 | 客户端 `Authorization` 头；服务端无 Session（无状态） |
| 失效控制 | 登出/改密场景用 Redis 黑名单 `jwt:blacklist:{jti}`，TTL = 剩余有效期 |

**密钥配置示例（Nacos dataId `jwt-config.yaml` 或环境变量）**

```yaml
jwt:
  secret: ${JWT_SECRET:car-seller-dev-secret-key-please-change-in-prod-32bytes}
  expire-hours: 2
  header: Authorization
  prefix: "Bearer "
```

> 默认值仅用于本地开发；**生产必须通过环境变量 `JWT_SECRET` 覆盖**，否则启动日志打 `WARN`。

### 10.4 传输与数据安全

| 项 | 要求 |
|---|---|
| HTTPS | 生产由 Ingress/Nginx 终止 TLS（`https://api.example.com`）；服务间通信在集群内网，可后续接入 mTLS（本期不做） |
| 本地开发 | 允许 HTTP（`http://localhost:9000`），但**不得**把 Token 打到日志 |
| 数据库账号 | 每个服务使用**独立账号**，只授予自己库的权限；禁止 root 用于业务。当前 yml 中 `root/123456` 仅限本地开发，**上线前必须改环境变量** |
| 密码存储 | 如后续增加用户中心，密码必须 `BCrypt` 哈希（cost ≥ 10），禁止 MD5/SHA1 |
| 敏感字段 | `contact_phone` 输出脱敏；`payment_method` 原样返回；任何密钥、私钥、JWT secret 不得写入数据库或代码库 |
| 配置加密 | Nacos 中的密钥可用 `jasypt` 加密（Phase 6 可选） |
| SQL 注入 | 全部使用 MyBatis `#{}` / JPA 参数绑定；禁止 `${}` 拼接用户输入；排序字段用白名单映射 |

### 10.5 限流与熔断（Phase 5，Sentinel）

**依赖**：`com.alibaba.cloud:spring-cloud-starter-alibaba-sentinel` + `spring-cloud-alibaba-sentinel-gateway`（网关）。

**规则规划**

| 资源 | 阈值（示例） | 动作 |
|---|---|---|
| `GET /api/products/**` | 200 QPS | 快速失败，返回 `429` |
| `POST /api/orders` | 20 QPS（按 `X-User-Id` 限流 5 QPS） | 排队等待 500ms 后失败 |
| `POST /api/payments` | 10 QPS | 快速失败 |
| 下游 `product-service` 慢调用 | RT > 1s 且比例 > 50% | 熔断 10 秒 |

**约定**

1. 限流规则优先在**网关**配置（统一入口，规则集中）；服务内的 `@SentinelResource` 只用于保护对下游的调用（Feign + Sentinel 熔断）。
2. 触发限流返回统一结构：`{"code":429,"message":"请求过于频繁，请稍后重试","data":null,"timestamp":...}`。
3. 熔断降级时返回兜底数据或快速失败，**禁止**在降级逻辑里再发起远程调用。

### 10.6 安全检查清单（发布前逐项确认）

- [ ] 所有对外接口均经网关，业务服务端口未暴露到公网
- [ ] 网关已清理客户端伪造的 `X-User-Id` 并重新注入
- [ ] 涉及用户资源的接口（订单、购物车）已按 `X-User-Id` 过滤
- [ ] JWT secret 来自环境变量/Nacos，仓库中无明文生产密钥
- [ ] 数据库账号非 root，密码非 `123456`
- [ ] Validation 注解覆盖所有必填字段，`message` 为中文
- [ ] SQL 无字符串拼接，无 `${}` 注入点
- [ ] 日志中无 Token、密码、完整手机号
- [ ] MQ 消费端有幂等 + 死信告警



## 11. 中间件与分布式能力

### 11.1 Nacos（注册中心 + 配置中心）

**当前状态**：所有模块已配置 `spring.cloud.nacos.discovery.server-addr=localhost:8848`；**配置中心尚未接入**。

**规划（Phase 0/1）**

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:public}
        group: DEFAULT_GROUP
      config:
        server-addr: ${NACOS_ADDR:localhost:8848}
        file-extension: yaml
        namespace: ${NACOS_NAMESPACE:public}
        group: DEFAULT_GROUP
  config:
    import: optional:nacos:${spring.application.name}.yaml
```

| 配置项 | 规划 |
|---|---|
| `namespace` | `dev` / `test` / `prod` 隔离，禁止混用 |
| `group` | 统一 `DEFAULT_GROUP` |
| `dataId` | `<服务名>.yaml`（如 `product-service.yaml`）+ 公共 `<共享>.yaml`（`common-db.yaml`、`jwt-config.yaml`） |
| 动态刷新 | 需要热更新的类加 `@RefreshScope`；数据源、端口等**启动期配置不热更新** |
| 服务列表核对 | Nacos 控制台 `http://localhost:8848/nacos`（默认 nacos/nacos）→ 服务管理 → 服务列表应看到 6 个服务名 |

> **注意**：`spring-cloud-starter-alibaba-nacos-config` 依赖尚未加入任何模块，`spring.config.import` 缺失时 Nacos 配置不会生效（Alibaba 2022.x 必须显式 `import`）。

### 11.2 Redis 使用规范

**连接配置（cart-service 现状）**

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
```

| Key 模式 | 类型 | TTL | 归属 | 说明 |
|---|---|---|---|---|
| `cart:{userId}` | Hash | 7 天 | cart-service | field=`productId`，value=数量 |
| `cart:selected:{userId}` | Set | 7 天 | cart-service | 选中的 productId 集合 |
| `product:detail:{id}` | String(JSON) | 10 分钟 + 随机 0~60s | product-service | 商品详情缓存（Phase 5） |
| `lock:inventory:{productId}` | String | 10 秒（看门狗续期） | inventory-service | 分布式锁（Phase 3） |
| `mq:idempotent:{topic}:{msgId}` | String | 24 小时 | 消费方 | 消息幂等去重（Phase 3） |
| `jwt:blacklist:{jti}` | String | 剩余有效期 | api-gateway | Token 失效黑名单（Phase 1） |

**规范**

1. Key 必须带业务前缀，**禁止无前缀裸 Key**；统一使用常量类生成，不允许散落字符串。
2. 所有缓存 Key **必须设置 TTL**，禁止永久 Key（购物车可设 7 天并续期）。
3. 序列化统一 `StringRedisSerializer`(key) + `GenericJackson2JsonRedisSerializer`(value)，不允许 JDK 序列化（可读性 + 跨语言）。
4. 缓存更新策略：**先更新库、再删缓存**（Cache Aside），不做"更新缓存"。
5. 缓存穿透：空值缓存 60 秒；缓存击穿：热点 Key 用逻辑过期或互斥重建；缓存雪崩：TTL 加随机抖动。
6. 禁止把 Redis 当唯一数据源存不可丢失数据（购物车可接受，订单/库存不可接受）。

### 11.3 分布式锁（库存扣减）

**方案**：Redisson `RLock`（Phase 3 引入 `redisson-spring-boot-starter`）。

```java
RLock lock = redissonClient.getLock("lock:inventory:" + productId);
boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);   // 等待 3s，持有 10s（看门狗自动续期）
if (!locked) {
    throw new BusinessException(50001, "库存操作繁忙，请稍后重试");
}
try {
    // 1) 幂等校验：inventory_deduct_records(order_id, product_id) 唯一
    // 2) 条件更新：UPDATE inventories SET quantity = quantity - #{qty}
    //              WHERE product_id = #{pid} AND quantity >= #{qty}
    // 3) 影响行数 = 0 → 库存不足，抛业务异常
    // 4) INSERT inventory_logs(change_type='DECREASE')
    // 5) 提交事务后发送 inventory-deduct-result-topic
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();          // 必须判断持有线程，避免误释放他人锁
    }
}
```

| 规则 | 说明 |
|---|---|
| 锁粒度 | `productId`（同一商品串行，不同商品并行） |
| 锁在事务外层 | 加锁 → 开事务 → 提交 → 解锁；**不要在事务内加锁后立即释放**，否则锁会先于事务提交释放 |
| 兜底 | 即使锁失效，`WHERE quantity >= ?` 条件更新仍能保证不超卖 |
| 禁止 | 手写 `SETNX + EXPIRE`（非原子）、无超时的 `lock()` 无限等待 |
| 降级 | 锁服务不可用时：直接走条件更新（牺牲吞吐保正确性），并打 `WARN` |


## 12. 本地开发与运行指南

### 12.1 环境要求

| 软件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | `java -version` 必须为 17（Boot 3.x 不支持 8/11） |
| Maven | 3.8+ | 使用根 pom 统一构建 |
| MySQL | 8.0.x | 4 个库（product_db / order_db / inventory_db / cart_db 可选） |
| Redis | 6.x / 7.x | cart-service + 分布式锁 + 幂等去重 |
| Nacos | 2.2.x | 注册中心（配置中心 Phase 1 启用） |
| RocketMQ | 4.9.x / 5.x | NameServer `9876` + Broker `10911` |
| Git | 任意 | — |

### 12.2 中间件启动（示例命令）

```powershell
# Nacos（单机模式；控制台 http://localhost:8848/nacos，默认 nacos/nacos）
docker run -d --name nacos -p 8848:8848 -p 9848:9848 -e MODE=standalone nacos/nacos-server:v2.2.3

# MySQL（本地已装可跳过）
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=123456 mysql:8.0

# Redis
docker run -d --name redis -p 6379:6379 redis:7

# RocketMQ（NameServer + Broker）
docker run -d --name rmqnamesrv -p 9876:9876 apache/rocketmq:4.9.7 sh mqnamesrv
docker run -d --name rmqbroker -p 10911:10911 -p 10909:10909 `
  -e "NAMESRV_ADDR=host.docker.internal:9876" apache/rocketmq:4.9.7 sh mqbroker -n host.docker.internal:9876
```

> Windows 下 Docker Desktop 访问宿主机用 `host.docker.internal`；若中间件直接装在宿主机，各 yml 中的 `localhost` 无需修改。

### 12.3 数据库初始化

```powershell
mysql -uroot -p123456 < sql/product_db.sql
mysql -uroot -p123456 < sql/order_db.sql
mysql -uroot -p123456 < sql/inventory_db.sql
mysql -uroot -p123456 < sql/auth_db.sql
mysql -uroot -p123456 < sql/cart_db.sql      # 可选
```

验证：

```powershell
mysql -uroot -p123456 -e "select count(*) from product_db.products; select count(*) from order_db.orders; select count(*) from inventory_db.inventories; select count(*) from auth_db.users;"
```

预期：`10 / 5 / 10 / 3`。

> 当前 yml 中数据库账号为 `root / 123456`，与上述命令一致；如你的本地密码不同，请修改各服务 `application.yml` 或使用环境变量覆盖（见 [12.6](#126-配置项速查表)）。

### 12.4 构建与启动

**构建（根目录）**

```powershell
mvn -q -DskipTests clean install          # 全量构建，产出各模块 jar
```

**启动顺序**

1. Nacos → 2. MySQL / Redis → 3. RocketMQ（NameServer + Broker）→ 4. 业务服务 → 5. 网关

**方式一：Maven 启动（推荐用于验证）**

```powershell
mvn -pl product-service spring-boot:run
mvn -pl cart-service    spring-boot:run
mvn -pl order-service   spring-boot:run
mvn -pl inventory-service spring-boot:run
mvn -pl payment-service spring-boot:run
mvn -pl auth-service    spring-boot:run
mvn -pl api-gateway     spring-boot:run
```

**方式二：java -jar**

```powershell
java -jar product-service/target/product-service-1.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-1.0-SNAPSHOT.jar
```

**方式三：IDEA**——分别运行 7 个 `*Application` 主类（需保证 Nacos、MySQL 已启动）。

**启动后自检**

| 检查项 | 期望 |
|---|---|
| Nacos 控制台服务列表 | 出现 `api-gateway`、`product-service`、`cart-service`、`order-service`、`inventory-service`、`payment-service`、`auth-service` |
| 直连 product-service | `curl http://localhost:8081/api/products/1` 返回 `R` 结构 |
| 经网关访问 | `curl http://localhost:9000/api/products/1` 与直连结果一致 |
| 日志 | 无 `Connection refused`（Nacos/MySQL/Redis/MQ 连不上时会有） |

### 12.5 端口冲突排查

```powershell
# 查看端口占用
netstat -ano | findstr :8081
# 结束占用进程（把 <PID> 换成上面查到的进程号）
taskkill /PID <PID> /F
```

常见冲突：9000 被其他网关/IDE 插件占用；8848 被本地 Nacos 占用但版本不一致；3306 已有 MySQL 实例。

### 12.6 配置项速查表

| 配置项 | 所在模块 | 当前值 | 说明 |
|---|---|---|---|
| `server.port` | 全部 | 9000 / 8081 / 8082 / 8083 / 8084 / 8085 / 8086 | 见 [4.1](#41-模块总览端口与完成度) |
| `spring.application.name` | 全部 | 与模块名一致 | 服务名 = Nacos 注册名 = `lb://` 目标 |
| `spring.cloud.nacos.discovery.server-addr` | 全部 | `localhost:8848` | 注册中心地址 |
| `spring.datasource.url` | product / order / inventory / auth | 各自库 | `serverTimezone=Asia/Shanghai`，`allowPublicKeyRetrieval=true` |
| `spring.datasource.username/password` | product / order / inventory / auth | `root` / `123456` | **仅本地开发** |
| `spring.datasource.hikari.*` | product / order / auth | poolName=ProductHikariPool / OrderHikariPool / AuthHikariPool，max=20，minIdle=5 | 其余服务沿用默认 |
| `mybatis-plus.*` | product / order / auth | `mapper-locations=classpath*:/mapper/**/*.xml`、`map-underscore-to-camel-case=true`、`id-type=auto` | 新服务接入 MP 时复制此段 |
| `spring.jpa.hibernate.ddl-auto` | inventory | `update` | order-service 已于 Phase 0 迁移 MyBatis-Plus；inventory-service 待迁移 |
| `spring.cloud.openfeign.client.config.default.*` | order | connect 3s / read 5s | 读超时必须小于网关 `response-timeout`（10s） |
| `spring.data.redis.*` | cart | `localhost:6379` db0 | 锁/幂等复用同一实例，用不同 database 或 Key 前缀隔离 |
| `rocketmq.name-server` | order / inventory / payment | `localhost:9876` | — |
| `rocketmq.producer.group` | order / payment | `order-producer-group` / `payment-producer-group` | 不可与其他服务共用 |
| `rocketmq.consumer.group` | inventory | `inventory-consumer-group` | 同一 Topic 的不同消费逻辑必须用不同 group |
| `logging.level.com.carseller.*` | product | `info`（mapper 为 `debug`） | 排查 SQL 时临时调 `debug` |
| `jwt.secret` | gateway / auth-service（统一由 `car-seller-common` 提供 `JwtUtils`） | `${JWT_SECRET:car-seller-dev-...-32bytes}` | HS256 密钥，长度 ≥ 32 字节；**两侧必须完全一致**，**生产必须用环境变量 `JWT_SECRET` 覆盖**，使用默认值时启动日志打 `WARN` |
| `jwt.expire-hours` | 同上 | `2` | Token 有效期（小时），见 [10.3](#103-jwt-规范) |
| `jwt.header` / `jwt.prefix` | 同上 | `Authorization` / `"Bearer "` | Token 传递格式 |
| `gateway.auth.whitelist` | gateway | `GET /api/products/**`、`GET /api/inventory/*`、`POST /api/auth/login`、`* /actuator/**` 等 | 免鉴权路径，格式 `"HTTP方法 路径模式"`（方法可写 `*`）；未命中的请求必须携带合法 JWT |
| `spring.cloud.gateway.globalcors.*` | gateway | `allowedOriginPatterns=*`、`allowCredentials=true`、`maxAge=3600` | 全局跨域，业务服务不再重复配置 CORS |
| `spring.cloud.gateway.httpclient.*` | gateway | `connect-timeout=3000`、`response-timeout=10s` | 下游调用超时 |

### 12.7 环境变量与启动参数覆盖

Spring Boot 的 relaxed binding 允许用环境变量覆盖 yml，**优先级：命令行参数 > 环境变量 > Nacos 配置 > `application.yml`**。

```powershell
# 方式一：环境变量（推荐用于 CI / 容器）
$env:SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/product_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:SPRING_DATASOURCE_PASSWORD="MyStrongPwd"
$env:NACOS_ADDR="127.0.0.1:8848"
mvn -pl product-service spring-boot:run

# 方式二：-D 启动参数
java -jar product-service/target/product-service-1.0-SNAPSHOT.jar `
  --spring.datasource.password=MyStrongPwd `
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848
```

常用变量：`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`、`SPRING_DATA_REDIS_HOST`、`SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`、`ROCKETMQ_NAME_SERVER`、`JWT_SECRET`。



## 13. 分阶段实施路线图

> 每个阶段结束时：代码可编译、可启动、[14](#14-接口自测与冒烟用例) 的用例通过、README 已回填。**不允许跳过 Phase 0 直接做业务功能**。

| 阶段 | 主题 | 建议周期 | 前置依赖 | 核心交付物 |
|---|---|---|---|---|
| Phase 0 | 工程基建加固 | 第 1 周 | 无 | 版本升级、`car-seller-common`、持久层统一 |
| Phase 1 | 网关鉴权与用户上下文 | 第 2 周 | Phase 0 | JWT 过滤器、`X-User-Id` 透传、白名单 |
| Phase 2 | 购物车服务 | 第 3 周 | Phase 1 | Redis 购物车 CRUD、Feign 调商品 |
| Phase 3 | 订单 + 库存 MQ 闭环 | 第 4~5 周 | Phase 2 | 下单、异步扣减、状态机、幂等与死信 |
| Phase 4 | 支付服务 | 第 6 周 | Phase 3 | 模拟支付、`order-paid-topic`、订单转 PAID |
| Phase 5 | 缓存 + 限流 + 可观测 | 第 7 周 | Phase 4 | Redis 缓存、Sentinel、指标与日志 |
| Phase 6 | 容器化与持续交付 | 第 8 周 | Phase 5 | Dockerfile、compose、CI 流水线 |

### Phase 0：工程基建加固

**任务**

1. 根 pom 升级：`spring-boot.version` → `3.1.5`，`spring-cloud-dependencies` → `2022.0.4`（见 [2.3](#23-phase-0-升级执行清单)）。
2. 新建模块 `car-seller-common`（`jar`），迁移 `R`、`ResultCode`、`PageResult`、`BusinessException`、`GlobalExceptionHandler`；product-service 删除本地副本并依赖 common。
3. 持久层统一：`order-service`、`inventory-service` 由 Spring Data JPA 改为 MyBatis-Plus（去掉 `spring-boot-starter-data-jpa` 与 `spring.jpa.*` 配置，补数据源与 MP 配置）。
4. 各服务 `pom.xml` 统一补齐 `spring-boot-starter-validation`、`spring-boot-starter-test`。
5. 根目录新增 `.gitignore`（`target/`、`.idea/`、`logs/`、`*.iml`、`.env`）。
6. 为每个服务补 `src/test/java` 下最小 `contextLoads` 测试。

**验收**

```powershell
mvn -q -DskipTests clean install           # 7 个模块全部 SUCCESS
mvn -pl product-service test                # 测试通过
curl http://localhost:8081/api/products/1   # 响应结构与改动前完全一致
```

### Phase 1：网关鉴权与用户上下文

**任务**

1. `car-seller-common` 新增 `com.carseller.common.security`：`JwtUtils`、`JwtProperties`、`UserContext`、`UserHeaders` 常量（`X-User-Id`、`X-User-Name`、`X-Request-Id`）。
2. 引入 `jjwt-api/impl/jackson 0.11.5`。
3. `api-gateway` 实现 `JwtAuthGlobalFilter`：白名单放行 → 校验签名与过期 → 清理客户端伪造头 → 注入 `X-User-Id`；失败返回 `401` + `R` 结构 JSON。
4. `auth-service`（:8086）已实现 `POST /api/auth/login`、`POST /api/auth/register`、`GET|PUT /api/auth/me`，账号存 `auth_db.users`（BCrypt）、Token 由 `car-seller-common` 的 `JwtUtils` 签发；网关路由与白名单已同步。
5. 配置 `jwt.secret`、`jwt.expire-hours` 到 Nacos 或环境变量。

**验收**

```powershell
# 无 Token → 401
curl -i http://localhost:9000/api/orders
# 白名单 → 200
curl -i http://localhost:9000/api/products/1
# 带 Token → 200，且下游日志能打印 X-User-Id
curl -i -H "Authorization: Bearer <token>" http://localhost:9000/api/products/1
```

### Phase 2：购物车服务（✅ 已完成）

**任务**

1. `cart-service` 实现 6 个端点（见 [8.6](#86-各服务-rest-端点清单)），数据存 Redis Hash `cart:{userId}` + Set `cart:selected:{userId}`。
2. 引入 OpenFeign：`ProductClient#getProduct(Long id)` 调 `/api/products/{id}` 校验商品与取价。
3. 加购校验：商品存在、数量 ≥ 1、单商品数量上限 99、购物车条目上限 50。
4. 统一响应与异常（依赖 common）；`X-User-Id` 缺失返回 `401`。
5. 购物车 TTL 7 天，每次写入续期。

**验收**

```powershell
curl -X POST http://localhost:9000/api/cart -H "Authorization: Bearer <token>" `
     -H "Content-Type: application/json" -d '{"productId":1,"quantity":2}'
curl http://localhost:9000/api/cart -H "Authorization: Bearer <token>"
# 加购不存在的商品 → "code":404
```

### Phase 3：订单 + 库存 MQ 闭环（本期核心）

**任务**

1. ✅ `order-service`：`Order`、`OrderItem` 实体 + Mapper/Service；`POST /api/orders` 走「校验 → 本地事务落库 → 提交后发消息」流程（[7.1](#71-主流程一订单创建--异步扣减库存)）。（已完成，见 `OrderServiceImpl` / `OrderTxServiceImpl` / `OrderEventPublisher`）
2. ✅ `order-service`：引入 `spring-cloud-starter-openfeign` + `spring-cloud-starter-loadbalancer`，`ProductClient` / `InventoryClient` 均已实现；启动类加 `@EnableFeignClients`。（已完成，见 `client/ProductClient` / `client/InventoryClient`）
3. ✅ `inventory-service`：`@RocketMQMessageListener` 消费 `order-created-topic`，实现幂等扣减（Redis + `inventory_deduct_records` 双保险）、条件更新、流水记录，并发送 `inventory-deduct-result-topic`。（已完成，见 `OrderCreatedConsumer` / `OrderCancelledConsumer` / `InventoryEventPublisher` / `InventoryServiceImpl`）
4. ✅ `order-service`：消费 `inventory-deduct-result-topic`（`InventoryDeductResultConsumer`）/ `order-paid-topic`（`OrderPaidConsumer`）、发送 `order-cancelled-topic`（`OrderOutboxServiceImpl`）；状态机条件更新见 `OrderTxServiceImpl` / `OrderStateServiceImpl`。
5. ✅ 新增表：`order_db.order_message_outbox`（已追加到 `sql/order_db.sql`，唯一键 `uk_order_tag(order_id, tag)`）、`inventory_db.inventory_deduct_records`（✅ 已追加到 `sql/inventory_db.sql`）。
6. ✅ 超时关单：`OrderMaintenanceScheduler` 每 5 分钟扫描 `PENDING` 超 30 分钟的订单，置 `CANCELLED` 并发送 `order-cancelled-topic`；另一条 cron（错开 30 秒）扫描 `order_message_outbox` 中 `PENDING` / `FAILED` 的到期记录重发。
7. 冗余库存同步：inventory 扣减成功后发送事件，product-service 更新 `products.stock`（单向幂等）。
8. 死信队列告警与补偿脚本（可先用手工 SQL + 重发消息）。

**验收**

```powershell
# 下单（库存 50 / 商品 1）
curl -X POST http://localhost:9000/api/orders -H "Authorization: Bearer <token>" `
     -H "Content-Type: application/json" `
     -d '{"items":[{"productId":1,"quantity":2}],"shippingAddress":"北京市朝阳区","contactPhone":"13800138000","paymentMethod":"ALIPAY"}'
# 3 秒后查库存应为 48
curl http://localhost:9000/api/inventory/1
# 查订单状态应为 PENDING
curl http://localhost:9000/api/orders/<orderId> -H "Authorization: Bearer <token>"
```

| 用例 | 期望 |
|---|---|
| 正常下单 | 订单 `PENDING`，库存 -2，`inventory_logs` +1 |
| 库存不足（quantity 超大） | 订单最终 `CANCELLED`，库存不变 |
| 重复投递同一消息 | 库存只扣一次（幂等生效） |
| 停掉 inventory-service 后下单 | 订单 `PENDING`，重启消费后库存补齐（消息不丢） |

### Phase 4：支付服务

**任务**

1. `payment-service`：`POST /api/payments` → Feign 校验订单 `PENDING` → 生成 `payNo/tradeNo` → 模拟成功 → 发送 `order-paid-topic`。
2. 幂等：同一 `orderId` 已支付则返回 `60001`；`payNo` 唯一。
3. 可选：新增 `payment_db.payments` 流水表（`sql/payment_db.sql`）。
4. `order-service`：新增 `@RocketMQMessageListener` 消费 `order-paid-topic`，`PENDING → PAID` 条件更新。
5. 支付失败/订单已取消的补偿记录。

**验收**：支付后 3 秒内 `GET /api/orders/{id}` 返回 `status=PAID`；重复支付返回 `code=60001`。

### Phase 5：缓存 + 限流 + 可观测

**任务**

1. product-service：`GET /api/products/{id}` 加 Redis 缓存 `product:detail:{id}`（TTL 10 分钟 + 抖动），增删改后主动删除缓存。
2. 网关接入 Sentinel：`spring-cloud-starter-alibaba-sentinel` + `spring-cloud-alibaba-sentinel-gateway`，配置 [10.5](#105-限流与熔断phase-5sentinel) 的规则。
3. 所有服务接入 Actuator（`management.endpoints.web.exposure.include=health,info,prometheus`）+ Micrometer Prometheus。
4. 日志格式统一（含 `traceId` = `X-Request-Id`），可选 ELK。
5. Grafana 看板：QPS、P99、错误率、MQ 堆积、Hikari 连接池。

**验收**

```powershell
curl http://localhost:8081/actuator/health          # {"status":"UP"}
redis-cli GET product:detail:1                      # 第二次请求后存在
# 超过网关限流阈值 → HTTP 429
```

### Phase 6：容器化与持续交付

**任务**

1. 各模块 `Dockerfile`（多阶段构建，基于 `eclipse-temurin:17-jre-alpine`）。
2. 根目录 `docker-compose.yml`：nacos + mysql + redis + rocketmq + 6 个服务 + 网络编排。
3. CI 流水线（GitHub Actions / GitLab CI / Jenkins 任选）：`mvn -B clean verify` → 构建镜像 → 推送仓库。
4. K8s 清单（Deployment + Service + ConfigMap），要点见 [16.3](#163-kubernetes-编排要点)。
5. 生产 profile：`application-prod.yml` + 环境变量注入密钥。

**验收**：`docker compose up -d` 后 `curl http://localhost:9000/api/products/1` 返回 `code=200`。



## 14. 接口自测与冒烟用例

### 14.1 product-service（✅ 当前可直接运行）

> 前提：MySQL `product_db` 已初始化，product-service 已启动（直连 8081 或经网关 9000）。以下用直连演示。

```powershell
# 1) 商品详情
curl http://localhost:8081/api/products/1

# 2) 分页 + 关键字 + 分类
curl "http://localhost:8081/api/products?pageNum=1&pageSize=5&keyword=Pro"
curl "http://localhost:8081/api/products?pageNum=1&pageSize=5&category=手机"

# 3) 新增商品
curl -X POST http://localhost:8081/api/products `
  -H "Content-Type: application/json" `
  -d '{"name":"测试商品","description":"冒烟测试","price":99.90,"stock":10,"category":"测试","imageUrl":"/images/test.jpg"}'

# 4) 更新商品
curl -X PUT http://localhost:8081/api/products/11 `
  -H "Content-Type: application/json" `
  -d '{"name":"测试商品-改","price":88.80,"stock":8}'

# 5) 删除商品
curl -X DELETE http://localhost:8081/api/products/11
```

**期望结果**

| 用例 | 期望 |
|---|---|
| 1) 详情 | `code=200`，`data.id=1`，`data.name=iPhone 15 Pro`，`data.price=7999.00` |
| 2) 分页 | `code=200`，`data.records` 非空，`data.total>=1`，`data.pageSize=5` |
| 3) 新增 | `code=200`，`message=商品创建成功`，`data.id>0`，`data.createdAt` 已回填 |
| 4) 更新 | `code=200`，`message=商品更新成功`，`data.name=测试商品-改` |
| 5) 删除 | `code=200`，`message=商品删除成功`，`data=null` |

**异常用例（回归 Validation 与异常处理）**

| 用例 | 请求 | 期望 |
|---|---|---|
| 查询不存在 | `GET /api/products/999999` | `code=404`，`message="商品不存在，id=999999"` |
| ID 非法 | `GET /api/products/0` | `code=400`，`message="商品ID不合法"` |
| 名称为空 | `POST` 体 `{"price":1,"stock":1}` | `code=400`，`message` 含"商品名称不能为空" |
| 价格为负 | `POST` 体 `{"name":"x","price":-1,"stock":1}` | `code=400`，`message` 含"商品价格不能小于 0" |
| 价格精度 | `POST` 体 `price=1.234` | `code=400`，`message` 含"最多 8 位整数、2 位小数" |
| 非 JSON | `POST` 体 `not-json` | `code=400`，`message` 以"请求参数格式不正确："开头 |
| 参数类型错 | `GET /api/products?pageNum=abc` | `code=400` |

### 14.2 经网关验证路由（api-gateway 已启动）

```powershell
curl http://localhost:9000/api/products/1        # 应等价于直连 8081
curl -i http://localhost:9000/api/orders         # 下游未实现 → 503/404，属预期
```

判断标准：**返回内容与直连一致**即说明路由 + LoadBalancer + Nacos 生效。

### 14.3 后续阶段冒烟清单

| 阶段 | 用例 | 期望 |
|---|---|---|
| Phase 1 | 无 Token 访问 `/api/orders` | HTTP 401 |
| Phase 1 | 伪造头 `X-User-Id: 999` + 有效 Token | 下游收到的是 Token 中的 userId，不是 999 |
| Phase 2 | 加购 → 查询 → 改数量 → 删除 → 查询为空 | 每步 `code=200`，最终 `records` 为空 |
| Phase 3 | 下单 → 查库存减少 | 库存减少量 = 下单量，`inventory_logs` 新增 1 条 `DECREASE` |
| Phase 3 | 库存不足下单 | 订单最终 `status=CANCELLED`，库存不变 |
| Phase 4 | 支付 → 订单变 PAID | `POST /api/payments` 后轮询订单，`status=PAID` |
| Phase 4 | 重复支付同一订单 | 第二次返回 `code=60001` |
| Phase 5 | 重复请求商品详情 | 第二次命中缓存（Redis `product:detail:1` 存在） |

### 14.4 快速回归脚本（PowerShell）

```powershell
$base = "http://localhost:8081"
$cases = @(
  @{ name = "详情";   url = "$base/api/products/1" },
  @{ name = "分页";   url = "$base/api/products?pageNum=1&pageSize=5" },
  @{ name = "不存在"; url = "$base/api/products/999999" }
)
foreach ($c in $cases) {
  $r = Invoke-RestMethod -Uri $c.url -Method Get
  "{0}: code={1} message={2}" -f $c.name, $r.code, $r.message
}
```

期望输出：`详情: code=200`、`分页: code=200`、`不存在: code=404`。


## 15. 可观测性与运维

### 15.1 健康检查

| 端点 | 说明 | 状态 |
|---|---|---|
| `GET /actuator/health` | 各服务健康状态（含 DB / Redis / Discovery） | 🚧 Phase 5 引入 `spring-boot-starter-actuator` |
| `GET /actuator/info` | 版本信息 | 🚧 Phase 5 |
| `GET /actuator/prometheus` | Prometheus 抓取端点（`management.endpoints.web.exposure.include=health,info,prometheus`） | 🚧 Phase 5 |
| Nacos 控制台 | 服务实例在线状态 | ✅ 现在就可用 |

### 15.2 日志

| 项 | 方案 |
|---|---|
| 本地 | 控制台输出，`logging.level.com.carseller.<svc>=info`，Mapper 包 `debug` 可看 SQL |
| 文件 | `logs/` 目录（仓库已建），Spring Boot 默认 `logging.file.name` 未配置，Phase 5 用 `logback-spring.xml` 按天滚动 |
| 格式 | 建议 `时间 级别 [traceId] 线程 logger - 消息`，`traceId` 取网关注入的 `X-Request-Id` |
| 集中收集 | ELK：Filebeat → Logstash → Elasticsearch → Kibana；或轻量方案 Loki + Grafana |
| 关键检索维度 | `orderId`、`traceId`、`userId`、`topic` |

### 15.3 监控指标

| 指标 | 来源 | 告警建议 |
|---|---|---|
| HTTP QPS / P99 / 5xx 率 | Micrometer + Prometheus | P99 > 1s 或 5xx > 1% 告警 |
| JVM 堆 / GC | Micrometer | 老年代 > 80% 告警 |
| Hikari 连接池活跃数 / 等待数 | Micrometer | 等待线程 > 0 持续 1 分钟告警 |
| RocketMQ 堆积（Diff Total） | RocketMQ Exporter | 堆积 > 1000 告警 |
| 库存扣减失败率 | 自定义计数器 `inventory_deduct_fail_total` | 失败率 > 5% 告警 |
| Nacos 实例数 | Nacos 控制台 | 实例数少于期望值告警 |

### 15.4 链路追踪（可选，Phase 6）

引入 Micrometer Tracing + Zipkin/SkyWalking：网关生成 `traceId` → 通过 `X-Request-Id` 透传 → Feign 拦截器与 MQ 消息头携带 → 在 Zipkin 中查看「下单→扣库存→支付→改状态」完整链路。


## 16. CI/CD 与容器化

### 16.1 Dockerfile（每个模块一份，Phase 6）

```dockerfile
# 多阶段构建：构建阶段
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY . .
RUN mvn -B -q -DskipTests clean package

# 运行阶段
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/product-service/target/product-service-1.0-SNAPSHOT.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8081
ENTRYPOINT ["java","-Xms256m","-Xmx512m","-jar","/app/app.jar"]
```

### 16.2 docker compose 编排（Phase 6）

```yaml
services:
  nacos:
    image: nacos/nacos-server:v2.2.3
    environment: [ "MODE=standalone" ]
    ports: [ "8848:8848", "9848:9848" ]
  mysql:
    image: mysql:8.0
    environment: [ "MYSQL_ROOT_PASSWORD=123456" ]
    ports: [ "3306:3306" ]
  redis:
    image: redis:7
    ports: [ "6379:6379" ]
  namesrv:
    image: apache/rocketmq:4.9.7
    command: sh mqnamesrv
    ports: [ "9876:9876" ]
  broker:
    image: apache/rocketmq:4.9.7
    command: sh mqbroker -n namesrv:9876
    depends_on: [ namesrv ]
  product-service:
    build: ./product-service
    environment:
      SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR: nacos:8848
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/product_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    depends_on: [ nacos, mysql ]
  api-gateway:
    build: ./api-gateway
    environment:
      SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR: nacos:8848
    ports: [ "9000:9000" ]
    depends_on: [ nacos, product-service ]
```

> 容器内**禁止写 `localhost`** 访问其他容器：服务名即主机名（`nacos:8848`、`mysql:3306`、`namesrv:9876`）。本地宿主机仍是 `localhost`。

### 16.3 Kubernetes 编排要点

| 要点 | 说明 |
|---|---|
| 镜像 | 每个服务一个 Deployment，`replicas` 业务服务 ≥ 2 |
| 服务发现 | K8s 内部可用 Service + Nacos 双注册；`lb://` 仍由 Nacos 提供实例列表 |
| 配置 | 非敏感配置进 ConfigMap，密钥进 Secret（`JWT_SECRET`、DB 密码） |
| 健康探针 | `livenessProbe`/`readinessProbe` 指向 `/actuator/health`，`initialDelaySeconds` ≥ 40（JVM 启动慢） |
| 资源限制 | 业务服务 `requests: cpu 250m / mem 512Mi`，`limits: cpu 1 / mem 768Mi` |
| 暴露方式 | 仅 `api-gateway` 建 Ingress（TLS 终止在此）；业务服务用 `ClusterIP` |
| 优雅停机 | `server.shutdown=graceful` + `terminationGracePeriodSeconds: 30`，保证 MQ 消费线程处理完当前消息 |
| 滚动更新 | `strategy.rollingUpdate.maxUnavailable: 0`、`maxSurge: 1` |

### 16.4 CI/CD 流水线阶段

```text
checkout → mvn -B clean verify（编译 + 单测）→ 构建镜像 → 推送 registry
        → 部署 dev（docker compose / k8s）→ 冒烟（见 §14 脚本）→ 人工审批 → 生产
```

流水线门禁：编译失败、单测失败、镜像构建失败、冒烟用例失败任一项即中断。


## 17. 常见问题 FAQ

### 17.1 启动与注册

**Q1：启动报 `No spring.config.import property has been defined`**
A：Spring Cloud Alibaba 2022.x 要求显式声明导入来源。若使用 `nacos-config`，需在 yml 加 `spring.config.import: optional:nacos:${spring.application.name}.yaml`；当前工程未引入 nacos-config 依赖，若你手动加了依赖，请同时加 `import` 配置。

**Q2：Nacos 控制台看不到服务 / 网关调用返回 `503 Service Unavailable`**
排查顺序：
1. Nacos 是否启动：`curl http://localhost:8848/nacos`；
2. 服务 `application.yml` 中 `spring.cloud.nacos.discovery.server-addr` 是否可达（`localhost` 在容器内无效，见 [16.2](#162-docker-compose-编排phase-6)）；
3. 服务启动日志是否打印 `nacos registry ... success`；
4. Nacos 服务列表中的**服务名**必须与网关 `lb://xxx` 中的名字**完全一致**（`spring.application.name`）；
5. 是否 namespace / group 不一致（网关与业务服务必须同 namespace、同 group）。

**Q3：网关启动失败，报 `Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway`**
A：`api-gateway` 的 pom 中误加了 `spring-boot-starter-web`。Gateway 基于 WebFlux，必须移除该依赖。

### 17.2 数据库与中间件

**Q4：中文写入数据库变 `???` 或乱码**
A：① 库/表必须是 `utf8mb4`；② JDBC URL 带 `useUnicode=true&characterEncoding=utf8`（各服务 yml 已带）；③ `sql/*.sql` 中的中文注释在 UTF-8 终端下显示乱码属正常现象（文件为本地编码），不影响执行结果。

**Q5：`Access denied` / `Public Key Retrieval is not allowed`**
A：确认 `spring.datasource.password` 与实际 MySQL 密码一致；`allowPublicKeyRetrieval=true` 已在 URL 中，若仍报错请检查 MySQL 8 的 `caching_sha2_password` 插件与账号权限。

**Q6：RocketMQ 消息发了但消费不到**
1. `rocketmq.name-server` 是否为 `localhost:9876`（Broker 的 `brokerIP1` 需指向可达地址）；
2. 消费端 `@RocketMQMessageListener` 的 `topic`、`selectorExpression`（Tag）与生产端**完全一致**；
3. `consumerGroup` 与 yml 中 `rocketmq.consumer.group` 一致；
4. 生产端是否真的成功发送（`syncSend` 有返回值/异常日志）；
5. 消费端是否启动成功（日志中含 `started rocketmq consumer`）。

**Q7：购物车数据重启后丢失**
A：购物车存 Redis，**Redis 重启且未持久化即丢失**，属预期设计（购物车不是强一致数据）。需要持久化可开启 Redis AOF/RDB，或改为写 `cart_db`。

**Q8：`GET /api/products/1` 返回 404 但库里明明有数据**
A：确认 `product_db.products` 有 `id=1`；确认请求路径带 `/api/products` 前缀（网关不裁剪前缀，见 [3.4](#34-关键架构约定)）；确认服务连的是 `product_db` 而非其他库。

### 17.3 构建与依赖

**Q9：编译报 `package javax.validation... does not exist`**
A：Boot 3.x 全部改为 `jakarta.*`。把 `javax.validation.constraints.*` 改为 `jakarta.validation.constraints.*`，`javax.servlet.*` 改为 `jakarta.servlet.*`。

**Q10：Maven 报版本冲突 / `NoSuchMethodError`**
A：执行 `mvn dependency:tree -Dverbose` 定位；根 pom 的三个 BOM 已统一版本，子模块**不要**再指定 Spring 相关版本。

**Q11：`mvn spring-boot:run` 时 8081 被占用**
A：见 [12.5](#125-端口冲突排查)；常见原因是有残留的 java 进程。

### 17.4 接口与鉴权

**Q12：经网关请求返回 401 / 403**
A：401 = Token 缺失/无效/过期（Phase 1 后生效）；403 = 已登录但越权（如查询他人订单）。调试时可直连服务端口并手工加头 `-H "X-User-Id: 1"`。

**Q13：接口返回 `code=500` `message=系统繁忙，请稍后重试`**
A：说明抛了未预期异常，查服务日志中的 `系统异常` 堆栈（`GlobalExceptionHandler` 用 `log.error("系统异常", e)` 打印）。

**Q14：接口 HTTP 状态是 200 但 `code=400`，正常吗？**
A：正常。本项目的业务结果放在响应体 `R.code` 中，HTTP 统一 200（见 [8.3](#83-统一响应格式-rt)）。只有网关层的 401/429/503 才使用非 200 的 HTTP 状态。


## 18. 待办事项与文档维护

### 18.1 架构决策记录（ADR）

| 编号 | 决策 | 结论 | 理由 |
|---|---|---|---|
| A | 版本基线 | README 同时记录「当前实际版本」与「目标版本」，升级列入 Phase 0 | 文档与代码都成立，避免文档描述的能力与代码不符 |
| B | 持久层 | 统一 **MyBatis-Plus 3.5.5**，order/inventory 的 Spring Data JPA 在 Phase 0 重构 | 与已实现的 product-service 一致；分页、条件更新写法统一；避免两套 ORM 增加心智负担 |
| C | 公共代码 | 新建 `car-seller-common` 模块承载 `R`/异常/JWT/MQ 常量 | 复制粘贴会导致契约漂移与异常处理不一致 |
| D | 库存真源 | `inventories.quantity` 为唯一真源；`products.stock` 仅为展示冗余 | 避免两个写入口导致超卖/漂移 |
| E | 服务间调用 | **OpenFeign + LoadBalancer**（Phase 2/3 引入） | 声明式、与 Nacos 天然集成；RestTemplate 需手写负载均衡逻辑 |
| F | 购物车存储 | **Redis 优先**，`cart_db` 仅作可选备份 | 购物车更新频繁、可容忍丢失，强一致需求低 |
| G | 网关前缀策略 | 网关**不做** `StripPrefix`，服务自身路径带 `/api/xxx` | 直连与经网关路径一致，调试简单；且当前 yml 已如此配置 |
| H | 一致性方案 | 本地事务 + MQ 最终一致 + 幂等消费，**不引入 Seata** | 轻量级系统，2PC/TCC 成本过高 |

### 18.2 文档维护规范

1. 本 README 是**架构基线**：凡涉及**端口、路由、表结构、Topic、响应格式、错误码**的改动，必须在本文件同一次提交中更新对应章节。
2. 章节编号与目录保持一致；新增章节须同步更新顶部「目录」与本文档的「文档版本 / 最后更新」。
3. 状态标记统一使用：
   - `✅` 已实现并与代码一致
   - `🚧` 规划中 / 部分实现
   - `⚙️` 外部依赖（需自行准备环境）
4. 禁止在文档中写"待补充""TBD"等占位内容；未定方案写入 [18.1](#181-架构决策记录adr) 并在定稿后回填。
5. 引用外部链接或配置示例时，必须可直接复制执行（命令里的路径/端口与本仓库一致）。

### 18.3 待办事项（按优先级）

| # | 事项 | 阶段 | 状态 |
|---|---|---|---|
| 1 | 根 pom 升级 Boot 3.1.5 / Cloud 2022.0.4 | Phase 0 | 🚧 |
| 2 | 新建 `car-seller-common` 并迁移 `R`/异常/分页 | Phase 0 | 🚧 |
| 3 | order/inventory 由 JPA 改 MyBatis-Plus | Phase 0 | 🚧 order ✅，inventory 待迁移 |
| 4 | 统一补 `.gitignore`（`target/`、`.idea/`、`logs/`） | Phase 0 | 🚧 |
| 5 | 网关 JWT 鉴权 + `X-User-Id` 透传 | Phase 1 | ✅ |
| 6 | 接入 Nacos 配置中心（`nacos-config` + `spring.config.import`） | Phase 1 | 🚧 |
| 7 | 购物车服务实现（Redis） | Phase 2 | ✅ |
| 8 | 订单服务实现 + `order-created-topic` 发送 | Phase 3 | ✅ 下单/详情/分页/取消（消费端待实现） |
| 9 | 库存服务幂等扣减 + 结果回传 | Phase 3 | 🚧 |
| 10 | 超时关单定时任务 | Phase 3 | 🚧 |
| 11 | 支付服务 + `order-paid-topic` | Phase 4 | 🚧 |
| 12 | 商品详情 Redis 缓存 | Phase 5 | 🚧 |
| 13 | 网关 Sentinel 限流熔断 | Phase 5 | 🚧 |
| 14 | Actuator + Micrometer + Grafana 看板 | Phase 5 | 🚧 |
| 15 | Dockerfile / compose / CI 流水线 | Phase 6 | 🚧 |
| 16 | 数据库密码与 JWT 密钥改为环境变量注入 | Phase 6 | 🚧 |
| 17 | 支付退款流程（`REFUNDING`/`REFUNDED`） | Phase 6 预留 | 🚧 |
| 18 | 库存对账任务（`products.stock` ↔ `inventories.quantity`） | Phase 6 预留 | 🚧 |

### 18.4 变更记录

| 版本 | 日期 | 内容 |
|---|---|---|
| v1.0 | 2026-11-05 | 首版：确立技术栈版本基线、6 服务端口与职责、数据模型与库存真源约定、订单/支付两条 MQ 主流程与 Topic 契约、统一响应与错误码、开发与安全规范、Phase 0~6 实施路线图 |
| v1.1 | 2026-11-05 | 新增 `auth-service`（:8086，`com.carseller.auth`）：注册 / 登录 / 用户信息 4 个端点、`auth_db`（`users` + `user_login_logs`）脚本；网关新增 `/api/auth/**` 路由并白名单 `POST /api/auth/login`、`POST /api/auth/register`；JWT 签发统一走 `car-seller-common` |

---

**文档维护责任人**：`car-seller` 全体开发者（谁改代码谁改文档）。

