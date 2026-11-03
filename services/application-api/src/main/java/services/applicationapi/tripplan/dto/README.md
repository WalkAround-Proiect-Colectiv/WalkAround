# Trip planning request and response records

These records form the JSON contract for the trip-planning controllers. Keep their field names and meanings stable when changing a provider or service implementation.

| Record | Meaning |
| --- | --- |
| `CitySuggestion` | GeoNames display name, country/code, coordinates, and `lookupName` for a later city lookup. `lookupName` preserves Unicode spelling with normalized whitespace. |
| `AirportResponse` | Local database airport ID, name, optional IATA code, and coordinates. A null IATA code is allowed by the database. |
| `PlaceRecommendationResponse` | Attraction or restaurant suggestion with name, optional category/address/description/rating/reviews/coordinates, a Google Maps URL, and optional HTTPS image and website URLs. |
| `PlaceRecommendationPageResponse` | One place-category page with `items` and a nullable zero-based `nextPage` for an explicit follow-up request. |
| `FlightSearchRequest` | Origin IATA code, destination city/country, EUR budget, inclusive local departure interval, nights, and ticket option. Bean Validation covers individual fields; the service validates relationships between fields and database/provider existence. |
| `FlightItineraryResponse` | Ticket option, EUR total price, outbound and return segment lists, and Google Flights `viewUrls`. |
| `FlightSegmentResponse` | One flight segment, including airport codes, local provider time strings, flight number, and airline. Multiple segments represent connections. |
| `TicketOption` | `ROUND_TRIP` or `TWO_ONE_WAY`. |
| `AccommodationSearchRequest` | Selected GeoNames city name and country code, first booked night, staying nights, and total EUR budget. |
| `AccommodationOfferResponse` | Property name and type, full-stay EUR price, dates, optional property website, description, address, rating, review count, amenities, room type names, and booking options. |
| `AccommodationBookingOptionResponse` | Booking source, tax-and-fee-inclusive full-stay EUR price, and HTTPS booking-site URL. |
| `SaveTripPlanRequest` | Title, optional description, origin airport, GeoNames departure city and country code, destination city and country code, flight search inputs, EUR flight budget, and EUR accommodation budget. It has no accommodation check-in date or selected provider offer. |
| `TripPlanResponse` | Saved plan ID, departure city/country code and other stored preset fields, and creation time. The owner ID is not exposed or accepted from the client. |

For `ROUND_TRIP`, `viewUrls` contains one search-page URL. For `TWO_ONE_WAY`, the first URL is for the outbound search and the second is for the return search. These URLs can point to a search page rather than the exact displayed offer. No DTO contains a booking token or a purchased-ticket record.

Accommodation offers are filtered using SerpApi's tax-and-fee-inclusive total for the entire stay. The provider retrieves details for selected properties. `bookingOptions` contain source-specific prices and booking links within the budget; they may be empty when SerpApi supplies no comparable rate. `propertyWebsiteUrl` is a property website when SerpApi supplies a safe HTTPS link; it may not display the same rate or support direct booking.

Saved plans persist the parameters needed to repeat the search later. `departureCity` and `departureCountryCode` let the user request airports in the same departure city. The selected `originAirport` must appear in that city's local airport results. `flightBudget` and `accommodationBudget` are separate total EUR limits. `departureFrom` and `departureUntil` are local datetimes, matching `FlightSearchRequest`. The accommodation check-in date is derived later from the selected flight and is not saved.
