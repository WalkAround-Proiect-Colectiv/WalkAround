package services.applicationapi.poiimages.model;

/** Where an uploaded photo lives in ImageKit: its file id (for deletion) and its path (for delivery URLs). */
public record StoredPoiImage(String fileId, String filePath) {
}
