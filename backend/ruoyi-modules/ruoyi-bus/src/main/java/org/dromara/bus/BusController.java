package org.dromara.bus;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.domain.R;
import org.dromara.bus.map.GeoProvider;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/bus")
public class BusController {
    private final BusService service;
    private final GeoProvider maps;
    public BusController(BusService service,GeoProvider maps) { this.service=service; this.maps=maps; }
    @GetMapping("/dashboard") public R<?> dashboard() { return R.ok(service.dashboard(service.staffActor())); }
    @GetMapping("/trips") public R<?> trips() { return R.ok(service.trips(service.staffActor())); }
    @PostMapping("/trips") public R<?> create(@RequestBody Map<String,Object> body) { return R.ok(service.createTrip(service.staffActor(),body)); }
    @PostMapping("/trips/{id}/staff") public R<?> staff(@PathVariable String id,@RequestBody Map<String,Object> body) { service.assignStaff(service.staffActor(),id,service.array(body.get("staff"))); return R.ok(); }
    @PostMapping("/trips/{id}/{action}") public R<?> action(@PathVariable String id,@PathVariable String action,@RequestBody Map<String,Object> body) { return R.ok(service.changeTrip(service.staffActor(),id,action,Objects.toString(body.get("reason"),""))); }
    @GetMapping("/trips/{id}/riders") public R<?> riders(@PathVariable String id) { return R.ok(service.riders(service.staffActor(),id)); }
    @PostMapping("/trips/{id}/batch") public R<?> batch(@PathVariable String id,@RequestBody Map<String,Object> body) { service.batch(service.staffActor(),id,body); return R.ok(); }
    @GetMapping("/employees") public R<?> employees() { return R.ok(service.employees(service.staffActor())); }
    @GetMapping("/catalog/{type}") public R<?> catalog(@PathVariable String type) { return R.ok(service.catalog(service.staffActor(),type)); }
    @PostMapping("/catalog/{type}") public R<?> save(@PathVariable String type,@RequestBody Map<String,Object> body) { return R.ok(service.saveCatalog(service.staffActor(),type,body)); }
    @PostMapping("/bookings") public R<?> book(@RequestBody Map<String,Object> body) { return R.ok(service.book(service.staffActor(),body)); }
    @PostMapping("/riders/{id}/{action}") public R<?> riderAction(@PathVariable String id,@PathVariable String action,@RequestBody Map<String,Object> body) { service.riderAction(service.staffActor(),id,action,Objects.toString(body.get("reason"),"")); return R.ok(); }
    @PostMapping("/riders/{id}/collect") public R<?> collect(@PathVariable String id,@RequestBody Map<String,Object> body) { service.collect(service.staffActor(),id,BusService.text(body,"method",20),BusService.text(body,"request_key",60)); return R.ok(); }
    @GetMapping("/trips/{id}/payments") public R<?> payments(@PathVariable String id) { return R.ok(service.payments(service.staffActor(),id)); }
    @PostMapping("/payments/{id}/reverse") public R<?> reverse(@PathVariable String id,@RequestBody Map<String,Object> body) { service.reverse(service.staffActor(),id,BusService.text(body,"reason",250)); return R.ok(); }
    @GetMapping("/map/capabilities") public R<?> map() { service.staffActor(); return R.ok(Map.of("enabled",maps.configured(),"coordinate_system","GCJ-02")); }
    @GetMapping("/map/search") public R<?> search(@RequestParam String keyword,@RequestParam(defaultValue="") String city) { service.staffActor(); return R.ok(maps.search(keyword,city)); }
    @GetMapping("/map/geocode") public R<?> geocode(@RequestParam String address,@RequestParam(defaultValue="") String city) { service.staffActor(); return R.ok(maps.geocode(address,city)); }
    @GetMapping("/map/reverse") public R<?> reverseLocation(@RequestParam double latitude,@RequestParam double longitude) { service.staffActor(); if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180) throw BusError.bad("坐标格式错误"); return R.ok(maps.reverse(latitude,longitude)); }
    @GetMapping("/trips/{id}/export") public void export(@PathVariable String id,HttpServletResponse response) throws Exception {
        BusService.Actor actor=service.staffActor(); if(!actor.owner()) throw BusError.denied();
        List<Map<String,Object>> rows=service.riders(actor,id);
        response.setContentType("text/csv;charset=UTF-8"); response.setHeader("Content-Disposition","attachment; filename=bus-manifest.csv");
        StringBuilder csv=new StringBuilder("\uFEFF姓名,联系人,手机号,状态,票价元,是否收款\r\n");
        for(Map<String,Object> row:rows) {
            for(String field:List.of("name","contact_name","phone","status")) csv.append(cell(Objects.toString(row.get(field),""))).append(',');
            csv.append(((Number)row.get("fare_cents")).intValue()/100.0).append(',').append(Boolean.TRUE.equals(row.get("paid"))?"已收":"未收").append("\r\n");
        }
        response.getOutputStream().write(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private String cell(String text) { if(text.matches("^[=+\\-@\\t\\r].*")) text="'"+text; return "\""+text.replace("\"","\"\"")+"\""; }
    @GetMapping("/trips/{id}/payments/export") public void exportPayments(@PathVariable String id,HttpServletResponse response) throws Exception {
        List<Map<String,Object>> payments=service.payments(service.staffActor(),id);
        response.setContentType("text/csv;charset=UTF-8"); response.setHeader("Content-Disposition","attachment; filename=bus-payments.csv");
        StringBuilder csv=new StringBuilder("\uFEFF流水编号,乘车人,手机号,收款人,方式,金额元,原流水编号,原因,时间\r\n");
        for(Map<String,Object> p:payments) {
            for(String field:List.of("id","name","phone","collector","method")) csv.append(cell(Objects.toString(p.get(field),""))).append(',');
            csv.append(((Number)p.get("amount_cents")).intValue()/100.0).append(',');
            for(String field:List.of("reverses_id","reason","created_at")) csv.append(cell(Objects.toString(p.get(field),""))).append(field.equals("created_at")?"\r\n":",");
        }
        response.getOutputStream().write(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
}
