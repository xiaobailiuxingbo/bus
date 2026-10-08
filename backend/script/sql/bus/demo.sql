-- Optional demonstration data only. Coordinates intentionally unset: confirm actual stops before navigation.
INSERT IGNORE INTO bus_merchant VALUES ('000000','qinzhou-demo','归途客运 · 演示','13800000000',true,true);
INSERT IGNORE INTO bus_station(id,tenant_id,name,address,landmark,instructions) VALUES
('demo-s1','000000','钦州候车点','钦州 · 演示站点，非实际乘车地址','请由老板确认实际候车位置','提前10分钟到达；演示地址不可用于实际乘车'),
('demo-s2','000000','灵山候车点','灵山 · 演示站点，非实际乘车地址','站点现场照片待补充','到站后可报到，实际乘车由工作人员确认'),
('demo-s3','000000','南宁下车点','南宁 · 演示下车点，非实际乘车地址','','请与老板核实下车地点');
INSERT IGNORE INTO bus_route VALUES ('demo-route','000000','钦州 → 南宁','钦州','南宁','[{"station_id":"demo-s1","offset_minutes":0},{"station_id":"demo-s2","offset_minutes":45},{"station_id":"demo-s3","offset_minutes":150}]','[{"from":"demo-s1","to":"demo-s2","cents":4000},{"from":"demo-s1","to":"demo-s3","cents":6000},{"from":"demo-s2","to":"demo-s3","cents":5000}]',true);
INSERT IGNORE INTO bus_vehicle VALUES ('demo-vehicle','000000','舒适大巴 · 演示','桂N·演示',35,true);
INSERT IGNORE INTO bus_passenger VALUES ('demo-p1','000000','demo:1','演示乘客一'),('demo-p2','000000','demo:2','演示乘客二'),('demo-p3','000000','demo:3','演示乘客三');
