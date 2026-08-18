package com.picmeup.photo;

import com.picmeup.photo.dto.PlatformUsageResponse;
import com.picmeup.photo.dto.SearchStatsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/stats")
public class SearchStatsController {

    private final FaceSearchRepository faceSearchRepository;
    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final S3StorageService s3StorageService;

    public SearchStatsController(FaceSearchRepository faceSearchRepository,
                                 EventRepository eventRepository,
                                 PhotoRepository photoRepository,
                                 S3StorageService s3StorageService) {
        this.faceSearchRepository = faceSearchRepository;
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
        this.s3StorageService = s3StorageService;
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

    @GetMapping("/usage")
    public ResponseEntity<PlatformUsageResponse> getUsage() {
        var startOfMonth = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay();

        long originalsBytes = s3StorageService.getBytesUsedByPrefix("originals/");
        long thumbnailsBytes = s3StorageService.getBytesUsedByPrefix("thumbnails/");
        long totalBytes = originalsBytes + thumbnailsBytes;

        long totalEvents = eventRepository.count();
        long totalPhotos = photoRepository.count();

        long facesIndexedThisMonth = photoRepository.countWithIndexedFacesSince(startOfMonth);
        long searchesThisMonth = faceSearchRepository.countBySearchedAtAfter(startOfMonth);
        long facesIndexedAllTime = photoRepository.countWithIndexedFaces();
        long searchesAllTime = faceSearchRepository.count();

        var response = new PlatformUsageResponse(
                totalEvents,
                totalPhotos,
                new PlatformUsageResponse.StorageUsage(totalBytes, originalsBytes, thumbnailsBytes),
                new PlatformUsageResponse.FacialRecognitionUsage(
                        facesIndexedThisMonth, searchesThisMonth,
                        facesIndexedAllTime, searchesAllTime
                )
        );

        return ResponseEntity.ok(response);
    }
}
