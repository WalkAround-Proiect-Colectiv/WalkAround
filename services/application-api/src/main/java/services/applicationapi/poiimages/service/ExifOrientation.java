package services.applicationapi.poiimages.service;

/**
 * Reads only the EXIF Orientation tag (0x0112) from a JPEG, without any other metadata and without a
 * library. Returns 1 ("upright, no change") when the tag is missing or the data is malformed, so a broken
 * EXIF block can never fail an upload. See the EXIF 2.3 specification, section 4.6.4 (TIFF IFD0).
 */
final class ExifOrientation {

    private static final int ORIENTATION_TAG = 0x0112;

    private ExifOrientation() {
    }

    static int read(byte[] jpeg) {
        try {
            return find(jpeg);
        } catch (RuntimeException malformed) {
            return 1;
        }
    }

    private static int find(byte[] data) {
        if (data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int offset = 2;
        while (offset + 4 <= data.length && (data[offset] & 0xFF) == 0xFF) {
            int marker = data[offset + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                break; // start of image data or end of image: no more metadata segments
            }
            int length = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            if (length < 2 || offset + 2 + length > data.length) {
                break;
            }
            int segment = offset + 4;
            if (marker == 0xE1 && length >= 8 && isExifHeader(data, segment)) {
                return orientationFromTiff(data, segment + 6, offset + 2 + length);
            }
            offset += 2 + length;
        }
        return 1;
    }

    private static boolean isExifHeader(byte[] data, int at) {
        return data[at] == 'E' && data[at + 1] == 'x' && data[at + 2] == 'i' && data[at + 3] == 'f'
                && data[at + 4] == 0 && data[at + 5] == 0;
    }

    private static int orientationFromTiff(byte[] data, int tiff, int end) {
        boolean littleEndian;
        if (data[tiff] == 'I' && data[tiff + 1] == 'I') {
            littleEndian = true;
        } else if (data[tiff] == 'M' && data[tiff + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        long ifdOffset = u32(data, tiff + 4, littleEndian);
        int ifd = (int) (tiff + ifdOffset);
        if (ifdOffset < 8 || ifd + 2 > end) {
            return 1;
        }
        int entries = u16(data, ifd, littleEndian);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (u16(data, entry, littleEndian) == ORIENTATION_TAG) {
                int value = u16(data, entry + 8, littleEndian);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] data, int at, boolean littleEndian) {
        int a = data[at] & 0xFF;
        int b = data[at + 1] & 0xFF;
        return littleEndian ? (b << 8) | a : (a << 8) | b;
    }

    private static long u32(byte[] data, int at, boolean littleEndian) {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int index = littleEndian ? at + 3 - i : at + i;
            value = (value << 8) | (data[index] & 0xFF);
        }
        return value;
    }
}
