package services.applicationapi.preferences.service;

import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.preferences.dto.*;
import services.applicationapi.preferences.model.PreferenceOption;
import services.applicationapi.preferences.repository.*;
import services.applicationapi.user.model.UserPreferenceChoice;
import services.applicationapi.user.repository.*;

@Service
public class PreferencesServiceImpl implements PreferencesService {
    private static final Logger log = LoggerFactory.getLogger(PreferencesServiceImpl.class);
    private final PreferenceQuestionRepository questions;
    private final PreferenceOptionRepository options;
    private final UserPreferenceChoiceRepository choices;
    private final UserRepository users;

    public PreferencesServiceImpl(PreferenceQuestionRepository questions, PreferenceOptionRepository options,
                                  UserPreferenceChoiceRepository choices, UserRepository users) {
        this.questions = questions;
        this.options = options;
        this.choices = choices;
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public PreferencesResponse get(UUID userId) {
        if (!users.existsById(userId)) throw missingUser();
        return response(userId, options.findAllByOrderByDisplayOrderAscIdAsc());
    }

    @Override
    @Transactional
    public PreferencesResponse update(UUID userId, UpdatePreferencesRequest request) {
        // Serialize replacements for the same user so two requests cannot combine their selections.
        users.findForUpdate(userId).orElseThrow(this::missingUser);
        var catalog = options.findAllByOrderByDisplayOrderAscIdAsc();
        Set<String> selected = new HashSet<>(request.selectedOptionIds());
        Set<String> allowed = catalog.stream().map(PreferenceOption::getId).collect(Collectors.toSet());
        if (selected.size() != request.selectedOptionIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select each option only once");
        }
        if (!allowed.containsAll(selected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose only the available preference options");
        }
        choices.deleteChoicesForUser(userId);
        choices.saveAllAndFlush(selected.stream().map(optionId -> new UserPreferenceChoice(userId, optionId)).toList());
        log.info("Preferences saved: userId={}, selectedOptions={}", userId, selected.size());
        return response(userId, catalog);
    }

    private PreferencesResponse response(UUID userId, List<PreferenceOption> catalog) {
        var grouped = catalog.stream().collect(Collectors.groupingBy(PreferenceOption::getQuestionId));
        var questionDtos = questions.findAllByOrderByDisplayOrderAscIdAsc().stream().map(question ->
            new PreferencesResponse.Question(question.getId(), question.getTitle(),
                grouped.getOrDefault(question.getId(), List.of()).stream()
                    .map(option -> new PreferencesResponse.Option(option.getId(), option.getLabel())).toList())).toList();
        var selected = choices.findByIdUserIdOrderByIdOptionIdAsc(userId).stream()
                .map(choice -> choice.getId().getOptionId()).toList();
        return new PreferencesResponse(questionDtos, selected);
    }

    private ResponseStatusException missingUser() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }
}
