# Changelog

All notable changes to Choir Manager are documented here.

---

## [1.1.0] — 2026-04-23

### Added
- **Auto-Sort All** — scans every Google Drive folder at once, infers event
  dates from folder names (ISO dates, "March 2024", underscores, bare years),
  fuzzy-matches photos to existing events within a ±3 day window, and
  auto-creates new events for unrecognised folders.
- **`DriveAutoSortService`** — pluggable service with a progress callback;
  date inference is public and reusable (`inferDate(String)`).
- **`AutoSortDialog`** — three-phase modal: config → live progress log →
  per-photo results table (IMPORTED / SKIPPED / ERROR with detail column).
- Photos now load from the local SQLite library on startup — no Drive
  connection required to browse previously imported photos.
- Gallery falls back gracefully when no photos are present yet.

### Changed
- `PhotoView` toolbar restructured: Auto-Sort button (green when connected)
  sits prominently next to Connect; manual Sync Folder still available.
- `pom.xml` shade plugin now strips signature files (`*.SF`, `*.DSA`, `*.RSA`)
  to prevent `SecurityException` on JAR launch.
- `ServicesResourceTransformer` added to merge `META-INF/services` entries
  correctly (fixes DJL engine registration in fat JAR).

### Fixed
- Tag cache now correctly refreshed after auto-sort completes.
- Gallery "no photos" placeholder text updated to guide new users.

---

## [1.0.0] — Initial release

### Features
- Roster management — full CRUD, voice-part filter, CSV export
- Attendance tracking — per-event sheet, editable status dropdown, summary bar
- Event management — add/edit/delete with type classification
- Photo gallery — Google Drive sync, face detection via DJL, AI tagging with
  confirmation dialog, manual tag/untag, bulk tag, member photo export
- SQLite database, auto-created on first run
- JavaFX UI with consistent CSS theme
