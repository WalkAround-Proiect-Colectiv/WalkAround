# Trip planning backend

This feature currently supports city suggestions, a local city-to-airports lookup, flight itinerary searches, accommodation searches, and saved search presets. The HTTP endpoints live in [`controllers`](controllers/README.md); request and response records live in [`dto`](dto/README.md).

## Where to start

| Concern | Package | Main responsibility |
| --- | --- | --- |
| City suggestions | [`service/cityautocomplete`](service/cityautocomplete/README.md) | Search GeoNames for populated places in geographical Europe. |
| City airports | [`service/cityairport`](service/cityairport/README.md) | Resolve a city and country to airports in the local database. |
| Flight search | [`service/flightsearch`](service/flightsearch/README.md) | Validate a destination, resolve its Google Flights city ID, and combine priced flight legs. |
| Accommodation search | [`service/accommodationsearch`](service/accommodationsearch/README.md) | Validate a GeoNames city and filter Google Hotels offers by full-stay EUR budget. |
| Saved plans | [`service/savedplan`](service/savedplan/README.md) | Create and view user-owned search presets. |
| Saved-plan model | [`model`](model/README.md) and [`repository`](repository/README.md) | Persist plans and scope reads by owner. |
| Provider settings | [`configurations`](configurations/README.md) | Bind settings and create HTTP clients with timeouts. |
| Name comparison | [`service/utils`](service/utils/README.md) | Normalize city spelling for comparisons across data sources. |

## Important boundaries

- Flight search checks the origin airport and destination country in the local database. It **does not** use the local city-airports lookup for the destination. GeoNames validates the destination city and SerpApi resolves its Google Flights city ID.
- The local airport data is imported by `scripts/airports_data/import_airports.py`. That importer selects EU member countries and their large airports. The city-airports endpoint can therefore omit smaller airports and cities without imported airports.
- City autocomplete's GeoNames query uses `continentCode=EU`, meaning geographical Europe. This is broader than the EU member-country data used by flight search.
- Search results are itinerary offers, not purchased tickets. `viewUrls` point to Google Flights search pages, or its homepage when SerpApi does not provide a usable link. They may not open the exact displayed flight.
- Accommodation search validates the selected city with GeoNames, searches Google Hotels, and fetches details for a bounded number of properties. Only known totals including taxes and fees can fit the budget; source-specific booking links may be available in `bookingOptions`.
- Saved plans store the selected departure city and country code alongside the origin airport, plus the other search inputs, title, and optional description. This lets the user query airports in the same departure city later. They do not store provider offers or an accommodation check-in date. A user can create a plan and view it by ID; list, edit, and delete are not implemented.
- The API key and GeoNames username come from application settings. Keep real values in the local `.env`; keep `.env.example` keys synchronized when adding settings.

When changing a provider, preserve the service interfaces and the request/response contract. Review the provider call count as well as the returned flights: a single application request can make several external searches.
