package services.applicationapi.tripplan.service.groupchat;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.tripplan.configurations.GroupChatProperties;
import services.applicationapi.tripplan.service.exceptions.GroupChatInvalidMessageException;

@Service
public class JpegGroupChatImageNormalizer implements GroupChatImageNormalizer {

    private final GroupChatProperties properties;

    public JpegGroupChatImageNormalizer(GroupChatProperties properties) {
        this.properties = properties;
    }

    @Override
    public NormalizedImage normalize(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new GroupChatInvalidMessageException("Choose a JPEG or PNG image");
        }
        if (image.getSize() > properties.imageMaxBytes()) {
            throw new GroupChatInvalidMessageException("The image is too large");
        }

        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(image.getBytes()))) {
            if (input == null) throw invalidImage();
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png")) {
                    throw invalidImage();
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1
                        || (long) width * height > properties.imageMaxPixels()) {
                    throw new GroupChatInvalidMessageException("Image dimensions are too large");
                }

                BufferedImage source = reader.read(0);
                double scale = Math.min(1.0,
                        (double) properties.imageOutputSize() / Math.max(width, height));
                int outputWidth = Math.max(1, (int) (width * scale));
                int outputHeight = Math.max(1, (int) (height * scale));
                BufferedImage output = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB);
                var graphics = output.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, outputWidth, outputHeight);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source, 0, 0, outputWidth, outputHeight, null);
                } finally {
                    graphics.dispose();
                }

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (!ImageIO.write(output, "jpeg", bytes)) throw invalidImage();
                byte[] jpeg = bytes.toByteArray();
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return new NormalizedImage(jpeg, HexFormat.of().formatHex(digest.digest(jpeg)),
                        outputWidth, outputHeight);
            } finally {
                reader.dispose();
            }
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw invalidImage();
        }
    }

    private GroupChatInvalidMessageException invalidImage() {
        return new GroupChatInvalidMessageException("Choose a JPEG or PNG image");
    }
}
