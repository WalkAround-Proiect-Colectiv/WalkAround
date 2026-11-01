package services.applicationapi.country.repository;

import java.util.Optional;
import org.springframework.data.repository.Repository;
import services.applicationapi.country.model.Country;

public interface CountryRepository extends Repository<Country, Long> {

    Optional<Country> findByCode(String code);
}
