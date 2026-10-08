# Bus 客运预约系统

面向客运老板、司机/站点人员和乘客的预约与对账项目。当前完成框架初始化，尚未实现客运业务。

## 目录

- `backend/`：RuoYi-Vue-Plus 5.6.2，Java 17、Spring Boot 3、MyBatis-Plus、Sa-Token；引入 WxJava 4.8.0 小程序与支付 SDK。
- `admin/`：配套 plus-ui 2.6.2，Vue 3、TypeScript、Element Plus。
- `miniapp/`：unibest 基础 Wot 模板，Vue 3、TypeScript、UniApp，编译为微信小程序。
- `compose.dev.yml`：本地 MySQL 和 Redis。
- `THIRD_PARTY.md`：上游版本、提交与许可证。

## 环境要求

Java 17、Maven 3.9+、Node.js 22.16+（或满足 Vite 的 Node 20.19+）、pnpm 10、微信开发者工具。
MySQL 8、Redis 7 可使用本机服务，也可使用 Docker Compose。

## 当前电脑已配置的开发环境

- JDK：`D:/dev/jdk-17.0.9/jdk-17.0.9`。
- Maven：`D:/dev/apache-maven-3.9.4`。
- MySQL：已有的 MySQL80 服务，`127.0.0.1:3306`，数据库 `bus`，已导入系统及工作流表。
- Redis：使用 `D:/dev/Redis-8.10.1-Windows-x64-cygwin-with-Service`，独立端口 `6380`；原来的 Redis 3.2 / 6379 保留。
- 本机数据库密码和 Redis 密码保存在 `.local/`，该目录不提交。示例配置见 `docs/application-dev.local.example.yml`。
- 本机已有程序占用 8080，后端使用 `8088`。管理端通过 `admin/.env.development.local` 代理到 8088；小程序通过 `miniapp/env/.env.local` 使用同一地址，这些本地覆盖文件均不提交。
- 本机管理端使用 `http://localhost:5178`，避开已有的 5173 服务。

当前电脑优先使用以下命令，不需要再启动 Compose：

```powershell
./scripts/start-redis.ps1
./scripts/start-backend.ps1
# 后端源码修改后重新构建并启动
./scripts/start-backend.ps1 -Build
```

Redis 的本地配置为开发缓存，未开启持久化。启动脚本仅设置当前进程 JDK，不修改系统环境变量。

## 本地启动

以下命令在仓库根目录执行。

```powershell
# 可选：启动本项目独立开发数据库，避免与现有 3306/6379 服务冲突
docker compose -f compose.dev.yml up -d

# 后端构建和启动，开发环境 HTTP 端口 8080
mvn -f backend/pom.xml -Pdev -DskipTests package
java -jar backend/ruoyi-admin/target/ruoyi-admin.jar

# 管理端，另开终端，访问 http://localhost:5173
pnpm --dir admin install
pnpm --dir admin dev

# 小程序，另开终端
pnpm --dir miniapp install
pnpm --dir miniapp dev:mp
```

微信开发者工具导入 `miniapp/dist/dev/mp-weixin`。
模板 AppID 已改为游客占位 `touristappid`，实际开发请在 `miniapp/env/.env.local` 配置自己的 `VITE_WX_APPID`。
游客模式不能验证微信登录、手机号和支付。
本地调试 API 默认为 `http://localhost:8080`，开发者工具中可关闭域名校验；真机需要可访问的开发地址。
体验版/正式版应在对应 `.env.*.local` 中配置 HTTPS API 地址及自己的 AppID，并配置合法域名。

首次启动 Compose 会导入系统和工作流 SQL，仅对空数据库卷生效。
若使用现有数据库：创建 `bus` 数据库，在其中导入上述两份 SQL，检查 `backend/ruoyi-admin/src/main/resources/application-dev.yml` 中数据库和 Redis 配置。
开发环境保留上游演示账号（admin/admin123）、客户端 ID 和示例加密配置，仅供本机调试，正式部署时必须替换相关凭据。
SnailJob 与监控客户端在开发环境关闭，暂不需要部署其服务。

## 构建检查

```powershell
mvn -f backend/pom.xml -Pdev -DskipTests package
pnpm --dir admin build:dev
pnpm --dir miniapp build:mp
```

编译通过不代表小程序与后端已完成业务联调。小程序模板自带登录/示例 API 的数据契约尚未接入若依，后续需要独立的乘客身份与接口适配。

## 后续业务范围

线路与上车点、班次容量、预约/取消、老板代录、到站与上车确认、收款流水及班次对账。
乘客身份与后台员工身份分开；司机/站点人员只能操作获授权的班次/站点；多租户业务表必须执行商家隔离。
到站、上车和付款分别记录，预约要防止重复占座及超卖，收款修改需保留业务流水。

## 仓库与许可证

远程仓库：https://github.com/xiaobailiuxingbo/bus.git

本仓库直接管理三个组件源码，不使用子模块。保留原有根目录 LICENSE，各上游目录许可证分别适用，参见 THIRD_PARTY.md。
