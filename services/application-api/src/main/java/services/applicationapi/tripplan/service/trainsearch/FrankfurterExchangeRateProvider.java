package services.applicationapi.tripplan.service.trainsearch;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import services.applicationapi.tripplan.service.exceptions.TrainSearchUnavailableException;
import tools.jackson.databind.JsonNode;

@Component
public class FrankfurterExchangeRateProvider implements ExchangeRateProvider {

    private static final Logger log = LoggerFactory.getLogger(FrankfurterExchangeRateProvider.class);

    private final RestClient client;

    public FrankfurterExchangeRateProvider(@Qualifier("frankfurterRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public BigDecimal rate(String baseCurrency, String quoteCurrency) {
        if (!baseCurrency.matches("[A-Z]{3}") || !quoteCurrency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency codes must use three uppercase letters");
        }

        JsonNode response;
        try {
            response = client.get()
                    .uri("/v2/rate/{base}/{quote}", baseCurrency, quoteCurrency)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            log.warn("Frankfurter exchange-rate lookup failed for {}/{}: {}",
                    baseCurrency, quoteCurrency, exception.getClass().getSimpleName());
            throw new TrainSearchUnavailableException("Train search is temporarily unavailable", exception);
        }

        if (response == null
                || !baseCurrency.equalsIgnoreCase(response.path("base").asText())
                || !quoteCurrency.equalsIgnoreCase(response.path("quote").asText())
                || !response.path("rate").isNumber()) {
            log.warn("Frankfurter returned an invalid exchange-rate response for {}/{}",
                    baseCurrency, quoteCurrency);
            throw new TrainSearchUnavailableException("Train search is temporarily unavailable");
        }

        BigDecimal rate = response.path("rate").decimalValue();
        if (rate.signum() <= 0) {
            log.warn("Frankfurter returned a non-positive exchange rate for {}/{}",
                    baseCurrency, quoteCurrency);
            throw new TrainSearchUnavailableException("Train search is temporarily unavailable");
        }
        log.debug("Frankfurter returned a {}/{} exchange rate", baseCurrency, quoteCurrency);
        return rate;
    }
}
