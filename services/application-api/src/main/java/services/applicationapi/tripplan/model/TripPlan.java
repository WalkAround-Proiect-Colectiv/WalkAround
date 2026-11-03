package services.applicationapi.tripplan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import services.applicationapi.user.model.User;

@Entity
@Table(name = "trip_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPlan {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 3)
    private String originAirport;

    @Column(nullable = false, length = 100)
    private String departureCity;

    @Column(nullable = false, length = 2)
    private String departureCountryCode;

    @Column(nullable = false, length = 100)
    private String destinationCity;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false)
    private LocalDateTime departureFrom;

    @Column(nullable = false)
    private LocalDateTime departureUntil;

    @Column(nullable = false)
    private int stayingNights;

    @Column(nullable = false, length = 16)
    private String ticketOption;

    @Column(nullable = false, precision = 11, scale = 2)
    private BigDecimal flightBudget;

    @Column(nullable = false, precision = 11, scale = 2)
    private BigDecimal accommodationBudget;

    @Column(nullable = false)
    private Instant createdAt;

    public TripPlan(
            User user,
            String title,
            String description,
            String originAirport,
            String departureCity,
            String departureCountryCode,
            String destinationCity,
            String countryCode,
            LocalDateTime departureFrom,
            LocalDateTime departureUntil,
            int stayingNights,
            String ticketOption,
            BigDecimal flightBudget,
            BigDecimal accommodationBudget
    ) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.title = title;
        this.description = description;
        this.originAirport = originAirport;
        this.departureCity = departureCity;
        this.departureCountryCode = departureCountryCode;
        this.destinationCity = destinationCity;
        this.countryCode = countryCode;
        this.departureFrom = departureFrom;
        this.departureUntil = departureUntil;
        this.stayingNights = stayingNights;
        this.ticketOption = ticketOption;
        this.flightBudget = flightBudget;
        this.accommodationBudget = accommodationBudget;
        this.createdAt = Instant.now();
    }
}
