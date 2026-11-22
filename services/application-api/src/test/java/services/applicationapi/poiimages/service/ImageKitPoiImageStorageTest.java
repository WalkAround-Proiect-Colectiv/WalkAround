package services.applicationapi.poiimages.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import services.applicationapi.poiimages.configurations.PoiImageKitProperties;
import services.applicationapi.poiimages.configurations.PoiImagesProperties;
import services.applicationapi.poiimages.model.StoredPoiImage;
import services.applicationapi.poiimages.service.exceptions.PoiImageStorageException;

class ImageKitPoiImageStorageTest {

    private static final String PRIVATE_KEY = "private_test_key";
    private static final String ENDPOINT = "https://ik.imagekit.io/example-test-endpoint/";
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final UUID IMAGE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String PATH = "/walkaround/poi-images/osm-n-123/" + IMAGE_ID + ".jpg";

    private final PoiImageKitProperties imageKit = new PoiImageKitProperties(PRIVATE_KEY, ENDPOINT,
            "https://upload.imagekit.io/api/v1/files/upload", "https://api.imagekit.io/v1/files",
            Duration.ofSeconds(10), Duration.ofHours(1));
    private final PoiImagesProperties properties = new PoiImagesProperties(5_242_880, 25_000_000, 1600, 320, 10);

    private MockRestServiceServer server;
    private ImageKitPoiImageStorage storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        storage = new ImageKitPoiImageStorage(builder.build(), imageKit, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String sign(String relativePath, long expiry) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(PRIVATE_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return HexFormat.of().formatHex(mac.doFinal((relativePath + expiry).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void signsPhotoUrlsThatExpireAfterTheConfiguredTime() throws Exception {
        long expiry = NOW.plus(Duration.ofHours(1)).getEpochSecond();
        String relative = PATH.substring(1);

        assertThat(storage.imageUrl(PATH)).isEqualTo(
                "https://ik.imagekit.io/example-test-endpoint/" + relative + "?ik-t=" + expiry + "&ik-s=" + sign(relative, expiry));
    }

    @Test
    void signsThumbnailUrlsIncludingTheResizeTransformation() throws Exception {
        long expiry = NOW.plus(Duration.ofHours(1)).getEpochSecond();
        String relative = "tr:w-320,c-at_max/" + PATH.substring(1);

        assertThat(storage.thumbnailUrl(PATH)).isEqualTo(
                "https://ik.imagekit.io/example-test-endpoint/" + relative + "?ik-t=" + expiry + "&ik-s=" + sign(relative, expiry));
    }

    @Test
    void roundsTheExpiryUpToTenMinutesSoUrlsCanBeCached() throws Exception {
        Instant oddTime = Instant.parse("2026-11-21T10:03:20Z");
        ImageKitPoiImageStorage later = new ImageKitPoiImageStorage(RestClient.create(), imageKit, properties,
                Clock.fixed(oddTime, ZoneOffset.UTC));
        ImageKitPoiImageStorage aBitLater = new ImageKitPoiImageStorage(RestClient.create(), imageKit, properties,
                Clock.fixed(oddTime.plusSeconds(200), ZoneOffset.UTC));

        String url = later.imageUrl(PATH);

        long expected = Instant.parse("2026-11-21T11:10:00Z").getEpochSecond();
        assertThat(url).contains("ik-t=" + expected + "&ik-s=" + sign(PATH.substring(1), expected));
        assertThat(aBitLater.imageUrl(PATH)).isEqualTo(url);
    }

    @Test
    void refusesToSignPathsOutsideThePoiPhotoFolder() {
        assertThatThrownBy(() -> storage.imageUrl("/walkaround/group-chats/x/y.jpg"))
                .isInstanceOf(PoiImageStorageException.class);
        assertThatThrownBy(() -> storage.imageUrl("/walkaround/poi-images/../group-chats/y.jpg"))
                .isInstanceOf(PoiImageStorageException.class);
    }

    @Test
    void uploadsAPrivateJpegIntoThePoisFolder() {
        String basic = "Basic " + Base64.getEncoder().encodeToString((PRIVATE_KEY + ":").getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo("https://upload.imagekit.io/api/v1/files/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", basic))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("name=\"isPrivateFile\""),
                        org.hamcrest.Matchers.containsString("/walkaround/poi-images/osm-n-123"),
                        org.hamcrest.Matchers.containsString(IMAGE_ID + ".jpg"))))
                .andRespond(withSuccess("{\"fileId\":\"file_abc123\",\"filePath\":\"" + PATH + "\",\"fileType\":\"image\"}",
                        MediaType.APPLICATION_JSON));

        StoredPoiImage stored = storage.upload(IMAGE_ID, "osm:n/123", new byte[] {1, 2, 3});

        assertThat(stored).isEqualTo(new StoredPoiImage("file_abc123", PATH));
        server.verify();
    }

    @Test
    void reportsUnavailableAndCleansUpWhenImageKitAnswersUnexpectedly() {
        server.expect(requestTo("https://upload.imagekit.io/api/v1/files/upload"))
                .andRespond(withSuccess("{\"fileId\":\"file_abc123\",\"filePath\":\"/elsewhere.jpg\",\"fileType\":\"image\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.imagekit.io/v1/files/file_abc123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> storage.upload(IMAGE_ID, "osm:n/123", new byte[] {1}))
                .isInstanceOf(PoiImageStorageException.class);
        server.verify();
    }

    @Test
    void reportsUnavailableWhenImageKitFails() {
        server.expect(requestTo("https://upload.imagekit.io/api/v1/files/upload")).andRespond(withServerError());

        assertThatThrownBy(() -> storage.upload(IMAGE_ID, "osm:n/123", new byte[] {1}))
                .isInstanceOf(PoiImageStorageException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    void deletesFilesByIdAndIgnoresInvalidIds() {
        server.expect(requestTo("https://api.imagekit.io/v1/files/file_abc123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        storage.delete("file_abc123");
        storage.delete("../../etc");
        storage.delete(null);
        server.verify();
    }

    @Test
    void explainsWhenImageKitIsNotConfigured() {
        PoiImageKitProperties empty = new PoiImageKitProperties("", "", "https://upload.imagekit.io/api/v1/files/upload",
                "https://api.imagekit.io/v1/files", Duration.ofSeconds(10), Duration.ofHours(1));
        ImageKitPoiImageStorage unconfigured = new ImageKitPoiImageStorage(RestClient.create(), empty, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> unconfigured.upload(IMAGE_ID, "osm:n/123", new byte[] {1}))
                .isInstanceOf(PoiImageStorageException.class)
                .hasMessageContaining("not available on this server");
        assertThatThrownBy(() -> unconfigured.imageUrl(PATH)).isInstanceOf(PoiImageStorageException.class);
    }

    @Test
    void turnsPoiIdsIntoSafeFolderNames() {
        assertThat(ImageKitPoiImageStorage.folderName("osm:n/123")).isEqualTo("osm-n-123");
        assertThat(ImageKitPoiImageStorage.folderName("osm:r/-7")).isEqualTo("osm-r--7");
        assertThat(ImageKitPoiImageStorage.folderName("3f2b6c1e-8a4d-4f7b-9c2e-1a2b3c4d5e6f"))
                .isEqualTo("3f2b6c1e-8a4d-4f7b-9c2e-1a2b3c4d5e6f");
    }
}
