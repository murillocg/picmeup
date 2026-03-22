package com.picmeup.photo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.picmeup.photo.dto.CreateEventRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createEvent_shouldReturn201() throws Exception {
        var request = new CreateEventRequest("City Marathon", LocalDate.of(2026, 5, 10), "Sydney");

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("City Marathon")))
                .andExpect(jsonPath("$.slug", is("city-marathon-2026-05-10")))
                .andExpect(jsonPath("$.location", is("Sydney")))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createEvent_shouldReturn400WhenNameMissing() throws Exception {
        var request = new CreateEventRequest("", LocalDate.of(2026, 5, 10), "Sydney");

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void getEvent_shouldReturnEventBySlug() throws Exception {
        var request = new CreateEventRequest("Beach Party", LocalDate.of(2026, 6, 15), "Bondi");

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(get("/api/events/beach-party-2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Beach Party")));
    }

    @Test
    void getEvent_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/events/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found: nonexistent"));
    }

    @Test
    void listActiveEvents_shouldReturnNonExpiredEvents() throws Exception {
        var request1 = new CreateEventRequest("Event One", LocalDate.of(2026, 5, 10), "Sydney");
        var request2 = new CreateEventRequest("Event Two", LocalDate.of(2026, 6, 20), "Melbourne");

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)));
        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void createEvent_shouldReturn400WhenDuplicateSlug() throws Exception {
        var request = new CreateEventRequest("Marathon", LocalDate.of(2026, 5, 10), "Sydney");

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An event with this name and date already exists"));
    }
}
