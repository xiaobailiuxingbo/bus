-- Additive migration. Execute once against the bus database. All queries scope tenant_id explicitly.
CREATE TABLE IF NOT EXISTS bus_merchant (
 tenant_id varchar(20) PRIMARY KEY, entry_key varchar(64) NOT NULL UNIQUE,
 name varchar(80) NOT NULL, phone varchar(20) NOT NULL, demo boolean NOT NULL DEFAULT false,
 enabled boolean NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS bus_station (
 id varchar(32) PRIMARY KEY, tenant_id varchar(20) NOT NULL, name varchar(80) NOT NULL,
 address varchar(250) NOT NULL, landmark varchar(200) NOT NULL DEFAULT '', photo varchar(500) NOT NULL DEFAULT '',
 instructions varchar(500) NOT NULL DEFAULT '', latitude decimal(10,7), longitude decimal(10,7),
 enabled boolean NOT NULL DEFAULT true, INDEX(tenant_id)
);
CREATE TABLE IF NOT EXISTS bus_route (
 id varchar(32) PRIMARY KEY, tenant_id varchar(20) NOT NULL, name varchar(100) NOT NULL,
 origin varchar(80) NOT NULL, destination varchar(80) NOT NULL,
 stops_json text NOT NULL, fares_json text NOT NULL, enabled boolean NOT NULL DEFAULT true, INDEX(tenant_id)
);
CREATE TABLE IF NOT EXISTS bus_vehicle (
 id varchar(32) PRIMARY KEY, tenant_id varchar(20) NOT NULL, name varchar(80) NOT NULL,
 plate varchar(20) NOT NULL, capacity int NOT NULL, enabled boolean NOT NULL DEFAULT true, INDEX(tenant_id)
);
CREATE TABLE IF NOT EXISTS bus_trip (
 id varchar(32) PRIMARY KEY, tenant_id varchar(20) NOT NULL, route_id varchar(32) NOT NULL,
 vehicle_id varchar(32) NOT NULL, depart_at datetime NOT NULL, capacity int NOT NULL, occupied int NOT NULL DEFAULT 0,
 status varchar(20) NOT NULL DEFAULT 'DRAFT', snapshot_json text NOT NULL, demo boolean NOT NULL DEFAULT false,
 INDEX(tenant_id,depart_at), CHECK(occupied >= 0 AND occupied <= capacity)
);
CREATE TABLE IF NOT EXISTS bus_trip_staff (
 tenant_id varchar(20) NOT NULL, trip_id varchar(32) NOT NULL, user_id bigint NOT NULL,
 station_id varchar(32) NOT NULL DEFAULT '', PRIMARY KEY(tenant_id,trip_id,user_id,station_id)
);
CREATE TABLE IF NOT EXISTS bus_passenger (
 id varchar(32) PRIMARY KEY, tenant_id varchar(20) NOT NULL, open_id varchar(100) NOT NULL,
 nickname varchar(80) NOT NULL, UNIQUE(tenant_id,open_id)
);
CREATE TABLE IF NOT EXISTS bus_session (
 token_hash varchar(64) PRIMARY KEY, tenant_id varchar(20) NOT NULL, passenger_id varchar(32) NOT NULL,
 expires_at datetime NOT NULL, INDEX(expires_at)
);
CREATE TABLE IF NOT EXISTS bus_booking (
 id varchar(32) PRIMARY KEY, tenant_id varchar(20) NOT NULL, trip_id varchar(32) NOT NULL,
 passenger_id varchar(32), contact_name varchar(80) NOT NULL, phone varchar(20) NOT NULL,
 request_key varchar(100) NOT NULL, fingerprint varchar(64) NOT NULL, created_at datetime NOT NULL,
 UNIQUE(tenant_id,request_key), INDEX(tenant_id,trip_id), INDEX(tenant_id,passenger_id)
);
CREATE TABLE IF NOT EXISTS bus_rider (
 id varchar(32) PRIMARY KEY, tenant_id varchar(20) NOT NULL, booking_id varchar(32) NOT NULL, trip_id varchar(32) NOT NULL,
 name varchar(80) NOT NULL, board_station varchar(32) NOT NULL, alight_station varchar(32) NOT NULL,
 board_at datetime NOT NULL, fare_cents int NOT NULL, status varchar(20) NOT NULL DEFAULT 'RESERVED',
 paid boolean NOT NULL DEFAULT false, arrived_at datetime, boarded_at datetime,
 INDEX(tenant_id,trip_id), INDEX(tenant_id,booking_id)
);
CREATE TABLE IF NOT EXISTS bus_payment (
 id varchar(32) PRIMARY KEY, tenant_id varchar(20) NOT NULL, rider_id varchar(32) NOT NULL, trip_id varchar(32) NOT NULL,
 amount_cents int NOT NULL, method varchar(20) NOT NULL, actor_id bigint NOT NULL,
 reverses_id varchar(32), operation_key varchar(100) NOT NULL, reason varchar(250) NOT NULL DEFAULT '',
 created_at datetime NOT NULL, UNIQUE(tenant_id,operation_key), UNIQUE(reverses_id), INDEX(tenant_id,trip_id)
);
CREATE TABLE IF NOT EXISTS bus_audit (
 id varchar(32) PRIMARY KEY, tenant_id varchar(20) NOT NULL, actor_id varchar(40) NOT NULL,
 event varchar(50) NOT NULL, entity_id varchar(32) NOT NULL, detail varchar(500) NOT NULL, created_at datetime NOT NULL
);

-- Business menus; no modification to existing role/menu assignments.
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES
(8100,'客运运营',0,1,'bus',NULL,1,0,'M','0','0','','guide',1,NOW()),
(8101,'班次与名单',8100,1,'trips','bus/trips',1,0,'C','0','0','bus:work','date',1,NOW()),
(8102,'线路与站点',8100,2,'catalog','bus/catalog',1,0,'C','0','0','bus:manage','location',1,NOW()),
(8103,'收款与对账',8100,3,'reports','bus/reports',1,0,'C','0','0','bus:manage','money',1,NOW()),
(8104,'基础管理权限',8102,1,'','','1','0','F','0','0','bus:manage','#',1,NOW());
INSERT IGNORE INTO sys_role(role_id,tenant_id,role_name,role_key,role_sort,status,create_by,create_time)
VALUES (8200,'000000','客运老板','bus_owner',10,'0',1,NOW()),(8201,'000000','司机与站点人员','bus_staff',11,'0',1,NOW());
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) VALUES
(8200,8100),(8200,8101),(8200,8102),(8200,8103),(8200,8104),(8201,8100),(8201,8101);
