package com.picmeup.photo;

import com.picmeup.photo.dto.SearchStatsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/stats")
public class SearchStatsController {

    private final FaceSearchRepository faceSearchRepository;
    private final EventRepository eventRepository;

    public SearchStatsController(FaceSearchRepository faceSearchRepository, EventRepository eventRepository) {
        this.faceSearchRepository = faceSearchRepository;
        this.eventRepository = eventRepository;
    }

    @GetMapping("/searches")
    public ResponseEntity<List<SearchStatsResponse>> getSearchStats() {
        var rawStats = faceSearchRepository.getSearchStatsByEvent();
        var stats = rawStats.stream()
                .map(row -> {
                    UUID eventId = (UUID) row[0];
                    long totalSearches = (long) row[1];
                    long searchesWithResults = (long) row[2];
                    var event = eventRepository.findById(eventId).orElse(null);
                    return new SearchStatsResponse(
                            eventId,
                            event != null ? event.getName() : "Deleted event",
                            event != null ? event.getSlug() : null,
                            totalSearches,
                            searchesWithResults
                    );
                })
                .toList();
        return ResponseEntity.ok(stats);
    }
}
