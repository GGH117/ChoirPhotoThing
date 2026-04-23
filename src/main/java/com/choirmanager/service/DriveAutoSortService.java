package com.choirmanager.service;

import com.choirmanager.db.EventDAO;
import com.choirmanager.db.PhotoDAO;
import com.choirmanager.model.Event;
import com.choirmanager.model.Photo;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans all Google Drive folders, downloads thumbnails, matches photos to
 * existing events by date, and inserts unmatched folders as new events.
 *
 * Progress is reported via a BiConsumer<String, Double> callback:
 *   - String: human-readable status message
 *   - Double: 0.0 – 1.0 progress fraction
 */
public class DriveAutoSortService {

    // Patterns used to pull a date out of a Drive folder name.
    // Tried in order; first match wins.
    private static final List<Pattern> DATE_PATTERNS = List.of(
        Pattern.compile("(\\d{4}-\\d{2}-\\d{2})"),                  // 2024-03-15
        Pattern.compile("(\\d{4})[_\\s](\\d{2})[_\\s](\\d{2})"),   // 2024_03_15
        Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})"),              // 03/15/2024
        Pattern.compile("(\\d{4})"),                                 // bare year → Jan 1
        Pattern.compile("(?i)(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[_\\s]*(\\d{4})")
    );

    private static final Map<String, String> MONTH_NUM = Map.ofEntries(
        Map.entry("jan","01"), Map.entry("feb","02"), Map.entry("mar","03"),
        Map.entry("apr","04"), Map.entry("may","05"), Map.entry("jun","06"),
        Map.entry("jul","07"), Map.entry("aug","08"), Map.entry("sep","09"),
        Map.entry("oct","10"), Map.entry("nov","11"), Map.entry("dec","12")
    );

    // -------------------------------------------------------------------------

    private final GoogleDriveService driveService;
    private final EventDAO           eventDAO;
    private final PhotoDAO           photoDAO;

    /** Tracks every photo processed so we can return a full summary. */
    private final List<SortResult> results = new ArrayList<>();

    public DriveAutoSortService(GoogleDriveService driveService,
                                EventDAO eventDAO,
                                PhotoDAO photoDAO) {
        this.driveService = driveService;
        this.eventDAO     = eventDAO;
        this.photoDAO     = photoDAO;
    }

    // =========================================================================
    // Main entry point
    // =========================================================================

    /**
     * Scans all Drive folders under {@code rootFolderId} (or "root" if null),
     * downloads thumbnails, matches to events, and creates events for
     * unmatched folders.
     *
     * @param rootFolderId  Drive folder ID to scan under, or null for My Drive
     * @param progress      callback(message, 0..1) — called on the calling thread
     * @return              summary of everything that was processed
     */
    public SortSummary sortAll(String rootFolderId,
                               BiConsumer<String, Double> progress) throws Exception {

        results.clear();

        // ── Step 1: List all event folders ────────────────────────────────
        progress.accept("Scanning Drive folders…", 0.0);
        List<File> folders = driveService.listEventFolders(rootFolderId);
        if (folders.isEmpty()) {
            progress.accept("No folders found.", 1.0);
            return buildSummary();
        }

        // ── Step 2: Load existing events (for date-matching) ──────────────
        List<Event> existingEvents = eventDAO.findAll();

        // ── Step 3: Process each folder ───────────────────────────────────
        for (int fi = 0; fi < folders.size(); fi++) {
            File folder = folders.get(fi);
            double folderProgress = (double) fi / folders.size();
            progress.accept("Processing folder: " + folder.getName(), folderProgress);

            String inferredDate = inferDate(folder.getName());
            Event  matchedEvent = inferredDate != null
                    ? findMatchingEvent(existingEvents, inferredDate)
                    : null;

            // Auto-create an event for this folder if none matched
            if (matchedEvent == null && inferredDate != null) {
                matchedEvent = createEventFromFolder(folder.getName(), inferredDate);
                existingEvents.add(matchedEvent); // keep in-memory list current
            }

            // ── Step 4: Download photos in this folder ────────────────────
            List<File> drivePhotos;
            try {
                drivePhotos = driveService.listPhotosInFolder(folder.getId());
            } catch (IOException e) {
                results.add(new SortResult(folder.getName(), null, null,
                        ResultStatus.ERROR, "Could not list photos: " + e.getMessage()));
                continue;
            }

            for (int pi = 0; pi < drivePhotos.size(); pi++) {
                File drivePhoto = drivePhotos.get(pi);
                double photoFrac = folderProgress
                        + (1.0 / folders.size()) * ((double)(pi + 1) / drivePhotos.size());
                progress.accept(
                    String.format("  %s — photo %d/%d",
                            folder.getName(), pi + 1, drivePhotos.size()),
                    photoFrac);

                processPhoto(drivePhoto, folder, matchedEvent, inferredDate);
            }
        }

        progress.accept("Done — " + results.size() + " photos processed.", 1.0);
        return buildSummary();
    }

    // =========================================================================
    // Per-photo processing
    // =========================================================================

    private void processPhoto(File drivePhoto, File folder,
                              Event matchedEvent, String folderDate) {
        try {
            Path thumb = driveService.downloadThumbnail(drivePhoto);
            String localPath = thumb.toString();

            // Check if already in DB
            Photo existing = photoDAO.findByFilePath(localPath);
            if (existing != null) {
                // Update event link if it was previously unmatched
                if (existing.getEventId() == null && matchedEvent != null) {
                    existing.setEventId(matchedEvent.getId());
                    photoDAO.insertPhoto(existing); // upsert via file_path unique constraint
                }
                results.add(new SortResult(
                        folder.getName(), drivePhoto.getName(),
                        matchedEvent != null ? matchedEvent.getTitle() : null,
                        ResultStatus.SKIPPED, "Already in library"));
                return;
            }

            // New photo — build and insert
            String takenDate = inferDate(drivePhoto.getName());
            if (takenDate == null) takenDate = folderDate;

            Photo photo = new Photo(
                0, localPath, drivePhoto.getId(),
                folder.getName(),
                matchedEvent != null ? matchedEvent.getId() : null,
                takenDate, null
            );
            photoDAO.insertPhoto(photo);

            results.add(new SortResult(
                    folder.getName(), drivePhoto.getName(),
                    matchedEvent != null ? matchedEvent.getTitle() : null,
                    ResultStatus.IMPORTED, null));

        } catch (Exception e) {
            results.add(new SortResult(
                    folder.getName(), drivePhoto.getName(), null,
                    ResultStatus.ERROR, e.getMessage()));
        }
    }

    // =========================================================================
    // Date inference
    // =========================================================================

    /**
     * Tries to extract an ISO date (YYYY-MM-DD) from a folder or file name.
     * Returns null if nothing recognizable is found.
     */
    public static String inferDate(String name) {
        if (name == null) return null;

        // Pattern 1: explicit ISO date
        Matcher m1 = DATE_PATTERNS.get(0).matcher(name);
        if (m1.find()) return m1.group(1);

        // Pattern 2: underscored / spaced
        Matcher m2 = DATE_PATTERNS.get(1).matcher(name);
        if (m2.find()) return m2.group(1) + "-" + m2.group(2) + "-" + m2.group(3);

        // Pattern 3: MM/DD/YYYY
        Matcher m3 = DATE_PATTERNS.get(2).matcher(name);
        if (m3.find()) return m3.group(3) + "-" + m3.group(1) + "-" + m3.group(2);

        // Pattern 5: "March 2024" / "Mar_2024"
        Matcher m5 = DATE_PATTERNS.get(4).matcher(name);
        if (m5.find()) {
            String month = MONTH_NUM.getOrDefault(m5.group(1).toLowerCase().substring(0,3), "01");
            return m5.group(2) + "-" + month + "-01";
        }

        // Pattern 4: bare year → Jan 1
        Matcher m4 = DATE_PATTERNS.get(3).matcher(name);
        if (m4.find()) return m4.group(1) + "-01-01";

        return null;
    }

    // =========================================================================
    // Event matching / creation
    // =========================================================================

    /**
     * Returns the event whose date is closest to {@code isoDate} within ±3 days.
     * Prefers exact matches.
     */
    private Event findMatchingEvent(List<Event> events, String isoDate) {
        try {
            LocalDate target = LocalDate.parse(isoDate);
            Event best = null;
            long bestDelta = Long.MAX_VALUE;
            for (Event e : events) {
                if (e.getEventDate() == null) continue;
                try {
                    long delta = Math.abs(LocalDate.parse(e.getEventDate())
                            .toEpochDay() - target.toEpochDay());
                    if (delta <= 3 && delta < bestDelta) {
                        bestDelta = delta;
                        best = e;
                    }
                } catch (DateTimeParseException ignored) {}
            }
            return best;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Event createEventFromFolder(String folderName, String isoDate) throws Exception {
        // Guess event type from folder name keywords
        Event.EventType type = Event.EventType.Other;
        String lower = folderName.toLowerCase();
        if (lower.contains("rehearsal") || lower.contains("practice") || lower.contains("rehearse"))
            type = Event.EventType.Rehearsal;
        else if (lower.contains("concert") || lower.contains("performance") || lower.contains("show"))
            type = Event.EventType.Concert;
        else if (lower.contains("workshop") || lower.contains("masterclass"))
            type = Event.EventType.Workshop;

        // Clean up the folder name for the title
        String title = folderName
                .replaceAll("_", " ")
                .replaceAll("\\d{4}-\\d{2}-\\d{2}", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (title.isBlank()) title = folderName;

        Event event = new Event(0, title, isoDate, null, null, null, type, null);
        return eventDAO.insert(event);
    }

    // =========================================================================
    // Summary
    // =========================================================================

    private SortSummary buildSummary() {
        long imported = results.stream().filter(r -> r.status == ResultStatus.IMPORTED).count();
        long skipped  = results.stream().filter(r -> r.status == ResultStatus.SKIPPED).count();
        long errors   = results.stream().filter(r -> r.status == ResultStatus.ERROR).count();
        long matched  = results.stream().filter(r -> r.eventTitle != null).count();
        return new SortSummary((int)imported, (int)skipped, (int)errors,
                               (int)matched, List.copyOf(results));
    }

    // =========================================================================
    // Result types
    // =========================================================================

    public enum ResultStatus { IMPORTED, SKIPPED, ERROR }

    public record SortResult(
            String folderName,
            String photoName,
            String eventTitle,
            ResultStatus status,
            String detail
    ) {}

    public record SortSummary(
            int imported,
            int skipped,
            int errors,
            int matchedToEvent,
            List<SortResult> details
    ) {
        public String summary() {
            return String.format(
                "Imported: %d  |  Skipped: %d  |  Errors: %d  |  Matched to events: %d",
                imported, skipped, errors, matchedToEvent);
        }
    }
}
