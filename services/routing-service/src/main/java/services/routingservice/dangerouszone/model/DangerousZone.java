package services.routingservice.dangerouszone.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "dangerous_zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DangerousZone
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "h3_index", nullable = false, length = 15)
    private String h3Index;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DangerousZoneCategory category;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "is_24_7")
    private boolean is24_7;
    @Column(name = "start_time")
    private LocalTime startTime;
    @Column(name = "end_time")
    private LocalTime endTime;
    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;
    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;
}