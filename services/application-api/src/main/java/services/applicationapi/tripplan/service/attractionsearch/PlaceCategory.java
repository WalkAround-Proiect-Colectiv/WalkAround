package services.applicationapi.tripplan.service.attractionsearch;

public enum PlaceCategory {
    ATTRACTIONS("top-rated tourist attractions"),
    RESTAURANTS("top-rated restaurants");

    private final String searchTerm;

    PlaceCategory(String searchTerm) {
        this.searchTerm = searchTerm;
    }

    public String searchTerm() {
        return searchTerm;
    }
}
