package services.applicationapi.poiimages.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class ExifOrientationTest {

    /** SOI, an APP1 EXIF segment with one IFD0 entry (Orientation), then an arbitrary marker. */
    private static byte[] jpeg(boolean littleEndian, int orientation) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        if (littleEndian) {
            tiff.writeBytes(new byte[] {'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0,
                    (byte) orientation, 0, 0, 0, 0, 0, 0, 0});
        } else {
            tiff.writeBytes(new byte[] {'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1,
                    0, (byte) orientation, 0, 0, 0, 0, 0, 0});
        }
        byte[] payload = tiff.toByteArray();
        int length = payload.length + 8;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1,
                (byte) (length >> 8), (byte) length, 'E', 'x', 'i', 'f', 0, 0});
        out.writeBytes(payload);
        out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xDA, 0, 2});
        return out.toByteArray();
    }

    @Test
    void readsTheOrientationInBothByteOrders() {
        assertThat(ExifOrientation.read(jpeg(false, 6))).isEqualTo(6);
        assertThat(ExifOrientation.read(jpeg(true, 8))).isEqualTo(8);
        assertThat(ExifOrientation.read(jpeg(true, 3))).isEqualTo(3);
    }

    @Test
    void treatsMissingOrInvalidDataAsUpright() {
        assertThat(ExifOrientation.read(new byte[0])).isEqualTo(1);
        assertThat(ExifOrientation.read(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDA, 0, 2})).isEqualTo(1);
        assertThat(ExifOrientation.read(jpeg(false, 9))).isEqualTo(1);
        assertThat(ExifOrientation.read(jpeg(false, 0))).isEqualTo(1);
        byte[] truncated = jpeg(false, 6);
        assertThat(ExifOrientation.read(java.util.Arrays.copyOf(truncated, 20))).isEqualTo(1);
        assertThat(ExifOrientation.read("not a jpeg at all".getBytes())).isEqualTo(1);
    }
}
