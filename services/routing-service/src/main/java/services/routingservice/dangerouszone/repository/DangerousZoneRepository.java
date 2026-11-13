package services.routingservice.dangerouszone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import services.routingservice.dangerouszone.model.DangerousZone;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DangerousZoneRepository extends JpaRepository<DangerousZone, UUID>
{
    /**
     * Distinct H3 cells whose centre lies inside the box and that have at least one report active at the given
     * time of day. Reports that cross midnight (start > end) are handled; reports that are not 24/7 but have no
     * start or end time are treated as always active.
     */
    @Query("""
            SELECT DISTINCT z.h3Index FROM DangerousZone z
            WHERE z.centerLat BETWEEN :minLat AND :maxLat
              AND z.centerLng BETWEEN :minLng AND :maxLng
              AND (z.is24_7 = true
                   OR z.startTime IS NULL
                   OR z.endTime IS NULL
                   OR (z.startTime <= z.endTime AND :currentTime BETWEEN z.startTime AND z.endTime)
                   OR (z.startTime > z.endTime AND (:currentTime >= z.startTime OR :currentTime <= z.endTime)))
            """)
    Set<String> findDangerCells(@Param("minLat") double minLat,
                                @Param("maxLat") double maxLat,
                                @Param("minLng") double minLng,
                                @Param("maxLng") double maxLng,
                                @Param("currentTime") LocalTime currentTime);
}