package com.junzhou.payment;

import com.junzhou.context.RequestScope;
import com.junzhou.service.TenantScopeService;
import org.springframework.stereotype.Component;

/** Temporary, explicit bridge for verified notifications and scheduled jobs only.
 * Refuses databases containing any additional tenant/store until stage 2.5/2.6 routing exists. */
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
