package services.applicationapi.poiimages.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import services.applicationapi.poiimages.service.exceptions.InvalidPoiImageRequestException;

class PoiImageServiceValidationTest {

    @Test
    void acceptsThePoiIdsPublishedInMapTiles() {
        assertThat(PoiImageService.validPoiId("3f2b6c1e-8a4d-4f7b-9c2e-1a2b3c4d5e6f")).isNotNull();
        assertThat(PoiImageService.validPoiId("osm:n/123456")).isEqualTo("osm:n/123456");
        assertThat(PoiImageService.validPoiId("osm:w/42")).isNotNull();
        assertThat(PoiImageService.validPoiId("osm:r/-7")).isNotNull();
        assertThat(PoiImageService.validPoiId("osm:node/1")).isNotNull();
    }

    @Test
    void storesEachPlaceUnderOneIdForm() {
        assertThat(PoiImageService.validPoiId("osm:node/123")).isEqualTo("osm:n/123");
        assertThat(PoiImageService.validPoiId("osm:way/42")).isEqualTo("osm:w/42");
        assertThat(PoiImageService.validPoiId("osm:relation/-7")).isEqualTo("osm:r/-7");
        assertThat(PoiImageService.validPoiId("3F2B6C1E-8A4D-4F7B-9C2E-1A2B3C4D5E6F"))
                .isEqualTo("3f2b6c1e-8a4d-4f7b-9c2e-1a2b3c4d5e6f");
    }

    @Test
    void rejectsAnythingElseAsAPoiId() {
        for (String id : new String[] {null, "", " ", "osm:x/1", "osm:n/abc", "../../etc/passwd", "osm:n/1 OR 1=1",
                "3f2b6c1e-8a4d-4f7b-9c2e-1a2b3c4d5e6", "x".repeat(170)}) {
            assertThatThrownBy(() -> PoiImageService.validPoiId(id))
                    .as(String.valueOf(id))
                    .isInstanceOf(InvalidPoiImageRequestException.class);
        }
    }

    @Test
    void trimsCaptionsAndTreatsBlankAsNone() {
        assertThat(PoiImageService.validCaption("  Sunset view  ")).isEqualTo("Sunset view");
        assertThat(PoiImageService.validCaption("   ")).isNull();
        assertThat(PoiImageService.validCaption(null)).isNull();
        assertThat(PoiImageService.validCaption("x".repeat(200))).hasSize(200);
        assertThatThrownBy(() -> PoiImageService.validCaption("x".repeat(201)))
                .isInstanceOf(InvalidPoiImageRequestException.class);
    }
}
