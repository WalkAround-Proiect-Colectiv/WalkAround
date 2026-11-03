# Trip planning components

`CalendarPicker` is an in-app date chooser shared by the departure period's **From** and **Until** fields and the accommodation check-in field. It opens as a modal on web and native, starts at the selected date or the earliest allowed date, disables past days, and prevents **Until** from being set before **From**. It returns a local `YYYY-MM-DD` date string without converting through UTC.

`AccommodationOfferCard` renders every hotel field returned by the backend. Booking options display their own source and full-stay price, while an optional property website is shown separately. It only opens HTTPS links with a host and without credentials or an API key query parameter. It cannot guarantee a displayed price remains available on an external site.

`PlaceRecommendationCard` renders a place's optional image, category, rating, review count, address, description, and external links. It shows a map action only for a Google Maps search URL and allows a separate HTTPS place website without credentials or an API key query parameter. Missing optional fields are hidden; image load failures leave the text card usable.
