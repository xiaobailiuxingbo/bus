package org.dromara.bus;

public class BusError extends RuntimeException {
    public final int code;
    public BusError(int code, String message) { super(message); this.code = code; }
    public static BusError bad(String message) { return new BusError(400, message); }
    public static BusError denied() { return new BusError(403, "无权访问此商家、班次或乘客记录"); }
}
