# Trip planning components

`CalendarPicker` is an in-app date chooser shared by the departure period's **From** and **Until** fields. It opens as a modal on web and native, starts at the selected date or the earliest allowed date, disables past days, and prevents **Until** from being set before **From**. It returns a local `YYYY-MM-DD` date string without converting through UTC.
