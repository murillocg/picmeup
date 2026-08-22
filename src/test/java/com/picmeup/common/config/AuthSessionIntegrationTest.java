package com.picmeup.common.config;

import com.picmeup.common.dto.LoginRequest;
import com.picmeup.photo.dto.CreateEventRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the admin session over real HTTP the way the browser does: the CSRF cookie the SPA
 * echoes back as a header, the session cookie, and logout actually ending the session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthSessionIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void sessionLifecycle_shouldAuthenticateAdminAndEndOnLogout() {
        // The SPA's first call hands out the CSRF cookie it later echoes back.
        var check = restTemplate.getForEntity("/api/auth/check", Map.class);
        assertThat(check.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(check.getBody()).containsEntry("authenticated", false);

        String csrfToken = cookieValue(check.getHeaders(), "XSRF-TOKEN");
        assertThat(csrfToken).isNotNull();

        var login = restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("admin", "admin123"), jsonHeaders(csrfToken, null)),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).containsEntry("authenticated", true);

        String sessionId = cookieValue(login.getHeaders(), "JSESSIONID");
        assertThat(sessionId).isNotNull();

        // An admin write succeeds with the session cookie plus the CSRF header.
        var event = new CreateEventRequest("Session Test", LocalDate.of(2026, 7, 1), "Sydney",
                new BigDecimal("20.00"), new BigDecimal("65.00"), false, false);
        var created = restTemplate.exchange("/api/events", HttpMethod.POST,
                new HttpEntity<>(event, jsonHeaders(csrfToken, sessionId)), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // ...and is rejected when the CSRF header is missing, as a cross-site request would be.
        var withoutCsrf = jsonHeaders(null, sessionId);
        withoutCsrf.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        var forbidden = restTemplate.exchange("/api/events", HttpMethod.POST,
                new HttpEntity<>(event, withoutCsrf), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var logout = restTemplate.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(null, jsonHeaders(csrfToken, sessionId)), String.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);

        // The old session cookie is dead: this is the refresh that used to stay logged in.
        var afterLogout = restTemplate.exchange("/api/auth/check", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders(csrfToken, sessionId)), Map.class);
        assertThat(afterLogout.getBody()).containsEntry("authenticated", false);

        var adminAfterLogout = restTemplate.exchange("/api/orders", HttpMethod.GET,
                new HttpEntity<>(null, jsonHeaders(csrfToken, sessionId)), String.class);
        assertThat(adminAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpHeaders jsonHeaders(String csrfToken, String sessionId) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (csrfToken != null) {
            headers.add("X-XSRF-TOKEN", csrfToken);
            headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        }
        if (sessionId != null) {
            headers.add(HttpHeaders.COOKIE, "JSESSIONID=" + sessionId);
        }
        return headers;
    }

    private String cookieValue(HttpHeaders headers, String name) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            return null;
        }
        return cookies.stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .map(cookie -> cookie.substring(name.length() + 1).split(";", 2)[0])
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse(null);
    }
}
