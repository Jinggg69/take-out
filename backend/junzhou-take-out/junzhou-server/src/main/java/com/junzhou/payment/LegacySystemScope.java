package com.junzhou.payment;

import com.junzhou.context.RequestScope;
import com.junzhou.service.TenantScopeService;
import org.springframework.stereotype.Component;

/** Temporary bridge for verified notifications and compatibility callers.
 * Scheduled jobs carry per-order scope; payment account routing still requires stage 2.6. */
@Component
public final class LegacySystemScope {
    public interface Scope extends AutoCloseable { @Override void close(); }
    private final TenantScopeService scopes;
    public LegacySystemScope(TenantScopeService scopes) { this.scopes=scopes; }
    public Scope enter() {
        scopes.requireLegacySingleStore();
        RequestScope previous=RequestScope.current();
        RequestScope.set(RequestScope.forSystem(1L,1L));
        return () -> { if(previous==null) RequestScope.clear(); else RequestScope.set(previous); };
    }
}
