package com.junzhou.ratelimit;

import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

@Component
public class ClientIpResolver {
    /** Forwarding headers are deliberately ignored unless trusted proxy handling is configured upstream. */
    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isEmpty() ? "unknown" : remoteAddr;
    }
}
