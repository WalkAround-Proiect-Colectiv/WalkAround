package services.applicationapi.poiimages;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Small generated images for tests. */
final class PoiImageTestImages {

    private PoiImageTestImages() {
    }

    static byte[] encode(int width, int height, String format) {
        int type = format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage image = new BufferedImage(width, height, type);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0, 101, 235));
            graphics.fillRect(0, 0, width, height / 2);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, bytes)) {
                throw new IllegalStateException("No writer for " + format);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    /** A JPEG with an EXIF segment carrying a fake GPS marker, like a phone photo. */
    static byte[] jpegWithExif(int width, int height) {
        byte[] jpeg = encode(width, height, "jpeg");
        byte[] payload = "Exif\0\0GPS-SECRET-LOCATION-46.7712,23.5899".getBytes(StandardCharsets.ISO_8859_1);
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /**
     * A JPEG whose pixels are stored sideways with an EXIF Orientation tag, like a phone photo taken upright.
     * Orientation 6 means "rotate 90 degrees clockwise to display".
     */
    static byte[] jpegWithOrientation(int storedWidth, int storedHeight, int orientation) {
        byte[] jpeg = encode(storedWidth, storedHeight, "jpeg");
        ByteArrayOutputStream exif = new ByteArrayOutputStream();
        byte[] header = {'E', 'x', 'i', 'f', 0, 0, 'M', 'M', 0, 42, 0, 0, 0, 8,
                0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, 0, 0, 0, 0};
        exif.write(header, 0, header.length);
        byte[] payload = exif.toByteArray();
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }
}
