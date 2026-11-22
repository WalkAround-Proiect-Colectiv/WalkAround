package services.applicationapi.poiimages.service.exceptions;

/** The uploaded file cannot be used as a POI photo. Maps to 400, or 413 when the file is too large. */
public class InvalidPoiImageException extends RuntimeException {

    private final boolean tooLarge;

    private InvalidPoiImageException(String message, boolean tooLarge) {
        super(message);
        this.tooLarge = tooLarge;
    }

    public static InvalidPoiImageException invalidFile() {
        return new InvalidPoiImageException("Choose a valid JPEG or PNG photo.", false);
    }

    public static InvalidPoiImageException tooLarge() {
        return new InvalidPoiImageException("The selected photo is too large.", true);
    }

    public static InvalidPoiImageException tooManyPixels() {
        return new InvalidPoiImageException("Photo dimensions are too large. Please choose a smaller image.", false);
    }

    public boolean isTooLarge() {
        return tooLarge;
    }
}
