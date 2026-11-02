package services.routingservice.dangerouszone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import services.routingservice.dangerouszone.model.DangerousZone;

import java.util.UUID;

public interface DangerousZoneRepository extends JpaRepository<DangerousZone, UUID>
{

}