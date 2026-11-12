package com.junzhou.interceptor;

import com.junzhou.json.JacksonObjectMapper;
import com.junzhou.result.Result;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

final class MiniappResponses {
    private MiniappResponses() { }
    static void error(HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        try { new JacksonObjectMapper().writeValue(response.getWriter(), Result.error(message)); }
        catch (IOException ex) { throw new IllegalStateException("Unable to write API response",ex); }
    }
}
