package org.dromara.bus.map;

import java.util.List;

/** Server-side boundary for future AMap integration. Never return guessed coordinates. */
public interface GeoProvider {
    record Point(String name,String address,double latitude,double longitude) {}
    boolean configured();
    List<Point> search(String keyword,String city);
    Point geocode(String address,String city);
    Point reverse(double latitude,double longitude);
}
