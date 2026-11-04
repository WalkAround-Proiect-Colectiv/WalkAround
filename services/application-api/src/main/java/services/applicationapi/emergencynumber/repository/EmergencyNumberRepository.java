package services.applicationapi.emergencynumber.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import services.applicationapi.emergencynumber.model.EmergencyNumber;

public interface EmergencyNumberRepository extends Repository<EmergencyNumber, Long> {

    @Query("""
            SELECT entry FROM EmergencyNumber entry
            WHERE entry.countryCode = :countryCode
            ORDER BY entry.generalEmergency DESC, entry.category, entry.name, entry.id
            """)
    List<EmergencyNumber> findByCountryCode(@Param("countryCode") String countryCode);
}
