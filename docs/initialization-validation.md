# 初始化验证（2026-10-08）

- 后端：完整 Maven reactor 36 个模块构建成功（`-Pdev -DskipTests package`）。首次依赖下载使用本地临时 Maven Central 镜像配置；未修改系统 Maven 配置。
- 管理端：`pnpm build:dev` 成功。
- 微信小程序：`pnpm build:mp` 成功，产物位于 `miniapp/dist/build/mp-weixin`。
- 小程序改动文件 ESLint：`src/utils/index.ts`、`pages.config.ts`、`vite.config.ts` 检查通过。
- Docker Compose：`config --quiet` 通过；当前电脑使用现有服务，未启动 Docker 容器。
- 数据库：连接现有 MySQL 8.0.46 成功；初始化前 `bus` 无业务表，导入后有 36 张框架表。
- Redis：独立 Redis 8 实例在 `127.0.0.1:6380` 返回 PONG；原 6379 服务保留。
- 后端运行：使用 D:/dev 中的 JDK 17 成功启动，`GET /auth/tenant/list` 返回业务码 200。
- 管理端运行：`http://127.0.0.1:5178` 返回 HTTP 200；代理 `GET /dev-api/auth/tenant/list` 返回业务码 200。
- PowerShell 启动脚本语法检查通过。

限制：验证的是基础框架、构建和本地 API 连通；尚未验证微信真机登录、支付，也尚未实现客运预约业务。小程序仍为上游基础示例，乘客 API 契约需要后续适配。

上游构建包含浏览器数据过期、管理端包体较大等提示，不影响本次编译。未为消除提示而升级上游依赖。上游文件保留原始格式，部分文件含已有空白格式问题。

本机运行配置与密码保存在忽略的 `.local/` 和 `.env.*.local` 文件中，不随仓库提交。
