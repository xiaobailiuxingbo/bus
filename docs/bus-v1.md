# 客运预约第一版

## 本地入口

- 管理端：http://127.0.0.1:5178/index ，使用现有若依账号登录。
- 乘客 H5 演示：http://127.0.0.1:9000 。执行 `cd miniapp; pnpm dev:h5 --host 127.0.0.1` 启动。
- 后端：http://127.0.0.1:8088 。执行 `./scripts/start-backend.ps1` 启动。
- 小程序：在 `miniapp` 执行 `pnpm dev:mp`，导入 `dist/dev/mp-weixin`。

演示商家、站点、车牌、手机号均是演示资料，不可据此实际乘车或收款。演示站点没有坐标，导航会说明待确认，不返回虚假位置。

## 初始化新环境

先完成若依数据库初始化，再依次执行：

1. `backend/script/sql/bus/V1__bus.sql`：新增业务表、业务菜单和两种角色。
2. **仅开发演示环境**执行 `backend/script/sql/bus/demo.sql`：独立演示资料与三个乘客身份。
3. 在本地 `.local/application-dev.yml` 增加 `bus.demo-login-enabled: true`。此开关默认关闭，且只有 dev/local profile 和本机请求可以使用。
4. 管理端创建草稿班次，分配工作人员，发布预约。

迁移不会重新导入或清空若依系统表。不同商家使用独立 tenant_id 和 entry_key；正式商家自行插入 bus_merchant 配置并保持 demo=false，不运行演示 seed。现有商家分享入口为 `?entry=qinzhou-demo`，小程序首页分享参数同理。

## 日常操作

1. 线路与站点：录入实际站点的地址、地标、照片和 GCJ-02 坐标，按站点顺序配置相对发车时间与可预约区间票价。停用基础资料不改变已有班次快照。
2. 班次与名单：选择线路、车辆、时间、容量及人员分配，保存草稿并发布。司机分配整个班次；站点人员分配指定站点。
3. 乘客预约：浏览无需登录；提交时使用微信登录或本地演示身份。每单1–6人，每人可以选择不同上下车区间。联系人手机号手动填写，没有短信验证。
4. 到站后乘客自行报到；工作人员确认实际到站、逐人上车和收款。批量操作仍按每个人落记录。
5. 收款方式为现金或微信转账，按钮表示**登记已实际收到的款项**，不发起支付或自动验证转账。
6. 取消未上车、未收款且尚未过所选站点计划时间的乘客会释放对应容量。老板可说明原因处理异常，已收款必须先冲正；完成班次不自动收款。
7. 对账：按日期范围选择班次，核对应收/已收/未收/取消/未乘车，查看收款人及正负流水，导出名单和收款 CSV。票款不是利润，未包括油费等成本。

若依角色 `客运老板`（bus_owner）拥有业务管理权限；`司机与站点人员`（bus_staff）拥有工作权限并必须有班次分配。角色不自动分配给任何现有普通账号；超级管理员可配置人员与角色。员工变更角色后重新登录以刷新若依权限缓存。

## 接口契约

业务响应为 `{code,msg,data}`，正常 code=200；400 参数或状态错误、401 乘客未登录、403 无权限、404 记录不存在、409 余位不足、503 外部服务未配置。金额使用整数分；站点和业务ID使用字符串；时间按 Asia/Shanghai 本地时间（ISO、不含时区）传输。

| 接口 | 用途 |
| --- | --- |
| GET /app/bus/merchant?entry=… | 商家品牌、联系方式及演示能力 |
| POST /app/auth/wx-login | `{entry,code}`；独立乘客登录 |
| POST /app/auth/dev-login | `{entry,index:1..3}`；仅本机开发演示 |
| POST /app/auth/logout | 注销乘客会话 |
| GET /app/bus/trips?entry=…&date=YYYY-MM-DD | 当天可预约班次 |
| GET /app/bus/trips/{id}?entry=… | 班次快照及容量 |
| GET /app/bus/stations/{id}?entry=… | 候车站点 |
| POST /app/bus/bookings | `{trip_id,contact_name,phone,request_key,riders:[{name,board_station,alight_station}]}` |
| GET /app/bus/bookings[/{id}] | 自己的预约/详情 |
| POST /app/bus/riders/{id}/arrive 或 cancel | 单人报到/取消；不会自动上车 |
| GET /bus/dashboard、/bus/trips | 工作台、获授权班次及汇总 |
| GET/POST /bus/catalog/{stations,routes,vehicles} | 基础管理；POST 带 id 为编辑，无 id 为新增 |
| GET /bus/employees | 当前商家可分配员工 |
| POST /bus/trips | 草稿班次；route_id、vehicle_id、depart_at、capacity、staff |
| POST /bus/trips/{id}/{publish,close,finish,cancel} | 班次状态操作；取消需 reason |
| POST /bus/trips/{id}/staff | `{staff:[{user_id,station_id}]}`；station_id='' 为整个班次 |
| GET /bus/trips/{id}/riders | 按权限过滤的名单 |
| POST /bus/bookings | 老板代录，使用同一套预约规则 |
| POST /bus/riders/{id}/{arrive,board,cancel} | 员工现场操作；取消仅老板且需 reason |
| POST /bus/riders/{id}/collect | `{method:CASH或WECHAT,request_key}`；全额收款登记 |
| POST /bus/trips/{id}/batch | `{ids,action:board或collect,method,request_key}`；整批事务 |
| GET /bus/trips/{id}/payments | 原始收款与冲正流水 |
| POST /bus/payments/{id}/reverse | `{reason}`；保留原始流水，不自动退款 |
| GET /bus/trips/{id}/export 或 payments/export | 带员工认证的 CSV 下载 |
| GET /bus/map/capabilities、search、geocode、reverse | 地图适配边界；当前未配置时能力关闭、查询返回503 |

乘客认证使用 `X-Passenger-Token`，与后台 Sa-Token 分离；令牌仅存储SHA-256摘要，8小时过期。后台使用原有 Authorization 和 clientid。请求中的 tenant_id 不作为授权来源。

线路 stops 为 `[{station_id,offset_minutes}]`，首站偏移0、顺序递增；fares 为 `[{from,to,cents}]`。站点照片使用 HTTPS URL 或已有本地资源路径。班次发布所用站点时间、地址、票价和车牌以创建班次时保存的快照为准；后续基础资料变化不会改变已有预约。

## 地图与微信正式接入

`GeoProvider` 预留地址搜索、地理编码和逆地理编码；当前实现为未配置适配器。未来替换为高德实现时，密钥放后端，管理端根据 capabilities 启用搜索选点。小程序导航封装独立使用内置地图，定位拒绝时仍可手选；距离为直线距离，不是路线长度。

正式微信登录配置后端 `BUS_WECHAT_APP_ID`、`BUS_WECHAT_SECRET`（或同名bus.wechat属性），前端本地配置真实 `VITE_WX_APPID`；配置合法 HTTPS 请求域名和必要位置权限。当前游客 AppID 未验证真实微信身份、位置授权和真机地图导航；线上支付不在首版范围。

## 验证

业务自动测试覆盖容量并发、幂等、部分取消、分别报到/上车/收款、票价快照、冲正、批量事务、商家与乘客隔离、员工范围、生产环境演示登录关闭、过期会话和未乘车记录。

```powershell
mvn -f backend/pom.xml -Pdev -pl ruoyi-modules/ruoyi-bus -am '-DskipTests=false' test
pnpm --dir admin build:dev
pnpm --dir miniapp build:mp
```

`node scripts/verify-passenger.mjs` 可针对本机空的演示班次（3–10席）进行 MySQL 黑盒验收；创建的演示预约会全部取消并恢复容量，保留取消历史。不用于真实商家。

7项业务自动测试通过。当前真实浏览器联调已完成：2人预约、1人报到、另1人取消、确认上车、现金登记、错误方式冲正、微信转账重新登记，最终净收款与班次汇总一致；收款CSV实际下载通过。页面分别检查390px手机和1280px桌面布局。

本机微信开发者工具CLI检查结果为未登录（login=false），当前仅确认小程序编译产物正常；未完成微信开发者工具内的实际页面运行或真机验证。后续由你登录工具并配置正式AppID后继续验证。
