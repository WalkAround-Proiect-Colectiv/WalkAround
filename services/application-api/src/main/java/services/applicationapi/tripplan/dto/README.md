# Trip planning request and response records

These records form the JSON contract for the trip-planning controllers. Keep their field names and meanings stable when changing a provider or service implementation.

| Record | Meaning |
| --- | --- |
| `CitySuggestion` | GeoNames display name, country/code, coordinates, and `lookupName` for a later city lookup. `lookupName` preserves Unicode spelling with normalized whitespace. |
| `AirportResponse` | Local database airport ID, name, optional IATA code, and coordinates. A null IATA code is allowed by the database. |
| `FlightSearchRequest` | Origin IATA code, destination city/country, EUR budget, inclusive local departure interval, nights, and ticket option. Bean Validation covers individual fields; the service validates relationships between fields and database/provider existence. |
| `FlightItineraryResponse` | Ticket option, EUR total price, outbound and return segment lists, and Google Flights `viewUrls`. |
| `FlightSegmentResponse` | One flight segment, including airport codes, local provider time strings, flight number, and airline. Multiple segments represent connections. |
| `TicketOption` | `ROUND_TRIP` or `TWO_ONE_WAY`. |

For `ROUND_TRIP`, `viewUrls` contains one search-page URL. For `TWO_ONE_WAY`, the first URL is for the outbound search and the second is for the return search. These URLs can point to a search page rather than the exact displayed offer. No DTO contains a booking token or a purchased-ticket record.
