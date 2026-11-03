package com.junzhou.websocket;

import com.junzhou.context.RequestScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Connections subscribe only to the scope authorised by the handshake. */
@Component
public class WebSocketServer extends TextWebSocketHandler implements SubProtocolCapable {
    @Autowired(required = false) private OrderSocketAuth auth;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    @Override public List<String> getSubProtocols() { return Collections.singletonList("junzhou-orders"); }
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!valid(session)) { session.close(CloseStatus.POLICY_VIOLATION); return; }
        sessions.put(session.getId(), session);
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId(), session);
    }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) throws Exception {
        sessions.remove(session.getId(), session);
        session.close(CloseStatus.SERVER_ERROR);
    }
    private boolean valid(WebSocketSession session) {
        try {
            if (auth == null) return false;
            Map<String,Object> a = session.getAttributes();
            RequestScope scope = auth.authenticate((String)a.get("orderToken"), (Long)a.get("orderStore"));
            return Objects.equals(scope.getTenantId(), a.get("orderTenant"))
                    && Objects.equals(scope.getStoreId(), a.get("orderStore"));
        } catch (RuntimeException ex) { return false; }
    }
    public void sendToStore(Long tenant, Long store, String message) {
        if (tenant == null || tenant <= 0 || store == null || store <= 0)
            throw new SecurityException("Missing order ownership");
        for (WebSocketSession session : sessions.values()) {
            try {
                if (!session.isOpen() || !valid(session)) {
                    sessions.remove(session.getId(), session);
                    session.close(CloseStatus.POLICY_VIOLATION);
                    continue;
                }
                if (tenant.equals(session.getAttributes().get("orderTenant"))
                        && store.equals(session.getAttributes().get("orderStore"))) {
                    synchronized (session) { session.sendMessage(new TextMessage(message)); }
                }
            } catch (Exception ex) {
                sessions.remove(session.getId(), session);
                try { session.close(CloseStatus.SERVER_ERROR); } catch (Exception ignored) { }
            }
        }
    }
}
