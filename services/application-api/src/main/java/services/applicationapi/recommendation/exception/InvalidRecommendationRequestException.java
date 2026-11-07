package services.applicationapi.recommendation.exception;

public class InvalidRecommendationRequestException extends RuntimeException {

    public InvalidRecommendationRequestException(String message) {
        super(message);
    }
}
