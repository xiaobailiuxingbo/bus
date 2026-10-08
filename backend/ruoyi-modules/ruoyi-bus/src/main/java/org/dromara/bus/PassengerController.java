package org.dromara.bus;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.servlet.http.HttpServletRequest;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@SaIgnore
@RestController
@RequestMapping("/app")
public class PassengerController {
    private final BusService service;
    public PassengerController(BusService service) { this.service=service; }
    @PostMapping("/auth/dev-login") public R<?> demo(@RequestBody Map<String,Object> body,HttpServletRequest request) { return R.ok(service.login(body,request,true)); }
    @PostMapping("/auth/wx-login") public R<?> login(@RequestBody Map<String,Object> body,HttpServletRequest request) { return R.ok(service.login(body,request,false)); }
    @PostMapping("/auth/logout") public R<?> logout(HttpServletRequest request) { service.logout(request); return R.ok(); }
    @GetMapping("/bus/merchant") public R<?> merchant(@RequestParam(defaultValue="qinzhou-demo") String entry) { return R.ok(service.capabilities(entry)); }
    @GetMapping("/bus/trips") public R<?> trips(@RequestParam(defaultValue="qinzhou-demo") String entry,@RequestParam(required=false) String date) { return R.ok(service.publicTrips(entry,date==null?LocalDate.now().toString():date)); }
    @GetMapping("/bus/trips/{id}") public R<?> trip(@RequestParam(defaultValue="qinzhou-demo") String entry,@PathVariable String id) { return R.ok(service.publicTrip(entry,id)); }
    @GetMapping("/bus/stations/{id}") public R<?> station(@RequestParam(defaultValue="qinzhou-demo") String entry,@PathVariable String id) { return R.ok(service.station(entry,id)); }
    @GetMapping("/bus/bookings") public R<?> bookings(HttpServletRequest request) { return R.ok(service.bookings(service.passenger(request))); }
    @GetMapping("/bus/bookings/{id}") public R<?> booking(@PathVariable String id,HttpServletRequest request) { return R.ok(service.booking(service.passenger(request),id)); }
    @PostMapping("/bus/bookings") public R<?> book(@RequestBody Map<String,Object> body,HttpServletRequest request) { return R.ok(service.book(service.passenger(request),body)); }
    @PostMapping("/bus/riders/{id}/{action}") public R<?> action(@PathVariable String id,@PathVariable String action,HttpServletRequest request) { if(!List.of("arrive","cancel").contains(action)) throw BusError.denied(); service.riderAction(service.passenger(request),id,action,""); return R.ok(); }
}
