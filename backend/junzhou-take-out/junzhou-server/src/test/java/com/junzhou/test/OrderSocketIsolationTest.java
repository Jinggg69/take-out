package com.junzhou.test;
import com.junzhou.websocket.*;
import com.junzhou.context.RequestScope;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OrderSocketIsolationTest {
    @Test void handshakeDerivesTenantFromAuthenticatedScopeAndRejectsForgedStore() {
        OrderSocketAuth auth = mock(OrderSocketAuth.class);
        when(auth.authenticate("signed", 1001L)).thenReturn(new RequestScope(42L,null,101L,1001L,Collections.singleton(1001L)));
        when(auth.authenticate("signed", 2001L)).thenThrow(new SecurityException());
        OrderSocketHandshake handshake = new OrderSocketHandshake(auth);
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/ws/untrusted");
        request.setQueryString("storeId=1001&tenantId=102");
        request.addHeader("Sec-WebSocket-Protocol", "junzhou-orders, auth.signed");
        Map<String,Object> attributes = new HashMap<>();
        assertTrue(handshake.beforeHandshake(new org.springframework.http.server.ServletServerHttpRequest(request),
                new org.springframework.http.server.ServletServerHttpResponse(new org.springframework.mock.web.MockHttpServletResponse()), new WebSocketServer(), attributes));
        assertEquals(101L, attributes.get("orderTenant"));
        request.setQueryString("storeId=2001");
        assertFalse(handshake.beforeHandshake(new org.springframework.http.server.ServletServerHttpRequest(request),
                new org.springframework.http.server.ServletServerHttpResponse(new org.springframework.mock.web.MockHttpServletResponse()), new WebSocketServer(), new HashMap<>()));
    }

    @Test void authenticationRejectsRevokedAndForgedTokens() {
        com.junzhou.properties.JwtProperties jwt = new com.junzhou.properties.JwtProperties();
        jwt.setAdminSecretKey("socket-test-secret");
        com.junzhou.service.AdminSessionService sessions = mock(com.junzhou.service.AdminSessionService.class);
        com.junzhou.service.TenantScopeService scopes = mock(com.junzhou.service.TenantScopeService.class);
        OrderSocketAuth auth = new OrderSocketAuth(jwt, sessions, scopes);
        com.junzhou.service.RbacService rbac=mock(com.junzhou.service.RbacService.class);
        ReflectionTestUtils.setField(auth,"rbac",rbac);
        when(rbac.permits(101L,42L,1001L,com.junzhou.security.PermissionAction.ORDER_READ)).thenReturn(true);
        Map<String,Object> claims = new HashMap<>(); claims.put("empId",42L); claims.put("jti","active");
        String token = com.junzhou.utils.JwtUtil.createJWT("socket-test-secret",60000,claims);
        when(sessions.isActive("active",42L)).thenReturn(true);
        RequestScope expected = new RequestScope(42L,null,101L,1001L,Collections.singleton(1001L));
        when(scopes.forEmployee(42L,1001L)).thenReturn(expected);
        assertSame(expected, auth.authenticate(token,1001L));
        when(sessions.isActive("active",42L)).thenReturn(false);
        assertThrows(SecurityException.class, () -> auth.authenticate(token,1001L));
        assertThrows(RuntimeException.class, () -> auth.authenticate("forged",1001L));
    }
    @Test void anonymousConnectionMustBeRejected() throws Exception {
        WebSocketServer server = new WebSocketServer();
        WebSocketSession session = mock(WebSocketSession.class);
        server.afterConnectionEstablished(session);
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }
    @Test void deliveriesStayWithinOrderStoreAndRevokedSessionsStopReceiving() throws Exception {
        WebSocketServer server = new WebSocketServer();
        OrderSocketAuth auth = mock(OrderSocketAuth.class);
        ReflectionTestUtils.setField(server, "auth", auth);
        WebSocketSession a = connection(server, auth, "a", 101L, 1001L);
        WebSocketSession a2 = connection(server, auth, "a2", 101L, 1002L);
        WebSocketSession b = connection(server, auth, "b", 102L, 1001L);
        server.sendToStore(101L, 1001L, "order");
        verify(a).sendMessage(any(TextMessage.class));
        verify(a2, never()).sendMessage(any()); verify(b, never()).sendMessage(any());
        when(auth.authenticate("a", 1001L)).thenThrow(new SecurityException());
        server.sendToStore(101L, 1001L, "another");
        verify(a, times(1)).sendMessage(any());
        verify(a).close(CloseStatus.POLICY_VIOLATION);
    }
    private WebSocketSession connection(WebSocketServer server, OrderSocketAuth auth, String id, Long tenant, Long store) throws Exception {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id); when(s.isOpen()).thenReturn(true);
        Map<String,Object> attrs = new HashMap<>(); attrs.put("orderToken", id); attrs.put("orderTenant", tenant); attrs.put("orderStore", store);
        when(s.getAttributes()).thenReturn(attrs);
        when(auth.authenticate(id, store)).thenReturn(new RequestScope(42L, null, tenant, store, Collections.singleton(store)));
        server.afterConnectionEstablished(s); return s;
    }
}
