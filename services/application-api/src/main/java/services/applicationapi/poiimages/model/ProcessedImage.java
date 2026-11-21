package services.applicationapi.poiimages.model;

/** An uploaded photo after validation: a resized, metadata-free JPEG and its dimensions. */
public record ProcessedImage(byte[] jpeg, int width, int height) {
}
