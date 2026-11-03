package com.junzhou.websocket;

import com.junzhou.context.RequestScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;

public class OrderSocketHandshake implements HandshakeInterceptor {
    private final OrderSocketAuth auth;
    public OrderSocketHandshake(OrderSocketAuth auth) { this.auth = auth; }
    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String,Object> attributes) {
        try {
            String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
            String token = null;
            if (protocols != null) for (String p : protocols.split(","))
                if (p.trim().startsWith("auth.")) token = p.trim().substring(5);
            String selected = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("storeId");
            RequestScope scope = auth.authenticate(token, selected == null ? null : Long.valueOf(selected));
            attributes.put("orderToken", token);
            attributes.put("orderTenant", scope.getTenantId());
            attributes.put("orderStore", scope.getStoreId());
            return true;
        } catch (RuntimeException ex) { response.setStatusCode(HttpStatus.FORBIDDEN); return false; }
    }
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) { }
}
