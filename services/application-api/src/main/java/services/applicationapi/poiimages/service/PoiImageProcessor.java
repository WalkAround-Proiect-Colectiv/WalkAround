package services.applicationapi.poiimages.service;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import services.applicationapi.poiimages.configurations.PoiImagesProperties;
import services.applicationapi.poiimages.model.ProcessedImage;
import services.applicationapi.poiimages.service.exceptions.InvalidPoiImageException;

/**
 * Validates an uploaded photo and produces the stored versions, following the same approach as profile
 * pictures: the file contents (not its name or declared type) must be JPEG or PNG, the decoded size is
 * bounded before decoding, and the image is re-encoded as JPEG. Re-encoding drops all metadata, including
 * the GPS position phones store in photos. Thumbnails are made later by ImageKit, not stored.
 */
@Component
public class PoiImageProcessor {

    private final PoiImagesProperties properties;

    public PoiImageProcessor(PoiImagesProperties properties) {
        this.properties = properties;
    }

    public ProcessedImage process(byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw InvalidPoiImageException.invalidFile();
        }
        if (upload.length > properties.maxBytes()) {
            throw InvalidPoiImageException.tooLarge();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw InvalidPoiImageException.invalidFile();
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png")) {
                    throw InvalidPoiImageException.invalidFile();
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > properties.maxPixels()) {
                    throw InvalidPoiImageException.tooManyPixels();
                }
                BufferedImage source = reader.read(0);
                int orientation = format.equalsIgnoreCase("jpeg") ? ExifOrientation.read(upload) : 1;
                BufferedImage photo = resizeUpright(source, properties.outputSize(), orientation);
                return new ProcessedImage(jpeg(photo), photo.getWidth(), photo.getHeight());
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof InvalidPoiImageException invalid) {
                throw invalid;
            }
            throw InvalidPoiImageException.invalidFile();
        }
    }

    /**
     * Scales down so the longest side is at most {@code maxSide} (never scales up) and turns the image upright
     * according to its EXIF orientation (1-8). Phones often store pixels sideways and rely on that tag, which
     * re-encoding removes, so the rotation must be applied to the pixels here. Transparency becomes white.
     */
    static BufferedImage resizeUpright(BufferedImage source, int maxSide, int orientation) {
        double scale = Math.min(1.0, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        boolean swapsSides = orientation >= 5 && orientation <= 8;
        BufferedImage output = new BufferedImage(swapsSides ? height : width, swapsSides ? width : height,
                BufferedImage.TYPE_INT_RGB);
        var graphics = output.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, output.getWidth(), output.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setTransform(orientationTransform(orientation, width, height));
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    /** Maps the stored (already scaled) image of size width x height onto the upright output. */
    private static AffineTransform orientationTransform(int orientation, int width, int height) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);       // mirrored horizontally
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height); // upside down
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);      // mirrored vertically
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);            // mirrored and rotated
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);      // rotate 90 degrees clockwise
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width); // mirrored and rotated
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);       // rotate 90 degrees counter-clockwise
            default -> new AffineTransform();
        };
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpeg", bytes)) {
            throw new IOException("No JPEG writer available");
        }
        return bytes.toByteArray();
    }
}
