package com.junzhou.websocket;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {
    private final WebSocketServer sockets;
    private final OrderSocketAuth auth;
    public WebSocketConfiguration(WebSocketServer sockets, OrderSocketAuth auth) { this.sockets = sockets; this.auth = auth; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sockets, "/ws/*").addInterceptors(new OrderSocketHandshake(auth));
    }
}
