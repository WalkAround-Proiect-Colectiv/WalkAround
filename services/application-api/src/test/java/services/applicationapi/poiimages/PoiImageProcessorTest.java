package services.applicationapi.poiimages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import services.applicationapi.poiimages.configurations.PoiImagesProperties;
import services.applicationapi.poiimages.model.ProcessedImage;
import services.applicationapi.poiimages.service.PoiImageProcessor;
import services.applicationapi.poiimages.service.exceptions.InvalidPoiImageException;

class PoiImageProcessorTest {

    private final PoiImageProcessor processor =
            new PoiImageProcessor(new PoiImagesProperties(200_000, 4_000_000, 400, 100, 10));

    private static BufferedImage read(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    @Test
    void resizesLargePhotos() throws IOException {
        ProcessedImage result = processor.process(PoiImageTestImages.encode(1000, 500, "jpeg"));

        assertThat(result.width()).isEqualTo(400);
        assertThat(result.height()).isEqualTo(200);
        BufferedImage stored = read(result.jpeg());
        assertThat(stored.getWidth()).isEqualTo(400);
        assertThat(stored.getHeight()).isEqualTo(200);
    }

    @Test
    void keepsSmallPhotosAtTheirSize() {
        ProcessedImage result = processor.process(PoiImageTestImages.encode(120, 80, "jpeg"));

        assertThat(result.width()).isEqualTo(120);
        assertThat(result.height()).isEqualTo(80);
    }

    @Test
    void convertsPngToJpeg() throws IOException {
        ProcessedImage result = processor.process(PoiImageTestImages.encode(300, 300, "png"));

        assertThat(result.jpeg()[0] & 0xFF).isEqualTo(0xFF);
        assertThat(result.jpeg()[1] & 0xFF).isEqualTo(0xD8);
        assertThat(read(result.jpeg())).isNotNull();
    }

    @Test
    void removesPhotoMetadataSuchAsTheGpsPosition() {
        byte[] upload = PoiImageTestImages.jpegWithExif(300, 200);
        assertThat(PoiImageTestImages.contains(upload, "GPS-SECRET")).isTrue();

        ProcessedImage result = processor.process(upload);

        assertThat(PoiImageTestImages.contains(result.jpeg(), "GPS-SECRET")).isFalse();
        assertThat(PoiImageTestImages.contains(result.jpeg(), "Exif")).isFalse();
    }

    @Test
    void turnsPhonePhotosUprightUsingTheirOrientationTag() {
        // Stored 300x200 (landscape) with "rotate 90 degrees" => the person saw a 200x300 portrait photo.
        ProcessedImage rotated = processor.process(PoiImageTestImages.jpegWithOrientation(300, 200, 6));
        assertThat(rotated.width()).isEqualTo(200);
        assertThat(rotated.height()).isEqualTo(300);

        ProcessedImage upsideDown = processor.process(PoiImageTestImages.jpegWithOrientation(300, 200, 3));
        assertThat(upsideDown.width()).isEqualTo(300);
        assertThat(upsideDown.height()).isEqualTo(200);

        ProcessedImage normal = processor.process(PoiImageTestImages.jpegWithOrientation(300, 200, 1));
        assertThat(normal.width()).isEqualTo(300);
    }

    @Test
    void rotatesThePixelsNotJustTheSize() throws IOException {
        // The test image is blue in its top half. Rotating 90 degrees clockwise moves that to the right half;
        // turning it upside down moves it to the bottom half.
        BufferedImage clockwise = read(processor.process(PoiImageTestImages.jpegWithOrientation(300, 200, 6)).jpeg());
        assertThat(isBlue(clockwise.getRGB(150, 150))).isTrue();
        assertThat(isBlue(clockwise.getRGB(50, 150))).isFalse();

        BufferedImage upsideDown = read(processor.process(PoiImageTestImages.jpegWithOrientation(300, 200, 3)).jpeg());
        assertThat(isBlue(upsideDown.getRGB(150, 150))).isTrue();
        assertThat(isBlue(upsideDown.getRGB(150, 50))).isFalse();
    }

    private static boolean isBlue(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int blue = rgb & 0xFF;
        return blue > 180 && red < 60;
    }

    @Test
    void rejectsOtherFormatsAndNonImages() {
        assertThatThrownBy(() -> processor.process(PoiImageTestImages.encode(50, 50, "gif")))
                .isInstanceOf(InvalidPoiImageException.class);
        assertThatThrownBy(() -> processor.process("not an image".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidPoiImageException.class);
        assertThatThrownBy(() -> processor.process(new byte[0]))
                .isInstanceOf(InvalidPoiImageException.class);
    }

    @Test
    void rejectsFilesThatAreTooLarge() {
        assertThatThrownBy(() -> processor.process(new byte[200_001]))
                .isInstanceOfSatisfying(InvalidPoiImageException.class,
                        exception -> assertThat(exception.isTooLarge()).isTrue());
    }

    @Test
    void rejectsImagesWithTooManyPixelsBeforeDecodingThem() {
        PoiImageProcessor strict = new PoiImageProcessor(new PoiImagesProperties(2_000_000, 10_000, 400, 100, 10));

        assertThatThrownBy(() -> strict.process(PoiImageTestImages.encode(200, 100, "png")))
                .isInstanceOfSatisfying(InvalidPoiImageException.class,
                        exception -> assertThat(exception.isTooLarge()).isFalse());
    }
}
