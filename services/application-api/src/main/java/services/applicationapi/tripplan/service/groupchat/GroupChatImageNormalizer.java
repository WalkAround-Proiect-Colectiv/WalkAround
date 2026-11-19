package services.applicationapi.tripplan.service.groupchat;

import org.springframework.web.multipart.MultipartFile;

public interface GroupChatImageNormalizer {

    NormalizedImage normalize(MultipartFile image);

    record NormalizedImage(byte[] jpeg, String sha256, int width, int height) {
    }
}
