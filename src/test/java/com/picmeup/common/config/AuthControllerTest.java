package com.picmeup.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.picmeup.common.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void check_shouldReportUnauthenticatedWithoutSession() throws Exception {
        mockMvc.perform(get("/api/auth/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(false)));
    }

    @Test
    void login_shouldRejectWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authenticated", is(false)));
    }

    @Test
    void login_shouldCreateSessionThatAuthenticatesLaterRequests() throws Exception {
        var session = login();

        mockMvc.perform(get("/api/auth/check").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.username", is("admin")));
    }

    @Test
    void logout_shouldEndTheSession() throws Exception {
        var session = login();

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isOk());

        assertThat(session.isInvalid()).isTrue();

        mockMvc.perform(get("/api/auth/check").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(false)));
    }

    @Test
    void adminEndpoint_shouldRejectRequestsAfterLogout() throws Exception {
        var session = login();

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders").session(session))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andReturn();

        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }
}
