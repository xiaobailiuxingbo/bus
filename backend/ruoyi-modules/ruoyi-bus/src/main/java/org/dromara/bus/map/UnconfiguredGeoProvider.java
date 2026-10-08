package org.dromara.bus.map;
import org.dromara.bus.BusError;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class UnconfiguredGeoProvider implements GeoProvider {
    public boolean configured() { return false; }
    public List<Point> search(String keyword,String city) { throw unavailable(); }
    public Point geocode(String address,String city) { throw unavailable(); }
    public Point reverse(double latitude,double longitude) { throw unavailable(); }
    private BusError unavailable() { return new BusError(503,"地图服务未配置"); }
}
