# Trip planning backend

This feature supports city suggestions, local city-to-airports lookup, flight, bus, and train itinerary searches, accommodation searches, place recommendations, destination safety, country hotlines, news, weather, and saved transport search presets. The HTTP endpoints live in [`controllers`](controllers/README.md); request and response records live in [`dto`](dto/README.md). Saved plans support Plane, Bus, and Train.

## Where to start

| Concern | Package | Main responsibility |
| --- | --- | --- |
| City suggestions | [`service/cityautocomplete`](service/cityautocomplete/README.md) | Search GeoNames for populated places in geographical Europe. |
| City airports | [`service/cityairport`](service/cityairport/README.md) | Resolve a city and country to airports in the local database. |
| Flight search | [`service/flightsearch`](service/flightsearch/README.md) | Validate a destination, resolve its Google Flights city ID, and combine priced flight legs. |
| Bus search | [`service/bussearch`](service/bussearch/README.md) | Validate both cities, resolve Omio city IDs, and combine bus fares within the budget. |
| Train search | [`service/trainsearch`](service/trainsearch/README.md) | Search Omio for all supported routes and Bilo for domestic Romanian two-one-way requests, then combine fares within the budget. |
| Accommodation search | [`service/accommodationsearch`](service/accommodationsearch/README.md) | Validate a GeoNames city and filter Google Hotels offers by full-stay EUR budget. |
| Place recommendations | [`service/attractionsearch`](service/attractionsearch/README.md) | Validate a European city and retrieve paged place suggestions by category. |
| Destination safety | [`service/safety`](service/safety/README.md) | Validate a European city and combine country advisories with nearby alerts and conflicts. |
| Country hotlines | [`service/hotlines`](service/hotlines/README.md) | Validate an EU destination country and group locally imported hotline numbers. |
| Destination news | [`service/newsfeed`](service/newsfeed/README.md) | Validate a destination and retrieve one Currents Search page of English-language city news. |
| Destination weather | [`service/weather`](service/weather/README.md) | Validate a European city and retrieve destination-local daily forecasts and optional historical stay comparisons from Open-Meteo. |
| Saved plans | [`service/savedplan`](service/savedplan/README.md) | Create personal and group-owned search presets, select the current group plan, and notify members after a group save commits. |
| Group chat and polls | [`service/groupchat`](service/groupchat/README.md) | Authorize group members, store ordered chat messages and one-vote polls over HTTP, issue socket tickets, and relay a transactional outbox to Kafka. |
| Trip groups | [`service/tripgroup`](service/tripgroup/README.md) | Create, list, and edit groups, protect pictures, and manage one-use invitations. |
| Saved-plan model | [`model`](model/README.md) and [`repository`](repository/README.md) | Persist plans and scope reads by owner. |
| Provider settings | [`configurations`](configurations/README.md) | Bind settings and create HTTP clients with timeouts. |
| Name comparison | [`service/utils`](service/utils/README.md) | Normalize city spelling for comparisons across data sources. |

## Important boundaries

- Flight search checks the origin airport and destination country in the local database. It **does not** use the local city-airports lookup for the destination. GeoNames validates the destination city and SerpApi resolves its Google Flights city ID.
- Bus and train searches use the selected GeoNames departure and destination cities and EU country codes. Their Scrappa Omio providers resolve city IDs, search fares in EUR for the requested 1 to 9 adults, and filter combined fares against the total budget. They do not use the local airport table. One request can make multiple paid provider calls; see each transport package README for limits.
- Domestic Romanian `TWO_ONE_WAY` train searches also use Bilo when its server-side API key is set. Bilo returns published schedules and fares, not seat inventory. RON fares are converted with a Frankfurter rate fetched once per eligible search for EUR budget comparison, and original amounts remain in the response.
- The local airport data is imported by `scripts/airports_data/import_airports.py`. That importer selects EU member countries and their large airports. The city-airports endpoint adds nearby large airports based on validated GeoNames city coordinates, but cannot show smaller or non-imported airports.
- City autocomplete's GeoNames query uses `continentCode=EU`, meaning geographical Europe. This is broader than the EU member-country data used by flight search.
- Search results are itinerary offers, not purchased tickets. `viewUrls` point to Google Flights search pages, or its homepage when SerpApi does not provide a usable link. They may not open the exact displayed flight.
- Accommodation search validates the selected city with GeoNames, searches Google Hotels, and fetches details for a bounded number of properties. Only known totals including taxes and fees can fit the budget; source-specific booking links may be available in `bookingOptions`.
- All place categories share GeoNames validation and one SerpApi Google Maps request per requested page. Google Maps results are not guaranteed to be inside the exact city boundary.
- Safety checks use GeoNames to validate the selected city, then call three Travel Risk API feeds. Advisories apply to the country; geolocated alerts and conflicts are filtered by distance from the city center. Missing nearby reports do not establish safety.
- Country hotlines use the selected destination's country code and local emergency-number rows. The importer in `scripts/emergency_numbers_data` owns the data. Verification labels and dates do not guarantee a number is reachable now.
- Destination news requests use GeoNames city validation and one Currents Search call per explicit user action. The country filter narrows publisher sources, not the article's exact event location.
- Destination weather uses validated GeoNames coordinates. The explicit forecast request makes one Open-Meteo call for at most 14 days. A separate historical request makes one archive call for each configured comparison year and covers every night of the selected stay, including trips outside the forecast window.
- Saved plans store the transport mode, selected departure and destination city/country pairs, departure interval, nights, adults, ticket option, transport and accommodation budgets, title, and optional description. Plane plans also store an origin airport; Bus and Train plans do not. They do not store provider offers or an accommodation check-in date. Personal plans support create and view; group plans additionally support listing and current-plan selection. Edit and delete are not implemented.
- Trip groups have admin-controlled, one-use invitation links. Only token hashes are persisted; the raw token is shown once at creation and is passed to preview/accept in a JSON body. Membership, not possession of a group ID, grants access to group details and pictures.
- Group members can save search presets owned by their group. The first becomes current; admins can mark another as current so members see which plan is being discussed. The same preset stores 1 to 9 adults for later transport and accommodation searches. Search providers receive the requested party size, and budgets are totals for that party.
- An active invite token also permits its holder to preview the group's optional picture before joining. This request is separate from the member-only picture endpoint and returns no photo once the invite expires, is revoked, or is used.
- The API key and GeoNames username come from application settings. Keep real values in the local `.env`; keep `.env.example` keys synchronized when adding settings.

When changing a provider, preserve the service interfaces and request/response contracts. Review provider call counts as well as returned itineraries: a single flight, bus, or train request can make several external searches.
