package services.applicationapi.preferences.service;

import java.util.UUID;
import services.applicationapi.preferences.dto.*;

public interface PreferencesService {
    PreferencesResponse get(UUID userId);
    PreferencesResponse update(UUID userId, UpdatePreferencesRequest request);
}
