package com.picmeup.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FaceSearchRepository extends JpaRepository<FaceSearch, UUID> {

    @Query("""
            SELECT fs.eventId, COUNT(fs), SUM(CASE WHEN fs.resultsCount > 0 THEN 1 ELSE 0 END)
            FROM FaceSearch fs
            GROUP BY fs.eventId
            """)
    List<Object[]> getSearchStatsByEvent();
}
