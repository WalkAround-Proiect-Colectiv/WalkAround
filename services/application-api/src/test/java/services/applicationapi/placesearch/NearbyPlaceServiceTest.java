package services.applicationapi.placesearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import services.applicationapi.placesearch.controllers.PlaceSearchController;
import services.applicationapi.placesearch.controllers.PlaceSearchExceptionHandler;
import services.applicationapi.placesearch.dto.NearbyPlaceResult;
import services.applicationapi.placesearch.repository.NearbyPlaceRepository;
import services.applicationapi.placesearch.service.InvalidPlaceSearchException;
import services.applicationapi.placesearch.service.NearbyPlaceService;
import services.applicationapi.placesearch.service.PlaceSearchService;
import services.applicationapi.placesearch.service.PlaceSearchUnavailableException;

class NearbyPlaceServiceTest {

    private final NearbyPlaceRepository repository = mock(NearbyPlaceRepository.class);
    private final NearbyPlaceService service = new NearbyPlaceService(repository);

    @Test
    void pagesWithoutSilentlyDroppingStations() {
        var records = IntStream.range(0, 51)
                .mapToObj(i -> new NearbyPlaceResult("n:" + i, "Station " + i, 46.77, 23.59, i))
                .toList();
        when(repository.findPolice(46.77, 23.59, 3000, 0, 51)).thenReturn(records);
        when(repository.findPolice(46.77, 23.59, 3000, 50, 51)).thenReturn(List.of(records.get(50)));

        var first = service.search("police", 46.77, 23.59, 3000, 0);
        var last = service.search("police", 46.77, 23.59, 3000, first.nextPage());

        assertThat(first.items()).hasSize(50);
        assertThat(first.nextPage()).isEqualTo(1);
        assertThat(last.items()).containsExactly(records.get(50));
        assertThat(last.nextPage()).isNull();
    }

    @Test
    void rejectsUnsupportedTypesInvalidPositionsRadiiAndPagesBeforeQuerying() {
        assertThatThrownBy(() -> service.search("hospital", 46.77, 23.59, 3000, 0))
                .isInstanceOf(InvalidPlaceSearchException.class);
        for (double lat : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 90, 0}) {
            assertThatThrownBy(() -> service.search("police", lat, 23.59, 3000, 0))
                    .isInstanceOf(InvalidPlaceSearchException.class);
        }
        assertThatThrownBy(() -> service.search("police", 46.77, Double.NaN, 3000, 0))
                .isInstanceOf(InvalidPlaceSearchException.class);
        for (int radius : new int[]{0, -1, 10001}) {
            assertThatThrownBy(() -> service.search("police", 46.77, 23.59, radius, 0))
                    .isInstanceOf(InvalidPlaceSearchException.class);
        }
        assertThatThrownBy(() -> service.search("police", 46.77, 23.59, 3000, -1))
                .isInstanceOf(InvalidPlaceSearchException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void databaseFailureIsNotAnEmptySuccessfulSearch() throws Exception {
        when(repository.findPolice(anyDouble(), anyDouble(), anyInt(), anyLong(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("private database details"));
        assertThatThrownBy(() -> service.search("police", 46.77, 23.59, 3000, 0))
                .isInstanceOf(PlaceSearchUnavailableException.class)
                .hasMessageNotContaining("private database details");
        mvc().perform(get("/api/places/nearby")
                        .param("type", "police").param("lat", "46.77").param("lon", "23.59"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Place search is temporarily unavailable"));
    }

    @Test
    void httpContractUsesDefaultsAndDistinguishesEmptyAndInvalidRequests() throws Exception {
        when(repository.findPolice(46.77, 23.59, 3000, 0, 51)).thenReturn(List.of());
        var mvc = mvc();
        mvc.perform(get("/api/places/nearby")
                        .param("type", "police").param("lat", "46.77").param("lon", "23.59"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextPage").isEmpty());
        mvc.perform(get("/api/places/nearby")
                        .param("type", "police").param("lat", "bad").param("lon", "23.59"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/places/nearby")
                        .param("type", "police").param("lat", "46.77").param("lon", "23.59")
                        .param("radiusMeters", "10001"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new PlaceSearchController(mock(PlaceSearchService.class), service))
                .setControllerAdvice(new PlaceSearchExceptionHandler()).build();
    }
}
