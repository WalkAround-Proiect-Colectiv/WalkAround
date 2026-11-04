# Emergency number repository

`EmergencyNumberRepository` reads all local rows for one country code, with general emergency rows first. It has no provider calls. The trip-planning hotline service groups rows by `source_record_id` into one service with several typed numbers. The importer owns writes and synchronizes these rows; the application only reads them.
