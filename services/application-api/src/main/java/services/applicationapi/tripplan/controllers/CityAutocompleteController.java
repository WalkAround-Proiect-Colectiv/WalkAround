package services.applicationapi.tripplan.controllers;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.CitySuggestion;
import services.applicationapi.tripplan.service.cityautocomplete.CityAutocompleteService;

@RestController
@RequestMapping("/api/trip-plans")
public class CityAutocompleteController {

    private final CityAutocompleteService cityAutocompleteService;

    public CityAutocompleteController(CityAutocompleteService cityAutocompleteService) {
        this.cityAutocompleteService = cityAutocompleteService;
    }

    @GetMapping("/cities")
    public List<CitySuggestion> autocomplete(@RequestParam String city) {
        return cityAutocompleteService.autocomplete(city);
    }
}
