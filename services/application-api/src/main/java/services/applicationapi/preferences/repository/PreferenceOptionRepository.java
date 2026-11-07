package services.applicationapi.preferences.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import services.applicationapi.preferences.model.PreferenceOption;

public interface PreferenceOptionRepository extends JpaRepository<PreferenceOption, String> {
    List<PreferenceOption> findAllByOrderByDisplayOrderAscIdAsc();
}
