package services.applicationapi.tripplan.service.tripgroup;

import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.tripplan.dto.TripGroupResponse;

public interface TripGroupService {

    TripGroupResponse create(UUID creatorId, String name, String description, MultipartFile picture);

    TripGroupResponse update(UUID groupId, UUID adminId, String name, String description,
                             MultipartFile picture, boolean removePicture);

    TripGroupResponse get(UUID groupId, UUID memberId);

    List<TripGroupResponse> list(UUID memberId);

    byte[] picture(UUID groupId, UUID memberId);
}
