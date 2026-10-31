package services.applicationapi.municipality.repository;

import java.util.List;
import org.springframework.data.repository.Repository;
import services.applicationapi.municipality.model.Municipality;

public interface MunicipalityRepository extends Repository<Municipality, Long> {

    List<Municipality> findByCountry_Code(String countryCode);
}
