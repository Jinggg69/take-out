package com.junzhou.task;
import com.junzhou.context.BaseContext;
import com.junzhou.context.RequestScope;
import com.junzhou.entity.Orders;
import java.util.function.Supplier;
/** Only scheduled entry points may open a global candidate scan. Business SQL stays scoped. */
public final class BackgroundJobScope {
    private static final ThreadLocal<Boolean> SCANNING = new ThreadLocal<>();
    private BackgroundJobScope() { }
    public static void clear() { RequestScope.clear(); BaseContext.removeCurrentId(); }
    public static boolean requireScanning() {
        if (!Boolean.TRUE.equals(SCANNING.get()) || RequestScope.current() != null)
            throw new SecurityException("Global job scan requires explicit system entry");
        return true;
    }
    public static <T> T scan(Supplier<T> query) {
        if (RequestScope.current() != null) throw new SecurityException("Cannot scan under request scope");
        SCANNING.set(true);
        try { return query.get(); } finally { SCANNING.remove(); }
    }
    public static void forOrder(Orders order, Runnable work) {
        clear();
        try {
            if (order.getId()==null || order.getId()<=0 || order.getTenantId()==null || order.getTenantId()<=0
                    || order.getStoreId()==null || order.getStoreId()<=0) throw new SecurityException("Invalid job ownership");
            RequestScope.set(RequestScope.forSystem(order.getTenantId(),order.getStoreId()));
            work.run();
        } finally { clear(); }
    }
}
