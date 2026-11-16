package services.applicationapi.tripplan.service.trainsearch;

import java.math.BigDecimal;

public interface ExchangeRateProvider {

    BigDecimal rate(String baseCurrency, String quoteCurrency);
}
