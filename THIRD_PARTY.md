# 上游来源与许可证

本仓库导入上游源码作为二次开发底座，保留各目录的 LICENSE 和 README。
根目录原有 Apache-2.0 LICENSE 不替代上游 MIT 许可证。

| 目录/依赖 | 来源 | 固定版本或提交 | 许可证 |
| --- | --- | --- | --- |
| backend | https://github.com/dromara/RuoYi-Vue-Plus | v5.6.2 / 8136a0191a2258c0e1b36a8146a1c5ebc070c139 | MIT |
| admin | https://github.com/CrazyLionCat/plus-ui | v5.6.2-v2.6.2 / d0d451967676707021b9857df529c395b27e90a7 | MIT |
| miniapp | https://github.com/feige996/unibest | base-wot-ui / 3aa7fe69aade0bdcf74a61795a6cc368f4aa8045（模板版本 3.18.11） | MIT |
| Wot Design Uni | https://github.com/Moonofweisheng/wot-design-uni | miniapp/pnpm-lock.yaml 中锁定 | MIT |
| WxJava | https://github.com/binarywang/WxJava | 4.8.0（miniapp、pay 模块） | Apache-2.0 |

小程序采用官方基础 Wot 模板，避免引入脚手架发布工具本身。当前使用 Wot v1（wot-design-uni），不是 Wot v2。
后端与管理端固定同一发布版本，保留 5.x 多租户能力。

初始化修改：统一目录结构；移除上游 Git 元数据、发布工作流和 Git hooks；更改应用名称、本地端口及 API 地址；关闭开发环境监控和 SnailJob；引入微信 SDK。上游元数据暂存在本地忽略目录 .bootstrap 中，不提交。

依赖项目的许可证仍分别适用，完整依赖版本以 lockfile 和 Maven POM 为准。
