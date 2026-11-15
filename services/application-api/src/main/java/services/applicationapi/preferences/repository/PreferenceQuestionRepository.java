package services.applicationapi.preferences.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import services.applicationapi.preferences.model.PreferenceQuestion;

public interface PreferenceQuestionRepository extends JpaRepository<PreferenceQuestion, String> {
    List<PreferenceQuestion> findAllByOrderByDisplayOrderAscIdAsc();
}
