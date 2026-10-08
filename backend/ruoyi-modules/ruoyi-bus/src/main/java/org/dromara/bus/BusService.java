package org.dromara.bus;

import cn.dev33.satoken.stp.StpUtil;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** All SQL is parameterized and explicitly tenant scoped (JDBC does not use the MyBatis tenant interceptor). */
@Service
public class BusService {
    public record Actor(String tenant, String id, boolean owner, boolean passenger) {}
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Environment env;
    public BusService(JdbcTemplate db, ObjectMapper json, Environment env) { this.db=db; this.json=json; this.env=env; }
    public static String id() { return UUID.randomUUID().toString().replace("-", ""); }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw BusError.bad("数据格式错误"); } }
    public Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?>)) throw BusError.bad("数据格式错误");
        return json.convertValue(value,new TypeReference<>(){});
    }
    public List<Map<String,Object>> array(Object value) {
        if (!(value instanceof List<?> list)) throw BusError.bad("列表格式错误");
        return list.stream().map(this::object).toList();
    }
    public static String text(Map<String,Object> data,String key,int max) {
        String value=Objects.toString(data.get(key),"").trim();
        if(value.isEmpty() || value.length()>max) throw BusError.bad("请正确填写 "+key);
        return value;
    }
    private static String optional(Map<String,Object> data,String key,int max) {
        String value=Objects.toString(data.get(key),"").trim();
        if(value.length()>max) throw BusError.bad(key+"内容过长"); return value;
    }
    public static int number(Object value,int min,int max) {
        try { int n=Integer.parseInt(value.toString()); if(n<min || n>max) throw new NumberFormatException(); return n; }
        catch(Exception e) { throw BusError.bad("数值超出允许范围"); }
    }
    private static LocalDateTime time(Object value) {
        try { return LocalDateTime.parse(value.toString().replace(" ","T")); }
        catch(Exception e) { throw BusError.bad("时间格式错误"); }
    }
    public List<Map<String,Object>> rows(String sql,Object... args) {
        List<Map<String,Object>> result=db.queryForList(sql,args);
        for(Map<String,Object> row:result) {
            for(String key:new ArrayList<>(row.keySet())) {
                Object value=row.get(key);
                if(value instanceof Timestamp t) row.put(key,t.toLocalDateTime().toString());
                if(key.endsWith("_json")) {
                    try { row.put(key.substring(0,key.length()-5),json.readValue(value.toString(),Object.class)); }
                    catch(Exception e) { throw new IllegalStateException("Stored bus JSON is invalid",e); }
                    row.remove(key);
                }
            }
        }
        return result;
    }
    public Map<String,Object> one(String sql,Object... args) {
        List<Map<String,Object>> list=rows(sql,args); if(list.isEmpty()) throw new BusError(404,"记录不存在或无权访问"); return list.get(0);
    }
    private long count(String sql,Object... args) { return Objects.requireNonNull(db.queryForObject(sql,Long.class,args)); }
    public Actor staffActor() {
        StpUtil.checkLogin();
        boolean owner=LoginHelper.isSuperAdmin() || StpUtil.hasPermission("bus:manage");
        if(!owner && !StpUtil.hasPermission("bus:work")) throw BusError.denied();
        return new Actor(LoginHelper.getTenantId(),LoginHelper.getUserIdStr(),owner,false);
    }
    public Actor passenger(HttpServletRequest request) {
        String token=request.getHeader("X-Passenger-Token");
        if(token==null || token.length()>200) throw new BusError(401,"请先登录乘客账号");
        List<Map<String,Object>> sessions=rows("SELECT s.tenant_id,s.passenger_id FROM bus_session s JOIN bus_merchant m ON m.tenant_id=s.tenant_id AND m.enabled=true WHERE s.token_hash=? AND s.expires_at>?",hash(token),LocalDateTime.now());
        if(sessions.isEmpty()) throw new BusError(401,"登录已过期，请重新登录");
        return new Actor(sessions.get(0).get("tenant_id").toString(),sessions.get(0).get("passenger_id").toString(),false,true);
    }
    public Map<String,Object> merchant(String entry) {
        return one("SELECT tenant_id,entry_key,name,phone,demo FROM bus_merchant WHERE entry_key=? AND enabled=true",entry);
    }
    public Map<String,Object> capabilities(String entry) {
        Map<String,Object> m=new LinkedHashMap<>(merchant(entry));
        m.remove("tenant_id"); m.put("demo_login",devEnabled()); m.put("map_search",false);
        return m;
    }
    private boolean devEnabled() {
        return Arrays.stream(env.getActiveProfiles()).anyMatch(p->p.equals("dev")||p.equals("local")) && Boolean.parseBoolean(env.getProperty("bus.demo-login-enabled","false"));
    }
    @Transactional
    public Map<String,Object> login(Map<String,Object> body,HttpServletRequest request,boolean demo) {
        Map<String,Object> merchant=merchant(text(body,"entry",64)); String tenant=merchant.get("tenant_id").toString();
        String openid; String nickname;
        if(demo) {
            if(!devEnabled() || !(request.getRemoteAddr().equals("127.0.0.1")||request.getRemoteAddr().equals("0:0:0:0:0:0:0:1")) || !Boolean.TRUE.equals(merchant.get("demo"))) throw BusError.denied();
            int index=number(body.get("index"),1,3); openid="demo:"+index; nickname="演示乘客 "+index;
        } else {
            String appid=env.getProperty("bus.wechat.app-id",""); String secret=env.getProperty("bus.wechat.secret","");
            if(appid.isBlank()||secret.isBlank()) throw new BusError(503,"微信登录尚未配置，请联系老板");
            try {
                WxMaDefaultConfigImpl config=new WxMaDefaultConfigImpl(); config.setAppid(appid); config.setSecret(secret);
                WxMaServiceImpl wx=new WxMaServiceImpl(); wx.setWxMaConfig(config);
                openid=wx.getUserService().getSessionInfo(text(body,"code",200)).getOpenid(); nickname="微信乘客";
            } catch(BusError e) { throw e; } catch(Exception e) { throw new BusError(503,"微信登录失败，请稍后重试"); }
        }
        db.update("INSERT IGNORE INTO bus_passenger(id,tenant_id,open_id,nickname) VALUES (?,?,?,?)",id(),tenant,openid,nickname);
        Map<String,Object> passenger=one("SELECT id,nickname FROM bus_passenger WHERE tenant_id=? AND open_id=?",tenant,openid);
        String token=id()+id(); db.update("DELETE FROM bus_session WHERE expires_at<?",LocalDateTime.now());
        db.update("INSERT INTO bus_session VALUES (?,?,?,?)",hash(token),tenant,passenger.get("id"),LocalDateTime.now().plusHours(8));
        return Map.of("token",token,"passenger",passenger,"demo",demo);
    }
    public void logout(HttpServletRequest request) {
        Actor a=passenger(request); db.update("DELETE FROM bus_session WHERE token_hash=? AND tenant_id=?",hash(request.getHeader("X-Passenger-Token")),a.tenant());
    }
    private void owner(Actor a) { if(a.passenger()||!a.owner()) throw BusError.denied(); }
    private Map<String,Object> trip(String tenant,String id,boolean lock) {
        return one("SELECT * FROM bus_trip WHERE tenant_id=? AND id=?"+(lock?" FOR UPDATE":""),tenant,id);
    }
    private void scope(Actor a,String tripId,String station) {
        if(a.passenger()) throw BusError.denied(); if(a.owner()) return;
        String sql="SELECT COUNT(*) FROM bus_trip_staff WHERE tenant_id=? AND trip_id=? AND user_id=?";
        if(station==null) { if(count(sql,a.tenant(),tripId,a.id())==0) throw BusError.denied(); }
        else if(count(sql+" AND (station_id='' OR station_id=?)",a.tenant(),tripId,a.id(),station)==0) throw BusError.denied();
    }
    private void audit(Actor a,String event,String entity,String reason) {
        db.update("INSERT INTO bus_audit VALUES (?,?,?,?,?,?,?)",id(),a.tenant(),a.id(),event,entity,reason,LocalDateTime.now());
    }
    public List<Map<String,Object>> catalog(Actor a,String type) {
        owner(a); String table=switch(type) { case "stations"->"bus_station"; case "routes"->"bus_route"; case "vehicles"->"bus_vehicle"; default->throw BusError.bad("未知类型"); };
        return rows("SELECT * FROM "+table+" WHERE tenant_id=? ORDER BY name",a.tenant());
    }
    public List<Map<String,Object>> employees(Actor a) {
        owner(a); return rows("SELECT user_id,user_name,nick_name FROM sys_user WHERE tenant_id=? AND status='0' AND del_flag='0'",a.tenant());
    }
    @Transactional
    public Map<String,Object> saveCatalog(Actor a,String type,Map<String,Object> body) {
        owner(a); String recordId=optional(body,"id",32); if(recordId.isBlank()) recordId=id();
        String name=text(body,"name",80); boolean enabled=!Boolean.FALSE.equals(body.get("enabled"));
        if(!optional(body,"id",32).isBlank()) catalogRecord(a.tenant(),type,recordId);
        switch(type) {
            case "stations" -> {
                Double lat=coordinate(body.get("latitude"),90),lng=coordinate(body.get("longitude"),180);
                if((lat==null)!=(lng==null)) throw BusError.bad("经纬度需要同时填写");
                String photo=optional(body,"photo",500); if(!photo.isBlank()&&!photo.startsWith("https://")&&!photo.startsWith("/")) throw BusError.bad("照片应为 HTTPS 地址或本地资源路径");
                db.update("INSERT INTO bus_station VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),address=VALUES(address),landmark=VALUES(landmark),photo=VALUES(photo),instructions=VALUES(instructions),latitude=VALUES(latitude),longitude=VALUES(longitude),enabled=VALUES(enabled)",recordId,a.tenant(),name,text(body,"address",250),optional(body,"landmark",200),photo,optional(body,"instructions",500),lat,lng,enabled);
            }
            case "vehicles" -> db.update("INSERT INTO bus_vehicle VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),plate=VALUES(plate),capacity=VALUES(capacity),enabled=VALUES(enabled)",recordId,a.tenant(),name,text(body,"plate",20),number(body.get("capacity"),1,100),enabled);
            case "routes" -> {
                List<Map<String,Object>> stops=array(body.get("stops")), fares=array(body.get("fares"));
                if(stops.size()<2||stops.size()>30) throw BusError.bad("线路需要2至30个站点");
                Set<String> stationIds=new HashSet<>(); int previous=-1;
                for(Map<String,Object> stop:stops) {
                    String station=text(stop,"station_id",32); int offset=number(stop.get("offset_minutes"),0,10080);
                    catalogRecord(a.tenant(),"stations",station);
                    if(!stationIds.add(station)||offset<=previous||previous==-1&&offset!=0) throw BusError.bad("站点不可重复，首站偏移为0，后续到站时间递增"); previous=offset;
                }
                Set<String> pairs=new HashSet<>();
                for(Map<String,Object> fare:fares) {
                    int from=index(stops,text(fare,"from",32)),to=index(stops,text(fare,"to",32));
                    if(from<0||to<=from||!pairs.add(fare.get("from")+":"+fare.get("to"))) throw BusError.bad("票价区间无效或重复");
                    number(fare.get("cents"),1,1000000);
                }
                if(fares.isEmpty()) throw BusError.bad("至少配置一个可预约区间票价");
                db.update("INSERT INTO bus_route VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),origin=VALUES(origin),destination=VALUES(destination),stops_json=VALUES(stops_json),fares_json=VALUES(fares_json),enabled=VALUES(enabled)",recordId,a.tenant(),name,text(body,"origin",80),text(body,"destination",80),encode(stops),encode(fares),enabled);
            }
            default -> throw BusError.bad("未知类型");
        }
        audit(a,"SAVE_"+type,recordId,"维护基础资料"); return catalogRecord(a.tenant(),type,recordId);
    }
    private Double coordinate(Object value,int max) {
        if(value==null||value.toString().isBlank()) return null;
        try { double n=Double.parseDouble(value.toString()); if(!Double.isFinite(n)||Math.abs(n)>max) throw new NumberFormatException(); return n; }
        catch(Exception e) { throw BusError.bad("坐标格式错误"); }
    }
    private Map<String,Object> catalogRecord(String tenant,String type,String recordId) {
        String table=switch(type) { case "stations"->"bus_station"; case "routes"->"bus_route"; case "vehicles"->"bus_vehicle"; default->throw BusError.bad("未知类型"); };
        return one("SELECT * FROM "+table+" WHERE tenant_id=? AND id=?",tenant,recordId);
    }
    public Map<String,Object> station(String entry,String stationId) { return catalogRecord(merchant(entry).get("tenant_id").toString(),"stations",stationId); }
    @Transactional
    public Map<String,Object> createTrip(Actor a,Map<String,Object> body) {
        owner(a); Map<String,Object> route=catalogRecord(a.tenant(),"routes",text(body,"route_id",32));
        Map<String,Object> vehicle=catalogRecord(a.tenant(),"vehicles",text(body,"vehicle_id",32));
        if(!Boolean.TRUE.equals(route.get("enabled"))||!Boolean.TRUE.equals(vehicle.get("enabled"))) throw BusError.bad("线路或车辆已停用");
        LocalDateTime departure=time(body.get("depart_at")); if(!departure.isAfter(LocalDateTime.now())) throw BusError.bad("发车时间须在未来");
        int capacity=number(body.get("capacity"),1,((Number)vehicle.get("capacity")).intValue());
        List<Map<String,Object>> stops=new ArrayList<>();
        for(Map<String,Object> routeStop:array(route.get("stops"))) {
            Map<String,Object> station=new LinkedHashMap<>(catalogRecord(a.tenant(),"stations",routeStop.get("station_id").toString()));
            if(!Boolean.TRUE.equals(station.get("enabled"))) throw BusError.bad("线路包含停用站点");
            station.put("station_id",station.get("id")); station.put("planned_at",departure.plusMinutes(((Number)routeStop.get("offset_minutes")).longValue()).toString()); station.remove("tenant_id"); stops.add(station);
        }
        Map<String,Object> snapshot=new LinkedHashMap<>(); snapshot.put("name",route.get("name")); snapshot.put("origin",route.get("origin")); snapshot.put("destination",route.get("destination")); snapshot.put("stops",stops); snapshot.put("fares",route.get("fares")); snapshot.put("vehicle_name",vehicle.get("name")); snapshot.put("plate",vehicle.get("plate"));
        String tripId=id(); db.update("INSERT INTO bus_trip VALUES (?,?,?,?,?,?,0,'DRAFT',?,?)",tripId,a.tenant(),route.get("id"),vehicle.get("id"),departure,capacity,encode(snapshot),Boolean.TRUE.equals(body.get("demo")));
        List<Map<String,Object>> staff=array(body.getOrDefault("staff",List.of()));
        for(Map<String,Object> assignment:staff) {
            String userId=text(assignment,"user_id",20),station=optional(assignment,"station_id",32);
            if(count("SELECT COUNT(*) FROM sys_user WHERE tenant_id=? AND user_id=? AND status='0' AND del_flag='0'",a.tenant(),userId)==0) throw BusError.bad("工作人员不存在");
            if(!station.isBlank()&&index(stops,station)<0) throw BusError.bad("分配站点不属于班次");
            db.update("INSERT IGNORE INTO bus_trip_staff VALUES (?,?,?,?)",a.tenant(),tripId,userId,station);
        }
        audit(a,"CREATE_TRIP",tripId,"创建草稿班次"); return trip(a.tenant(),tripId,false);
    }
    @Transactional
    public Map<String,Object> changeTrip(Actor a,String tripId,String action,String reason) {
        owner(a); Map<String,Object> trip=trip(a.tenant(),tripId,true); String old=trip.get("status").toString();
        String next=switch(action) {
            case "publish" -> { if(!old.equals("DRAFT")&&!old.equals("CLOSED")) throw BusError.bad("当前状态不能发布"); if(!time(trip.get("depart_at")).isAfter(LocalDateTime.now())) throw BusError.bad("班次已过发车时间"); yield "OPEN"; }
            case "close" -> { if(!old.equals("OPEN")) throw BusError.bad("当前状态不能停止预约"); yield "CLOSED"; }
            case "finish" -> { if(!old.equals("OPEN")&&!old.equals("CLOSED")) throw BusError.bad("当前状态不能结束"); yield "FINISHED"; }
            case "cancel" -> { if(old.equals("CANCELLED")||old.equals("FINISHED")) throw BusError.bad("班次已结束或取消"); if(reason.isBlank()) throw BusError.bad("请填写取消原因"); if(count("SELECT COUNT(*) FROM bus_rider WHERE tenant_id=? AND trip_id=? AND (paid=true OR status='BOARDED')",a.tenant(),tripId)>0) throw BusError.bad("请先处理已收款或已上车的乘客"); yield "CANCELLED"; }
            default -> throw BusError.bad("未知操作");
        };
        db.update("UPDATE bus_trip SET status=? WHERE tenant_id=? AND id=?",next,a.tenant(),tripId);
        if(next.equals("CANCELLED")) {
            db.update("UPDATE bus_rider SET status='CANCELLED' WHERE tenant_id=? AND trip_id=? AND status<>'CANCELLED'",a.tenant(),tripId);
            db.update("UPDATE bus_trip SET occupied=0 WHERE tenant_id=? AND id=?",a.tenant(),tripId);
        }
        if(next.equals("FINISHED")) db.update("UPDATE bus_rider SET status='MISSED' WHERE tenant_id=? AND trip_id=? AND status IN ('RESERVED','ARRIVED')",a.tenant(),tripId);
        audit(a,"TRIP_"+next,tripId,reason); return trip(a.tenant(),tripId,false);
    }
    public List<Map<String,Object>> publicTrips(String entry,String date) {
        String tenant=merchant(entry).get("tenant_id").toString();
        LocalDate day; try { day=LocalDate.parse(date); } catch(Exception e) { throw BusError.bad("日期格式错误"); }
        List<Map<String,Object>> trips=rows("SELECT * FROM bus_trip WHERE tenant_id=? AND status='OPEN' AND depart_at>=? AND depart_at<? AND depart_at>? ORDER BY depart_at",tenant,day.atStartOfDay(),day.plusDays(1).atStartOfDay(),LocalDateTime.now());
        trips.forEach(t->t.remove("tenant_id")); return trips;
    }
    public Map<String,Object> publicTrip(String entry,String tripId) {
        Map<String,Object> t=trip(merchant(entry).get("tenant_id").toString(),tripId,false); if(t.get("status").equals("DRAFT")) throw new BusError(404,"班次尚未发布"); t.remove("tenant_id"); return t;
    }
    public List<Map<String,Object>> trips(Actor a) {
        List<Map<String,Object>> trips;
        if(a.owner()) trips=rows("SELECT * FROM bus_trip WHERE tenant_id=? ORDER BY depart_at DESC",a.tenant());
        else trips=rows("SELECT t.* FROM bus_trip t WHERE t.tenant_id=? AND EXISTS(SELECT 1 FROM bus_trip_staff s WHERE s.tenant_id=t.tenant_id AND s.trip_id=t.id AND s.user_id=?) ORDER BY depart_at DESC",a.tenant(),a.id());
        for(Map<String,Object> t:trips) { t.put("summary",summary(riders(a,t.get("id").toString()))); t.put("staff",rows("SELECT user_id,station_id FROM bus_trip_staff WHERE tenant_id=? AND trip_id=?",a.tenant(),t.get("id"))); }
        return trips;
    }
    @Transactional
    public void assignStaff(Actor a,String tripId,List<Map<String,Object>> assignments) {
        owner(a); Map<String,Object> t=trip(a.tenant(),tripId,true);
        if(List.of("FINISHED","CANCELLED").contains(t.get("status"))) throw BusError.bad("已结束班次不能修改人员分配");
        List<Map<String,Object>> stops=array(object(t.get("snapshot")).get("stops"));
        for(Map<String,Object> s:assignments) {
            String userId=text(s,"user_id",20),station=optional(s,"station_id",32);
            if(count("SELECT COUNT(*) FROM sys_user WHERE tenant_id=? AND user_id=? AND status='0' AND del_flag='0'",a.tenant(),userId)==0) throw BusError.bad("工作人员不存在");
            if(!station.isBlank()&&index(stops,station)<0) throw BusError.bad("分配站点不属于班次");
        }
        db.update("DELETE FROM bus_trip_staff WHERE tenant_id=? AND trip_id=?",a.tenant(),tripId);
        for(Map<String,Object> s:assignments) db.update("INSERT IGNORE INTO bus_trip_staff VALUES (?,?,?,?)",a.tenant(),tripId,text(s,"user_id",20),optional(s,"station_id",32));
        audit(a,"ASSIGN_STAFF",tripId,"更新工作人员分配");
    }
    public List<Map<String,Object>> riders(Actor a,String tripId) {
        trip(a.tenant(),tripId,false); scope(a,tripId,null);
        String query="SELECT r.*,b.contact_name,b.phone,b.passenger_id FROM bus_rider r JOIN bus_booking b ON b.id=r.booking_id AND b.tenant_id=r.tenant_id WHERE r.tenant_id=? AND r.trip_id=?";
        if(a.owner()) return rows(query+" ORDER BY r.board_at,b.created_at,r.id",a.tenant(),tripId);
        return rows(query+" AND EXISTS(SELECT 1 FROM bus_trip_staff s WHERE s.tenant_id=r.tenant_id AND s.trip_id=r.trip_id AND s.user_id=? AND (s.station_id='' OR s.station_id=r.board_station)) ORDER BY r.board_at,b.created_at",a.tenant(),tripId,a.id());
    }
    public Map<String,Object> summary(List<Map<String,Object>> riders) {
        long reserved=0,boarded=0,cancelled=0,missed=0,due=0,paid=0;
        for(Map<String,Object> r:riders) {
            String status=r.get("status").toString(); if(status.equals("CANCELLED")) { cancelled++; continue; }
            reserved++; if(status.equals("BOARDED")) boarded++; if(status.equals("MISSED")) missed++;
            long fare=((Number)r.get("fare_cents")).longValue(); due+=fare; if(Boolean.TRUE.equals(r.get("paid"))) paid+=fare;
        }
        return Map.of("reserved",reserved,"boarded",boarded,"cancelled",cancelled,"missed",missed,"due_cents",due,"paid_cents",paid,"unpaid_cents",due-paid);
    }
    public Map<String,Object> dashboard(Actor a) {
        List<Map<String,Object>> trips=trips(a); String today=LocalDate.now().toString();
        List<Map<String,Object>> todayTrips=trips.stream().filter(t->t.get("depart_at").toString().startsWith(today)).toList();
        List<Map<String,Object>> all=new ArrayList<>(); for(Map<String,Object> t:todayTrips) all.addAll(riders(a,t.get("id").toString()));
        return Map.of("owner",a.owner(),"today",today,"trips",todayTrips,"summary",summary(all),"merchant",one("SELECT name,phone,demo FROM bus_merchant WHERE tenant_id=?",a.tenant()));
    }
    private int index(List<Map<String,Object>> stops,String station) { for(int i=0;i<stops.size();i++) if(station.equals(stops.get(i).get("station_id"))) return i; return -1; }
    @Transactional
    public Map<String,Object> book(Actor a,Map<String,Object> body) {
        String tripId=text(body,"trip_id",32); Map<String,Object> trip=trip(a.tenant(),tripId,true); if(!a.passenger()) owner(a);
        String key=(a.passenger()?"p:":"s:")+a.id()+":"+text(body,"request_key",60);
        String fingerprint=hash(encode(body)); List<Map<String,Object>> existing=rows("SELECT id,fingerprint FROM bus_booking WHERE tenant_id=? AND request_key=?",a.tenant(),key);
        if(!existing.isEmpty()) { if(!existing.get(0).get("fingerprint").equals(fingerprint)) throw BusError.bad("重复请求的内容发生变化，请重新提交"); return booking(a,existing.get(0).get("id").toString()); }
        if(!trip.get("status").equals("OPEN")||!time(trip.get("depart_at")).isAfter(LocalDateTime.now())) throw BusError.bad("本班次已停止预约");
        String phone=text(body,"phone",20); if(!phone.matches("1[3-9]\\d{9}")) throw BusError.bad("请填写有效的11位手机号");
        String contact=text(body,"contact_name",80); List<Map<String,Object>> requested=array(body.get("riders")); if(requested.isEmpty()||requested.size()>6) throw BusError.bad("每次预约1至6人");
        Map<String,Object> snapshot=object(trip.get("snapshot")); List<Map<String,Object>> stops=array(snapshot.get("stops")),fares=array(snapshot.get("fares"));
        List<Map<String,Object>> validated=new ArrayList<>();
        for(Map<String,Object> r:requested) {
            String name=text(r,"name",80),from=text(r,"board_station",32),to=text(r,"alight_station",32); int start=index(stops,from),end=index(stops,to);
            if(start<0||end<=start) throw BusError.bad("请选择合法的上下车站点");
            LocalDateTime boarding=time(stops.get(start).get("planned_at")); if(!boarding.isAfter(LocalDateTime.now())) throw BusError.bad("所选上车点已过预约时间");
            Map<String,Object> fare=fares.stream().filter(f->from.equals(f.get("from"))&&to.equals(f.get("to"))).findFirst().orElseThrow(()->BusError.bad("此区间暂未配置票价"));
            validated.add(Map.of("name",name,"from",from,"to",to,"boarding",boarding,"cents",fare.get("cents")));
        }
        if(db.update("UPDATE bus_trip SET occupied=occupied+? WHERE tenant_id=? AND id=? AND occupied+?<=capacity",requested.size(),a.tenant(),tripId,requested.size())!=1) throw new BusError(409,"余位不足，请减少人数或选择其他班次");
        String bookingId=id(); db.update("INSERT INTO bus_booking VALUES (?,?,?,?,?,?,?,?,?)",bookingId,a.tenant(),tripId,a.passenger()?a.id():null,contact,phone,key,fingerprint,LocalDateTime.now());
        for(Map<String,Object> r:validated) db.update("INSERT INTO bus_rider(id,tenant_id,booking_id,trip_id,name,board_station,alight_station,board_at,fare_cents) VALUES (?,?,?,?,?,?,?,?,?)",id(),a.tenant(),bookingId,tripId,r.get("name"),r.get("from"),r.get("to"),r.get("boarding"),r.get("cents"));
        audit(a,"BOOK",bookingId,"预约 "+requested.size()+" 人"); return booking(a,bookingId);
    }
    public Map<String,Object> booking(Actor a,String bookingId) {
        Map<String,Object> b=one("SELECT * FROM bus_booking WHERE tenant_id=? AND id=?",a.tenant(),bookingId);
        if(a.passenger()&&!a.id().equals(b.get("passenger_id"))) throw BusError.denied();
        if(!a.passenger()) owner(a);
        b.remove("request_key"); b.remove("fingerprint"); b.put("riders",rows("SELECT * FROM bus_rider WHERE tenant_id=? AND booking_id=? ORDER BY id",a.tenant(),bookingId)); b.put("trip",trip(a.tenant(),b.get("trip_id").toString(),false)); return b;
    }
    public List<Map<String,Object>> bookings(Actor a) {
        return rows("SELECT b.id,b.contact_name,b.phone,b.created_at,t.snapshot_json,t.depart_at,t.status AS trip_status FROM bus_booking b JOIN bus_trip t ON t.id=b.trip_id AND t.tenant_id=b.tenant_id WHERE b.tenant_id=? AND b.passenger_id=? ORDER BY b.created_at DESC LIMIT 100",a.tenant(),a.id());
    }
    @Transactional
    public void riderAction(Actor a,String riderId,String action,String reason) {
        Map<String,Object> locator=one("SELECT trip_id FROM bus_rider WHERE tenant_id=? AND id=?",a.tenant(),riderId);
        String tripId=locator.get("trip_id").toString(); Map<String,Object> trip=trip(a.tenant(),tripId,true);
        Map<String,Object> rider=one("SELECT r.*,b.passenger_id FROM bus_rider r JOIN bus_booking b ON b.id=r.booking_id AND b.tenant_id=r.tenant_id WHERE r.tenant_id=? AND r.id=? FOR UPDATE",a.tenant(),riderId);
        if(a.passenger()) { if(!a.id().equals(rider.get("passenger_id"))) throw BusError.denied(); }
        else scope(a,tripId,rider.get("board_station").toString());
        String old=rider.get("status").toString();
        switch(action) {
            case "arrive" -> {
                if(old.equals("ARRIVED")||old.equals("BOARDED")) return;
                if(!old.equals("RESERVED")||List.of("FINISHED","CANCELLED").contains(trip.get("status"))) throw BusError.bad("当前状态不能报到");
                db.update("UPDATE bus_rider SET status='ARRIVED',arrived_at=? WHERE tenant_id=? AND id=?",LocalDateTime.now(),a.tenant(),riderId);
            }
            case "board" -> {
                if(a.passenger()) throw BusError.denied(); if(old.equals("BOARDED")) return;
                if(!List.of("RESERVED","ARRIVED").contains(old)||List.of("FINISHED","CANCELLED").contains(trip.get("status"))) throw BusError.bad("当前状态不能确认上车");
                db.update("UPDATE bus_rider SET status='BOARDED',boarded_at=? WHERE tenant_id=? AND id=?",LocalDateTime.now(),a.tenant(),riderId);
            }
            case "cancel" -> {
                if(old.equals("CANCELLED")) return;
                if(Boolean.TRUE.equals(rider.get("paid"))) throw BusError.bad("请先由老板处理已收款记录");
                if(a.passenger() && (!List.of("RESERVED","ARRIVED").contains(old)||!time(rider.get("board_at")).isAfter(LocalDateTime.now()))) throw BusError.bad("已过上车时间或已上车，请联系老板处理");
                if(!a.passenger()) { owner(a); if(reason.isBlank()) throw BusError.bad("请填写处理原因"); }
                db.update("UPDATE bus_rider SET status='CANCELLED' WHERE tenant_id=? AND id=?",a.tenant(),riderId);
                db.update("UPDATE bus_trip SET occupied=occupied-1 WHERE tenant_id=? AND id=? AND occupied>0",a.tenant(),tripId);
            }
            default -> throw BusError.bad("未知乘客操作");
        }
        audit(a,"RIDER_"+action,riderId,reason);
    }
    @Transactional
    public void collect(Actor a,String riderId,String method,String requestKey) {
        if(a.passenger()) throw BusError.denied();
        if(!List.of("CASH","WECHAT").contains(method)) throw BusError.bad("请选择现金或微信转账");
        Map<String,Object> locator=one("SELECT trip_id FROM bus_rider WHERE tenant_id=? AND id=?",a.tenant(),riderId); String tripId=locator.get("trip_id").toString();
        Map<String,Object> t=trip(a.tenant(),tripId,true); Map<String,Object> r=one("SELECT * FROM bus_rider WHERE tenant_id=? AND id=? FOR UPDATE",a.tenant(),riderId); scope(a,tripId,r.get("board_station").toString());
        String key="collect:"+a.id()+":"+requestKey; if(key.length()>100||requestKey.isBlank()) throw BusError.bad("操作标识错误");
        List<Map<String,Object>> existing=rows("SELECT rider_id,method FROM bus_payment WHERE tenant_id=? AND operation_key=?",a.tenant(),key);
        if(!existing.isEmpty()) { if(!existing.get(0).get("rider_id").equals(riderId)||!existing.get(0).get("method").equals(method)) throw BusError.bad("操作标识已用于其他收款"); return; }
        if(r.get("status").equals("CANCELLED")||t.get("status").equals("CANCELLED")) throw BusError.bad("已取消乘客不可收款");
        if(Boolean.TRUE.equals(r.get("paid"))) return;
        db.update("INSERT INTO bus_payment VALUES (?,?,?,?,?,?,?,NULL,?,'',?)",id(),a.tenant(),riderId,tripId,r.get("fare_cents"),method,a.id(),key,LocalDateTime.now());
        db.update("UPDATE bus_rider SET paid=true WHERE tenant_id=? AND id=?",a.tenant(),riderId); audit(a,"COLLECT",riderId,method);
    }
    @Transactional
    public void reverse(Actor a,String paymentId,String reason) {
        owner(a); if(reason.isBlank()||reason.length()>250) throw BusError.bad("请填写冲正原因（不超过250字）");
        Map<String,Object> locator=one("SELECT * FROM bus_payment WHERE tenant_id=? AND id=?",a.tenant(),paymentId);
        trip(a.tenant(),locator.get("trip_id").toString(),true);
        Map<String,Object> payment=one("SELECT * FROM bus_payment WHERE tenant_id=? AND id=? FOR UPDATE",a.tenant(),paymentId);
        if(((Number)payment.get("amount_cents")).intValue()<=0) throw BusError.bad("只能冲正原始收款");
        if(count("SELECT COUNT(*) FROM bus_payment WHERE tenant_id=? AND reverses_id=?",a.tenant(),paymentId)>0) return;
        db.update("INSERT INTO bus_payment VALUES (?,?,?,?,?,?,?,?,?,?,?)",id(),a.tenant(),payment.get("rider_id"),payment.get("trip_id"),-((Number)payment.get("amount_cents")).intValue(),payment.get("method"),a.id(),paymentId,"reverse:"+paymentId,reason,LocalDateTime.now());
        db.update("UPDATE bus_rider SET paid=false WHERE tenant_id=? AND id=?",a.tenant(),payment.get("rider_id")); audit(a,"REVERSE",paymentId,reason);
    }
    @Transactional
    public void batch(Actor a,String tripId,Map<String,Object> body) {
        trip(a.tenant(),tripId,true); scope(a,tripId,null);
        Object raw=body.get("ids"); if(!(raw instanceof List<?> list)||list.isEmpty()||list.size()>100) throw BusError.bad("请选择1至100位乘客");
        List<String> ids=list.stream().map(Object::toString).distinct().sorted().toList();
        String action=text(body,"action",20);
        for(String riderId:ids) {
            one("SELECT id FROM bus_rider WHERE tenant_id=? AND trip_id=? AND id=?",a.tenant(),tripId,riderId);
            if(action.equals("board")) riderAction(a,riderId,"board","");
            else if(action.equals("collect")) collect(a,riderId,text(body,"method",20),hash(text(body,"request_key",60)+riderId));
            else throw BusError.bad("批量操作只支持确认上车或登记收款");
        }
    }
    public List<Map<String,Object>> payments(Actor a,String tripId) {
        owner(a); trip(a.tenant(),tripId,false);
        return rows("SELECT p.*,r.name,b.phone,u.nick_name AS collector FROM bus_payment p JOIN bus_rider r ON r.id=p.rider_id AND r.tenant_id=p.tenant_id JOIN bus_booking b ON b.id=r.booking_id AND b.tenant_id=r.tenant_id LEFT JOIN sys_user u ON u.user_id=p.actor_id AND u.tenant_id=p.tenant_id WHERE p.tenant_id=? AND p.trip_id=? ORDER BY p.created_at DESC,p.id",a.tenant(),tripId);
    }
}
