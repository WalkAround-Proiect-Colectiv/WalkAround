# Destination news feed

`NewsFeedService` validates a city and EU country before searching news. It uses the existing `AccommodationCityValidator` to confirm the city belongs to that country and obtain a canonical city name. Invalid input returns `400`; unavailable GeoNames or Currents returns a generic `502`.

`NewsFeedProvider` separates the service from the news source. `CurrentsNewsFeedProvider` calls `GET /v1/search` with `keywords` set to the validated city, `language=en`, `country` set to the selected country, and configured `page_size` (20 by default). It sends the API key in an `Authorization: Bearer` header and makes one Currents request per user action. It requires `status=ok` and a `news` list, skips entries without a title or safe HTTPS publisher URL, and exposes article metadata and links. It does not pass upstream errors or credentials to clients, cache news, or fetch further pages.

Currents' `country` parameter filters source country; a matching article may mention the city without describing events inside it. The UI labels this distinction. Search is requested only when the user opens the news section near the bottom of the trip page, avoiding quota use during city selection. [Currents Search reference](https://currentsapi.services/en/docs/search).
