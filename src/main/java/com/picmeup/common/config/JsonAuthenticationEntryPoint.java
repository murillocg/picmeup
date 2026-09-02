package com.picmeup.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Answers unauthenticated requests in JSON, so the SPA never has to parse an HTML error.
 *
 * <p>Distinguishes two situations the caller must tell apart: no usable identity (401),
 * and a proven identity that has no access here (403). The second carries the reason and
 * the address actually used, so the UI can say "you signed in as X, which has no access"
 * instead of bouncing someone back to a sign-in they just completed successfully.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        if (authException instanceof AccessNotGrantedException denied) {
            // Values come from a JWT claim, so they are serialised rather than
            // concatenated — an apostrophe or quote in an address must not be able to
            // produce malformed JSON.
            var body = new LinkedHashMap<String, Object>();
            body.put("status", HttpStatus.FORBIDDEN.value());
            body.put("reason", denied.getReason().name());
            body.put("email", denied.getEmail());
            body.put("message", denied.getMessage());
            write(response, HttpStatus.FORBIDDEN, body);
            return;
        }

        write(response, HttpStatus.UNAUTHORIZED,
                Map.of("status", HttpStatus.UNAUTHORIZED.value(), "message", "Unauthorized"));
    }

    private void write(HttpServletResponse response, HttpStatus status, Map<String, Object> body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
