package services.routingservice.dangerouszone.service;

import com.uber.h3core.H3Core;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import services.routingservice.dangerouszone.dto.DangerousZoneHeatmapResponse;
import services.routingservice.dangerouszone.dto.DangerousZoneRequest;
import services.routingservice.dangerouszone.model.DangerousZone;
import services.routingservice.dangerouszone.repository.DangerousZoneRepository;


import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DangerousZoneService
{
    private final DangerousZoneRepository repository;
    private final H3Core h3;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    public DangerousZoneService(DangerousZoneRepository repository,
                                KafkaTemplate<String, Object> kafkaTemplate) throws IOException {
        this.repository = repository;
        this.h3 = H3Core.newInstance();
        this.kafkaTemplate = kafkaTemplate;
    }
    public void reportZone(DangerousZoneRequest request, UUID userId)
    {
        String h3Index = h3.latLngToCellAddress(request.lat(), request.lng(), 10);
        DangerousZone zone = DangerousZone.builder()
                .h3Index(h3Index)
                .category(request.category())
                .description(request.description())
                .is24_7(request.is24_7())
                .startTime(request.is24_7() ? null : request.startTime())
                .endTime(request.is24_7() ? null : request.endTime())
                .reportedBy(userId)
                .build();
        repository.save(zone);
        kafkaTemplate.send("dangerous-zones-topic", request);
    }
    public List<DangerousZoneHeatmapResponse> getHeatmapData()
    {
        List<DangerousZone> allZones = repository.findAll();
        Map<String, List<DangerousZone>> zonesByCell = allZones.stream()
                .collect(Collectors.groupingBy(DangerousZone::getH3Index));

        return zonesByCell.entrySet().stream()
                .map(entry -> {
                    String h3Index = entry.getKey();
                    List<DangerousZone> zonesInCell = entry.getValue();
                    int intensity = zonesInCell.size();
                    List<String> topCategories = zonesInCell.stream()
                            .map(z -> z.getCategory().name())
                            .distinct()
                            .collect(Collectors.toList());

                    return new DangerousZoneHeatmapResponse(h3Index, intensity, topCategories);
                })
                .collect(Collectors.toList());
    }
}