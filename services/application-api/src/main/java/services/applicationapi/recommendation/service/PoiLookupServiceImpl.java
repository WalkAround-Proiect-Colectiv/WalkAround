package services.applicationapi.recommendation.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import services.applicationapi.recommendation.domain.ActivityCategoryRule;
import services.applicationapi.recommendation.domain.PoiCandidate;
import services.applicationapi.recommendation.repository.PoiRepository;

@Service
public class PoiLookupServiceImpl implements PoiLookupService {

    private final PoiRepository poiRepository;

    public PoiLookupServiceImpl(PoiRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

    @Override
    public List<PoiCandidate> findNearbyPois(
            double latitude,
            double longitude,
            int radiusMeters,
            int limit,
            List<ActivityCategoryRule> categories
    ) {
        if (categories == null || categories.isEmpty() || radiusMeters <= 0 || limit <= 0) {
            return List.of();
        }

        Set<String> poiClasses = new LinkedHashSet<>();
        Set<String> landcoverClasses = new LinkedHashSet<>();
        Set<String> waterClasses = new LinkedHashSet<>();

        for (ActivityCategoryRule category : categories) {
            parseOsmFilter(category.osmFilter(), poiClasses, landcoverClasses, waterClasses);
        }

        int fetchLimit = Math.min(200, Math.max(limit * 3, 30));
        List<PoiCandidate> rawCandidates = poiRepository.findNearbyPois(
                latitude,
                longitude,
                radiusMeters,
                fetchLimit,
                poiClasses,
                landcoverClasses,
                waterClasses
        );

        if (rawCandidates == null || rawCandidates.isEmpty()) {
            return List.of();
        }

        Map<String, PoiCandidate> deduplicated = new LinkedHashMap<>();
        for (PoiCandidate candidate : rawCandidates) {
            if (candidate == null || candidate.name() == null || candidate.name().isBlank()) {
                continue;
            }
            if (candidate.distanceMeters() > radiusMeters) {
                continue;
            }
            String dedupeKey = candidate.osmKind().toLowerCase(Locale.ROOT)
                    + ":" + candidate.osmClass().toLowerCase(Locale.ROOT)
                    + ":" + candidate.name().trim().toLowerCase(Locale.ROOT);
            deduplicated.putIfAbsent(dedupeKey, candidate);
            if (deduplicated.size() >= fetchLimit) {
                break;
            }
        }

        return List.copyOf(deduplicated.values());
    }

    public static void parseOsmFilter(
            String osmFilter,
            Set<String> poiClasses,
            Set<String> landcoverClasses,
            Set<String> waterClasses
    ) {
        if (osmFilter == null || osmFilter.isBlank()) {
            return;
        }
        String[] parts = osmFilter.split(":", 2);
        if (parts.length != 2) {
            return;
        }
        String kind = parts[0].trim().toLowerCase(Locale.ROOT);
        String[] classes = parts[1].split(",");
        for (String cls : classes) {
            String trimmed = cls.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                continue;
            }
            switch (kind) {
                case "poi" -> poiClasses.add(trimmed);
                case "landcover" -> landcoverClasses.add(trimmed);
                case "water" -> waterClasses.add(trimmed);
                default -> { }
            }
        }
    }

    public static boolean matchesOsmFilter(String osmFilter, String osmKind, String osmClass) {
        if (osmFilter == null || osmKind == null || osmClass == null) {
            return false;
        }
        String[] parts = osmFilter.split(":", 2);
        if (parts.length != 2 || !parts[0].trim().equalsIgnoreCase(osmKind.trim())) {
            return false;
        }
        for (String cls : parts[1].split(",")) {
            if (cls.trim().equalsIgnoreCase(osmClass.trim())) {
                return true;
            }
        }
        return false;
    }
}
