package services.applicationapi.airport.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;
import services.applicationapi.airport.model.Airport;

public interface AirportRepository extends Repository<Airport, Long> {

    List<Airport> findByMunicipality_IdInOrderByNameAscIdAsc(Collection<Long> municipalityIds);

    boolean existsByIataCodeIgnoreCase(String iataCode);
}
