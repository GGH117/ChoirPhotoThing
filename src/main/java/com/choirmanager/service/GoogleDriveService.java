package com.choirmanager.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.*;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

/**
 * Handles all Google Drive interactions:
 *  - OAuth2 authentication (browser-based first-time flow)
 *  - Listing event folders
 *  - Listing photos within a folder
 *  - Downloading photos to a local cache directory
 *
 * Setup:
 *   1. Create a Google Cloud project at https://console.cloud.google.com
 *   2. Enable the Drive API
 *   3. Create OAuth 2.0 credentials (Desktop app type)
 *   4. Download credentials.json and place it in the project root
 */
public class GoogleDriveService {

    private static final String APP_NAME        = "Choir Manager";
    private static final String CREDENTIALS_FILE = "credentials.json";
    private static final Path   TOKENS_DIR       = Path.of("tokens");
    private static final Path   CACHE_DIR        = Path.of("photo_cache");
    private static final List<String> SCOPES     = Collections.singletonList(DriveScopes.DRIVE_READONLY);
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private Drive driveService;
    private boolean connected = false;

    // -------------------------------------------------------------------------
    // Connection
    // -------------------------------------------------------------------------

    /**
     * Authenticates with Google Drive. Opens a browser on first run for OAuth consent.
     * Tokens are cached locally so subsequent runs are silent.
     *
     * @throws IOException              if credentials.json is missing
     * @throws GeneralSecurityException on SSL issues
     */
    public void connect() throws IOException, GeneralSecurityException {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize(transport);
        driveService = new Drive.Builder(transport, JSON_FACTORY, credential)
                .setApplicationName(APP_NAME)
                .build();
        Files.createDirectories(CACHE_DIR);
        connected = true;
    }

    public boolean isConnected() { return connected; }

    private Credential authorize(NetHttpTransport transport) throws IOException {
        java.io.File credFile = new java.io.File(CREDENTIALS_FILE);
        if (!credFile.exists()) {
            throw new FileNotFoundException(
                "credentials.json not found in project root.\n" +
                "Please download it from Google Cloud Console → APIs & Services → Credentials.");
        }
        GoogleClientSecrets secrets;
        try (InputStream in = new FileInputStream(credFile)) {
            secrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        }
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                transport, JSON_FACTORY, secrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(TOKENS_DIR.toFile()))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    // -------------------------------------------------------------------------
    // Folder listing
    // -------------------------------------------------------------------------

    /**
     * Returns top-level folders in the user's Drive that look like event folders.
     * You can pass a parent folder ID to restrict the search, or null for "My Drive".
     */
    public List<File> listEventFolders(String parentFolderId) throws IOException {
        requireConnected();
        String parent = parentFolderId != null ? parentFolderId : "root";
        String query = String.format(
            "mimeType='application/vnd.google-apps.folder' and '%s' in parents and trashed=false",
            parent);
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name, createdTime, modifiedTime)")
                .setOrderBy("name desc")
                .execute();
        return result.getFiles();
    }

    /** Returns all image files inside the given Drive folder. */
    public List<File> listPhotosInFolder(String folderId) throws IOException {
        requireConnected();
        String query = String.format(
            "('%s' in parents) and (mimeType contains 'image/') and trashed=false",
            folderId);
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name, mimeType, createdTime, thumbnailLink, size)")
                .setOrderBy("createdTime")
                .setPageSize(500)
                .execute();
        return result.getFiles();
    }

    // -------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------

    /**
     * Downloads a photo from Drive to the local cache.
     * Returns the local Path, or the cached copy if already downloaded.
     */
    public Path downloadPhoto(File driveFile) throws IOException {
        requireConnected();
        Path dest = CACHE_DIR.resolve(sanitize(driveFile.getName()));
        if (java.nio.file.Files.exists(dest)) return dest; // already cached

        try (OutputStream out = new FileOutputStream(dest.toFile())) {
            driveService.files().get(driveFile.getId())
                    .executeMediaAndDownloadTo(out);
        }
        return dest;
    }

    /**
     * Downloads a thumbnail-sized version of a photo (faster for gallery view).
     * Falls back to full download if thumbnail is unavailable.
     */
    public Path downloadThumbnail(File driveFile) throws IOException {
        requireConnected();
        String thumbName = "thumb_" + sanitize(driveFile.getName());
        Path dest = CACHE_DIR.resolve(thumbName);
        if (java.nio.file.Files.exists(dest)) return dest;

        String thumbUrl = driveFile.getThumbnailLink();
        if (thumbUrl != null) {
            // Drive thumbnail links are direct HTTPS URLs — fetch via HTTP client
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(thumbUrl.replace("=s220", "=s400")))
                    .GET().build();
            try {
                java.net.http.HttpResponse<byte[]> resp =
                        client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                java.nio.file.Files.write(dest, resp.body());
                return dest;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return downloadPhoto(driveFile); // fallback
    }

    // -------------------------------------------------------------------------
    // Root folder picker helper
    // -------------------------------------------------------------------------

    /** Returns the Drive file ID for a folder with the given name under root, or null. */
    public String findFolderByName(String name) throws IOException {
        requireConnected();
        String query = String.format(
            "name='%s' and mimeType='application/vnd.google-apps.folder' and 'root' in parents and trashed=false",
            name.replace("'", "\\'"));
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .setPageSize(5)
                .execute();
        List<File> files = result.getFiles();
        return files.isEmpty() ? null : files.get(0).getId();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void requireConnected() {
        if (!connected) throw new IllegalStateException(
            "Not connected to Google Drive. Call connect() first.");
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
