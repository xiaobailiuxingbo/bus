package org.dromara.bus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class BusServiceTest {
    private JdbcTemplate db;
    private BusService service;
    private MockEnvironment env;
    private final BusService.Actor owner=new BusService.Actor("t","1",true,false);
    private final BusService.Actor passenger=new BusService.Actor("t","p1",false,true);
    private String route,vehicle,from,to,trip;
    @BeforeEach void setup() throws Exception {
        DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000","sa","");
        db=new JdbcTemplate(ds);
        String migration=Files.readString(Path.of("../../script/sql/bus/V1__bus.sql")).split("-- Business menus")[0];
        for(String sql:migration.split(";")) if(!sql.isBlank()) db.execute(sql);
        db.execute("CREATE TABLE sys_user(user_id bigint,tenant_id varchar(20),user_name varchar(80),nick_name varchar(80),status varchar(2),del_flag varchar(2))");
        db.update("INSERT INTO bus_merchant VALUES ('t','test','测试商家','13800000000',true,true)");
        env=new MockEnvironment().withProperty("bus.demo-login-enabled","true"); env.setActiveProfiles("dev");
        BusService target=new BusService(db,new ObjectMapper(),env); ProxyFactory factory=new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource())); service=(BusService)factory.getProxy();
        from=service.saveCatalog(owner,"stations",Map.of("name","上车点","address","测试地址")).get("id").toString();
        to=service.saveCatalog(owner,"stations",Map.of("name","下车点","address","测试地址")).get("id").toString();
        vehicle=service.saveCatalog(owner,"vehicles",Map.of("name","测试车","plate","桂N测试","capacity",3)).get("id").toString();
        route=service.saveCatalog(owner,"routes",routeBody(5000)).get("id").toString();
        trip=service.createTrip(owner,Map.of("route_id",route,"vehicle_id",vehicle,"depart_at",LocalDateTime.now().plusHours(2).toString(),"capacity",3)).get("id").toString();
        service.changeTrip(owner,trip,"publish","");
    }
    private Map<String,Object> routeBody(int fare) { return Map.of("name","测试线路","origin","钦州","destination","南宁","stops",List.of(Map.of("station_id",from,"offset_minutes",0),Map.of("station_id",to,"offset_minutes",120)),"fares",List.of(Map.of("from",from,"to",to,"cents",fare))); }
    private Map<String,Object> request(int people,String key) {
        List<Map<String,Object>> riders=new ArrayList<>(); for(int i=0;i<people;i++) riders.add(Map.of("name","乘客"+i,"board_station",from,"alight_station",to));
        return Map.of("trip_id",trip,"contact_name","联系人","phone","13800000000","request_key",key,"riders",riders);
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> bookedRiders(Map<String,Object> booking) { return (List<Map<String,Object>>)booking.get("riders"); }
    private long occupied() { return db.queryForObject("SELECT occupied FROM bus_trip WHERE id=?",Long.class,trip); }
    @Test void groupCancellationPaymentAndReversalStayConsistent() {
        Map<String,Object> b=service.book(passenger,request(3,"group")); assertEquals(3,occupied());
        assertEquals(b.get("id"),service.book(passenger,request(3,"group")).get("id")); assertEquals(3,occupied());
        var riders=bookedRiders(b); String first=riders.get(0).get("id").toString(),second=riders.get(1).get("id").toString();
        service.riderAction(passenger,first,"arrive",""); assertEquals("ARRIVED",service.booking(passenger,b.get("id").toString()).get("riders") instanceof List<?> ? db.queryForObject("SELECT status FROM bus_rider WHERE id=?",String.class,first) : "");
        service.riderAction(owner,first,"board",""); service.riderAction(passenger,second,"cancel",""); service.riderAction(passenger,second,"cancel",""); assertEquals(2,occupied());
        service.collect(owner,first,"CASH","pay1"); service.collect(owner,first,"CASH","pay1"); service.collect(owner,first,"WECHAT","pay2");
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM bus_payment",Integer.class));
        assertThrows(BusError.class,()->service.riderAction(passenger,first,"cancel",""));
        String payment=service.payments(owner,trip).get(0).get("id").toString(); service.reverse(owner,payment,"登记方式错误"); service.reverse(owner,payment,"登记方式错误");
        assertEquals(0,db.queryForObject("SELECT SUM(amount_cents) FROM bus_payment",Integer.class));
        service.collect(owner,first,"WECHAT","pay3"); assertEquals(5000,db.queryForObject("SELECT SUM(amount_cents) FROM bus_payment",Integer.class));
        Map<String,Object> summary=service.summary(service.riders(owner,trip)); assertEquals(5000L,summary.get("paid_cents")); assertEquals(5000L,summary.get("unpaid_cents"));
    }
    @Test void concurrentBookingsNeverOversell() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> attempts=new ArrayList<>(); for(int i=0;i<12;i++) { final int n=i; attempts.add(()->{try{service.book(passenger,request(1,"race"+n));return true;}catch(BusError e){assertEquals(409,e.code);return false;}}); }
            long successes=0; for(Future<Boolean> f:pool.invokeAll(attempts)) if(f.get()) successes++;
            assertEquals(3,successes); assertEquals(3,occupied()); assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM bus_rider",Integer.class));
        } finally { pool.shutdownNow(); }
    }
    @Test void pricesAreSnapshotsAndInvalidOrdersRollback() {
        Map<String,Object> updated=new LinkedHashMap<>(routeBody(8000)); updated.put("id",route); service.saveCatalog(owner,"routes",updated);
        var booking=service.book(passenger,request(1,"snapshot")); assertEquals(5000,((Number)bookedRiders(booking).get(0).get("fare_cents")).intValue());
        assertThrows(BusError.class,()->service.book(passenger,request(7,"too-many"))); assertEquals(1,occupied());
        assertThrows(BusError.class,()->service.book(passenger,request(2,"snapshot"))); assertEquals(1,occupied());
    }
    @Test void actorScopeAndOwnershipCannotBeBypassed() {
        var booking=service.book(passenger,request(1,"scope")); String bookingId=booking.get("id").toString(),riderId=bookedRiders(booking).get(0).get("id").toString();
        assertThrows(BusError.class,()->service.booking(new BusService.Actor("t","p2",false,true),bookingId));
        assertThrows(BusError.class,()->service.booking(new BusService.Actor("other","p1",false,true),bookingId));
        var staff=new BusService.Actor("t","9",false,false); assertThrows(BusError.class,()->service.riderAction(staff,riderId,"board",""));
        db.update("INSERT INTO bus_trip_staff VALUES (?,?,?,?)","t",trip,9,to);
        assertEquals(0,service.riders(staff,trip).size()); assertThrows(BusError.class,()->service.collect(staff,riderId,"CASH","bad-scope"));
        db.update("INSERT INTO bus_trip_staff VALUES (?,?,?,?)","t",trip,9,from); service.riderAction(staff,riderId,"board","");
        assertThrows(BusError.class,()->service.reverse(staff,"irrelevant","not-owner"));
    }
    @Test void demoLoginIsDisabledInProductionAndExpiredSessionsFail() {
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");env.setActiveProfiles("prod");
        assertThrows(BusError.class,()->service.login(Map.of("entry","test","index",1),request,true));
        request.addHeader("X-Passenger-Token","expired"); db.update("INSERT INTO bus_session VALUES (?,?,?,?)",BusService.hash("expired"),"t","p1",LocalDateTime.now().minusHours(1));
        assertThrows(BusError.class,()->service.passenger(request));
    }
    @Test void finishedTripTracksMissedRidersWithoutFreeingInventory() {
        service.book(passenger,request(2,"finish")); service.changeTrip(owner,trip,"finish","");
        assertEquals(2L,service.summary(service.riders(owner,trip)).get("missed")); assertEquals(2,occupied());
        assertThrows(BusError.class,()->service.book(passenger,request(1,"late")));
    }
    @Test void batchOperationsAreAtomicAndPerPerson() {
        var booking=service.book(passenger,request(2,"batch")); var ids=bookedRiders(booking).stream().map(r->r.get("id").toString()).toList();
        assertThrows(BusError.class,()->service.batch(owner,trip,Map.of("ids",List.of(ids.get(0),"missing"),"action","collect","method","CASH","request_key","invalid")));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM bus_payment",Integer.class));
        var command=Map.<String,Object>of("ids",ids,"action","collect","method","CASH","request_key","batch-ok"); service.batch(owner,trip,command); service.batch(owner,trip,command);
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM bus_payment",Integer.class));
        service.batch(owner,trip,Map.of("ids",ids,"action","board")); assertEquals(2L,service.summary(service.riders(owner,trip)).get("boarded"));
    }
}
