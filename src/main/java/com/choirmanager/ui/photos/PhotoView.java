package com.choirmanager.ui.photos;

import com.choirmanager.db.MemberDAO;
import com.choirmanager.db.PhotoDAO;
import com.choirmanager.model.Member;
import com.choirmanager.model.Photo;
import com.choirmanager.service.FaceRecognitionService;
import com.choirmanager.service.GoogleDriveService;
import com.google.api.services.drive.model.File;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Polished photo gallery with:
 *  - Folder-grouped sections with header labels
 *  - Sort by date / folder / tag count
 *  - "Untagged only" filter toggle
 *  - Multi-select bulk tagging
 *  - Auto-rename by event
 *  - Search by member name or folder
 */
public class PhotoView extends BorderPane {

    private final PhotoDAO photoDAO;
    private final MemberDAO memberDAO;
    private final GoogleDriveService driveService;
    private final FaceRecognitionService faceService;

    private final Button connectBtn    = new Button("🔗 Connect Google Drive");
    private final Button syncBtn       = new Button("🔄 Sync Folder");
    private final Button bulkTagBtn    = new Button("🏷 Bulk Tag Selected");
    private final Button renameBtn     = new Button("✏️ Auto-Rename");
    private final Button exportBtn     = new Button("📤 Export Member");
    private final TextField searchField = new TextField();
    private final ComboBox<String> sortCombo  = new ComboBox<>();
    private final ToggleButton untaggedToggle = new ToggleButton("⬜ Untagged Only");

    private final TreeView<String> folderTree    = new TreeView<>();
    private final VBox galleryContainer          = new VBox(0);
    private final Label statusLabel              = new Label("Not connected to Google Drive");

    private List<Member> allMembers = new ArrayList<>();
    private String selectedDriveFolderId = null;
    private final List<Photo> allPhotos              = new ArrayList<>();
    private final Map<Integer, List<Member>> tagCache = new HashMap<>();
    private final Set<Integer> selectedPhotoIds       = new HashSet<>();

    public PhotoView() throws SQLException {
        this.photoDAO     = new PhotoDAO();
        this.memberDAO    = new MemberDAO();
        this.driveService = new GoogleDriveService();
        this.faceService  = new FaceRecognitionService(photoDAO);
        buildUI();
        loadMembersAsync();
        loadAIModelAsync();
    }

    // =========================================================================
    // Build UI
    // =========================================================================

    private void buildUI() {
        syncBtn.setDisable(true);
        bulkTagBtn.setDisable(true);

        // Toolbar row 1
        HBox toolbar1 = new HBox(8, connectBtn, syncBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                bulkTagBtn, renameBtn, new Region(), exportBtn);
        HBox.setHgrow(toolbar1.getChildren().get(6), Priority.ALWAYS);
        toolbar1.setPadding(new Insets(8, 12, 4, 12));
        toolbar1.setAlignment(Pos.CENTER_LEFT);

        // Toolbar row 2
        searchField.setPromptText("🔍 Search by member or folder…");
        searchField.setPrefWidth(240);
        searchField.textProperty().addListener((o, old, v) -> applyFiltersAndRebuild());

        sortCombo.getItems().addAll(
                "Sort: Date (newest)", "Sort: Date (oldest)",
                "Sort: Folder A–Z",    "Sort: Tag Count ↓");
        sortCombo.setValue("Sort: Date (newest)");
        sortCombo.setOnAction(e -> applyFiltersAndRebuild());

        untaggedToggle.setOnAction(e -> {
            untaggedToggle.setText(untaggedToggle.isSelected() ? "🔴 Untagged Only" : "⬜ Untagged Only");
            applyFiltersAndRebuild();
        });

        HBox toolbar2 = new HBox(8, searchField, sortCombo, untaggedToggle);
        toolbar2.setPadding(new Insets(4, 12, 8, 12));
        toolbar2.setAlignment(Pos.CENTER_LEFT);

        VBox toolbarBox = new VBox(toolbar1, toolbar2);
        toolbarBox.setStyle("-fx-background-color: #f5f5f5; " +
                "-fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");

        // Folder tree
        TreeItem<String> treeRoot = new TreeItem<>("My Drive");
        treeRoot.setExpanded(true);
        folderTree.setRoot(treeRoot);
        folderTree.setPrefWidth(220);
        folderTree.setStyle("-fx-background-color: #f0f0f0;");
        folderTree.getSelectionModel().selectedItemProperty()
                .addListener((o, old, sel) -> onFolderSelected(sel));

        Label folderLabel = new Label("Drive Folders");
        folderLabel.setStyle("-fx-font-weight: bold; -fx-padding: 8 8 4 8;");
        VBox leftPanel = new VBox(folderLabel, folderTree);
        VBox.setVgrow(folderTree, Priority.ALWAYS);

        // Gallery
        galleryContainer.setPadding(new Insets(12));
        galleryContainer.setStyle("-fx-background-color: white;");
        Label placeholder = new Label(
                "Connect to Google Drive and select an event folder to view photos.");
        placeholder.setStyle("-fx-text-fill: #999; -fx-font-size: 14px;");
        galleryContainer.getChildren().add(placeholder);

        ScrollPane scroll = new ScrollPane(galleryContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: white;");

        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(4, 12, 4, 12));
        statusBar.setStyle("-fx-background-color: #e8e8e8;");

        connectBtn.setOnAction(e -> connectToDrive());
        syncBtn.setOnAction(e    -> syncSelectedFolder());
        bulkTagBtn.setOnAction(e -> bulkTagSelected());
        renameBtn.setOnAction(e  -> autoRenamePhotos());
        exportBtn.setOnAction(e  -> exportMemberPhotos());

        setTop(toolbarBox);
        setLeft(leftPanel);
        setCenter(scroll);
        setBottom(statusBar);
    }

    // =========================================================================
    // Gallery rendering
    // =========================================================================

    private void applyFiltersAndRebuild() {
        String query         = searchField.getText().toLowerCase();
        boolean untaggedOnly = untaggedToggle.isSelected();
        String sort          = sortCombo.getValue();

        List<Photo> filtered = allPhotos.stream().filter(p -> {
            if (untaggedOnly && !tagCache.getOrDefault(p.getId(), List.of()).isEmpty()) return false;
            if (!query.isBlank()) {
                String folder = p.getDriveFolderName() != null
                        ? p.getDriveFolderName().toLowerCase() : "";
                boolean memberMatch = tagCache.getOrDefault(p.getId(), List.of()).stream()
                        .anyMatch(m -> m.getFullName().toLowerCase().contains(query));
                if (!folder.contains(query) && !memberMatch) return false;
            }
            return true;
        }).collect(Collectors.toList());

        Comparator<Photo> comparator = switch (sort) {
            case "Sort: Date (oldest)" -> Comparator.comparing(p -> nullSafe(p.getTakenDate()));
            case "Sort: Folder A–Z"    -> Comparator.comparing(p -> nullSafe(p.getDriveFolderName()));
            case "Sort: Tag Count ↓"   -> Comparator.comparingInt(
                    (Photo p) -> tagCache.getOrDefault(p.getId(), List.of()).size()).reversed();
            default -> Comparator.comparing((Photo p) -> nullSafe(p.getTakenDate())).reversed();
        };
        filtered.sort(comparator);

        LinkedHashMap<String, List<Photo>> grouped = new LinkedHashMap<>();
        for (Photo p : filtered) {
            String folder = p.getDriveFolderName() != null ? p.getDriveFolderName() : "Unsorted";
            grouped.computeIfAbsent(folder, k -> new ArrayList<>()).add(p);
        }

        Platform.runLater(() -> {
            galleryContainer.getChildren().clear();
            if (grouped.isEmpty()) {
                Label empty = new Label(untaggedOnly
                        ? "🎉 All photos are tagged!"
                        : "No photos match your search.");
                empty.setStyle("-fx-text-fill: #999; -fx-font-size: 14px; -fx-padding: 20;");
                galleryContainer.getChildren().add(empty);
                return;
            }
            for (Map.Entry<String, List<Photo>> entry : grouped.entrySet()) {
                galleryContainer.getChildren().add(
                        buildSectionHeader(entry.getKey(), entry.getValue().size()));
                galleryContainer.getChildren().add(buildPhotoSection(entry.getValue()));
            }
            updateSelectionBadge();
        });
    }

    private HBox buildSectionHeader(String folderName, int count) {
        Label title = new Label(folderName);
        title.setFont(Font.font(null, FontWeight.BOLD, 14));

        Label countLbl = new Label(count + " photo" + (count == 1 ? "" : "s"));
        countLbl.setStyle("-fx-text-fill: #888; -fx-font-size: 12px;");

        Hyperlink selectAll = new Hyperlink("Select all");
        selectAll.setOnAction(e -> selectAllInFolder(folderName));

        Hyperlink deselectAll = new Hyperlink("Deselect");
        deselectAll.setOnAction(e -> deselectAllInFolder(folderName));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, title, countLbl, spacer, selectAll, deselectAll);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 8, 6, 8));
        header.setStyle("-fx-border-color: transparent transparent #ddd transparent; " +
                "-fx-border-width: 0 0 1 0;");
        return header;
    }

    private FlowPane buildPhotoSection(List<Photo> photos) {
        FlowPane pane = new FlowPane();
        pane.setHgap(8);
        pane.setVgap(8);
        pane.setPadding(new Insets(10, 8, 10, 8));
        for (Photo p : photos) pane.getChildren().add(buildTile(p));
        return pane;
    }

    private VBox buildTile(Photo photo) {
        ImageView thumb = new ImageView();
        thumb.setFitWidth(140);
        thumb.setFitHeight(105);
        thumb.setPreserveRatio(true);

        if (photo.getFilePath() != null) {
            try {
                thumb.setImage(new Image(
                        Path.of(photo.getFilePath()).toUri().toString(),
                        140, 105, true, true, true));
            } catch (Exception ignored) {}
        }

        // Tagged members badge
        List<Member> tags = tagCache.getOrDefault(photo.getId(), List.of());
        Label tagBadge = new Label(tags.isEmpty() ? "" : tags.size() + " 👤");
        tagBadge.setStyle("-fx-background-color: #2c6fad; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-padding: 1 6 1 6; -fx-font-size: 10px;");
        tagBadge.setVisible(!tags.isEmpty());

        // Untagged badge
        Label untaggedBadge = new Label("UNTAGGED");
        untaggedBadge.setStyle("-fx-background-color: #e8a020; -fx-text-fill: white; " +
                "-fx-background-radius: 4; -fx-padding: 1 5 1 5; -fx-font-size: 9px;");
        untaggedBadge.setVisible(tags.isEmpty());

        // Selection checkbox
        CheckBox selectBox = new CheckBox();
        selectBox.setSelected(selectedPhotoIds.contains(photo.getId()));
        selectBox.selectedProperty().addListener((o, old, checked) -> {
            if (checked) selectedPhotoIds.add(photo.getId());
            else         selectedPhotoIds.remove(photo.getId());
            updateSelectionBadge();
        });

        StackPane thumbStack = new StackPane(thumb, tagBadge, untaggedBadge, selectBox);
        StackPane.setAlignment(tagBadge,      Pos.TOP_RIGHT);
        StackPane.setAlignment(untaggedBadge, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(selectBox,     Pos.TOP_LEFT);
        StackPane.setMargin(tagBadge,      new Insets(4));
        StackPane.setMargin(untaggedBadge, new Insets(4));
        StackPane.setMargin(selectBox,     new Insets(4));

        Label nameLbl = new Label(photo.getDisplayName());
        nameLbl.setMaxWidth(140);
        nameLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #555;");

        String tagText = tags.stream().map(Member::getFirstName).collect(Collectors.joining(", "));
        Label tagNames = new Label(tagText.isEmpty() ? "" : "👤 " + tagText);
        tagNames.setMaxWidth(140);
        tagNames.setStyle("-fx-font-size: 10px; -fx-text-fill: #2c6fad;");
        tagNames.setWrapText(true);

        VBox tile = new VBox(4, thumbStack, nameLbl, tagNames);
        tile.setAlignment(Pos.CENTER);
        tile.setPadding(new Insets(6));
        boolean selected = selectedPhotoIds.contains(photo.getId());
        tile.setStyle(tileStyle(selected));
        tile.getProperties().put("photo", photo);

        // Double-click opens detail; single click toggles selection
        tile.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openPhotoDetail(photo);
            } else {
                selectBox.setSelected(!selectBox.isSelected());
                tile.setStyle(tileStyle(selectBox.isSelected()));
            }
        });
        // Keep border in sync with checkbox
        selectBox.selectedProperty().addListener((o, old, v) -> tile.setStyle(tileStyle(v)));

        return tile;
    }

    private String tileStyle(boolean selected) {
        return "-fx-background-color: white; -fx-border-radius: 4; " +
                "-fx-background-radius: 4; -fx-cursor: hand; -fx-border-width: 2; " +
                "-fx-border-color: " + (selected ? "#2c6fad;" : "#ddd;");
    }

    private void updateSelectionBadge() {
        int n = selectedPhotoIds.size();
        bulkTagBtn.setDisable(n == 0);
        bulkTagBtn.setText(n == 0 ? "🏷 Bulk Tag Selected" : "🏷 Bulk Tag (" + n + ")");
    }

    // =========================================================================
    // Google Drive
    // =========================================================================

    private void connectToDrive() {
        connectBtn.setDisable(true);
        connectBtn.setText("Connecting…");
        setStatus("Connecting to Google Drive…");
        new Thread(() -> {
            try {
                driveService.connect();
                List<File> folders = driveService.listEventFolders(null);
                Platform.runLater(() -> {
                    populateFolderTree(folders);
                    connectBtn.setText("✅ Connected");
                    syncBtn.setDisable(false);
                    setStatus("Connected — " + folders.size() + " folder(s) in My Drive");
                });
            } catch (IOException | GeneralSecurityException e) {
                Platform.runLater(() -> {
                    connectBtn.setDisable(false);
                    connectBtn.setText("🔗 Connect Google Drive");
                    setStatus("Connection failed.");
                    new Alert(Alert.AlertType.ERROR,
                            "Could not connect to Google Drive.\n\nMake sure credentials.json " +
                            "is in the project root.\n\n" + e.getMessage(),
                            ButtonType.OK).showAndWait();
                });
            }
        }).start();
    }

    private void populateFolderTree(List<File> folders) {
        TreeItem<String> root = folderTree.getRoot();
        root.getChildren().clear();
        for (File f : folders) {
            TreeItem<String> item = new TreeItem<>(f.getName());
            item.getProperties().put("driveId", f.getId());
            root.getChildren().add(item);
        }
    }

    private void onFolderSelected(TreeItem<String> item) {
        if (item == null || item == folderTree.getRoot()) return;
        Object id = item.getProperties().get("driveId");
        if (id instanceof String folderId) {
            selectedDriveFolderId = folderId;
            setStatus("Selected: " + item.getValue() + " — click Sync to load photos");
        }
    }

    private void syncSelectedFolder() {
        if (selectedDriveFolderId == null) {
            new Alert(Alert.AlertType.INFORMATION,
                    "Select an event folder from the left panel first.",
                    ButtonType.OK).showAndWait();
            return;
        }
        String folderName = folderTree.getSelectionModel().getSelectedItem().getValue();
        syncBtn.setDisable(true);
        setStatus("Syncing " + folderName + "…");

        new Thread(() -> {
            try {
                List<File> drivePhotos = driveService.listPhotosInFolder(selectedDriveFolderId);
                setStatusLater("Found " + drivePhotos.size() + " photos — downloading thumbnails…");

                for (int i = 0; i < drivePhotos.size(); i++) {
                    File f = drivePhotos.get(i);
                    Path thumb = driveService.downloadThumbnail(f);
                    Photo existing = photoDAO.findByFilePath(thumb.toString());
                    Photo photo;
                    if (existing != null) {
                        photo = existing;
                    } else {
                        photo = new Photo(0, thumb.toString(), f.getId(), folderName,
                                null, extractDate(f.getName()), null);
                        photoDAO.insertPhoto(photo);
                    }
                    photo.setDriveFolderName(folderName);
                    tagCache.put(photo.getId(), photoDAO.getTaggedMembers(photo.getId()));
                    allPhotos.add(photo);
                    final int idx = i + 1;
                    setStatusLater("Loaded " + idx + "/" + drivePhotos.size() + "…");
                }
                Platform.runLater(() -> {
                    syncBtn.setDisable(false);
                    setStatus(folderName + " — " + drivePhotos.size() + " photos loaded");
                    applyFiltersAndRebuild();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    syncBtn.setDisable(false);
                    setStatus("Sync failed: " + e.getMessage());
                    new Alert(Alert.AlertType.ERROR, "Sync failed: " + e.getMessage(),
                            ButtonType.OK).showAndWait();
                });
            }
        }).start();
    }

    // =========================================================================
    // Bulk tag
    // =========================================================================

    private void bulkTagSelected() {
        if (selectedPhotoIds.isEmpty()) return;
        ChoiceDialog<Member> choice = new ChoiceDialog<>(null, allMembers);
        choice.setTitle("Bulk Tag");
        choice.setHeaderText("Tag " + selectedPhotoIds.size() +
                " selected photo(s) with a member.\nExisting tags are kept.");
        choice.setContentText("Member:");
        choice.showAndWait().ifPresent(member -> new Thread(() -> {
            try {
                for (int photoId : selectedPhotoIds) {
                    photoDAO.tagMember(photoId, member.getId());
                    List<Member> tags = tagCache.computeIfAbsent(photoId, k -> new ArrayList<>());
                    if (tags.stream().noneMatch(m -> m.getId() == member.getId())) tags.add(member);
                }
                Platform.runLater(() -> {
                    selectedPhotoIds.clear();
                    updateSelectionBadge();
                    applyFiltersAndRebuild();
                });
            } catch (Exception e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
                        "Bulk tag failed: " + e.getMessage(), ButtonType.OK).showAndWait());
            }
        }).start());
    }

    // =========================================================================
    // Auto-rename
    // =========================================================================

    private void autoRenamePhotos() {
        if (allPhotos.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION,
                    "Sync a folder first before renaming.", ButtonType.OK).showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Renames cached photo files to match their event folder.\n\n" +
                "Example: 'IMG_4821.jpg' → 'Spring_Concert_2024_001.jpg'\n\nContinue?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Auto-Rename Photos");
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        new Thread(() -> {
            int renamed = 0;
            Map<String, Integer> counters = new HashMap<>();
            for (Photo photo : allPhotos) {
                if (photo.getFilePath() == null) continue;
                Path src = Path.of(photo.getFilePath());
                if (!Files.exists(src)) continue;
                String folder = photo.getDriveFolderName() != null
                        ? photo.getDriveFolderName() : "Unsorted";
                String safeFolder = folder.replaceAll("[^a-zA-Z0-9 _-]", "").replace(" ", "_");
                int idx = counters.merge(folder, 1, Integer::sum);
                String ext = getExtension(src.getFileName().toString());
                Path dest = src.getParent().resolve(
                        String.format("%s_%03d%s", safeFolder, idx, ext));
                try {
                    if (!Files.exists(dest)) {
                        Files.move(src, dest);
                        photo.setFilePath(dest.toString());
                        photoDAO.insertPhoto(photo);
                        renamed++;
                    }
                } catch (Exception ignored) {}
            }
            final int count = renamed;
            Platform.runLater(() -> {
                applyFiltersAndRebuild();
                new Alert(Alert.AlertType.INFORMATION,
                        "Renamed " + count + " file(s).", ButtonType.OK).showAndWait();
            });
        }).start();
    }

    // =========================================================================
    // Photo detail
    // =========================================================================

    private void openPhotoDetail(Photo photo) {
        if (photo.getFilePath() == null || !Files.exists(Path.of(photo.getFilePath()))) {
            new Alert(Alert.AlertType.WARNING,
                    "Photo file not found on disk. Try syncing again.",
                    ButtonType.OK).showAndWait();
            return;
        }
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(photo.getDisplayName()
                + (photo.getDriveFolderName() != null ? " — " + photo.getDriveFolderName() : ""));

        PhotoDetailView detail = new PhotoDetailView(
                photo, Path.of(photo.getFilePath()), allMembers, photoDAO, faceService,
                __ -> new Thread(() -> {
                    try {
                        tagCache.put(photo.getId(), photoDAO.getTaggedMembers(photo.getId()));
                        Platform.runLater(this::applyFiltersAndRebuild);
                    } catch (Exception ignored) {}
                }).start());

        Scene scene = new Scene(detail, 980, 620);
        try { scene.getStylesheets().add(
                getClass().getResource("/css/main.css").toExternalForm()); }
        catch (Exception ignored) {}
        stage.setScene(scene);
        stage.show();
    }

    // =========================================================================
    // Export
    // =========================================================================

    private void exportMemberPhotos() {
        ChoiceDialog<Member> choice = new ChoiceDialog<>(null, allMembers);
        choice.setTitle("Export Member Photos");
        choice.setHeaderText("Select a member to export all their tagged photos:");
        choice.showAndWait().ifPresent(member -> {
            javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
            dc.setTitle("Export folder for " + member.getFullName());
            java.io.File dir = dc.showDialog(getScene().getWindow());
            if (dir == null) return;
            new Thread(() -> {
                try {
                    List<Photo> photos = photoDAO.findByMember(member.getId());
                    int count = 0;
                    for (Photo p : photos) {
                        if (p.getFilePath() == null) continue;
                        Path src = Path.of(p.getFilePath());
                        if (!Files.exists(src)) continue;
                        Files.copy(src, dir.toPath().resolve(src.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                        count++;
                    }
                    final int c = count;
                    Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION,
                            "Exported " + c + " photo(s) for " + member.getFullName(),
                            ButtonType.OK).showAndWait());
                } catch (Exception e) {
                    Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
                            "Export failed: " + e.getMessage(), ButtonType.OK).showAndWait());
                }
            }).start();
        });
    }

    // =========================================================================
    // Selection helpers
    // =========================================================================

    private void selectAllInFolder(String folderName) {
        allPhotos.stream().filter(p -> folderName.equals(p.getDriveFolderName()))
                .forEach(p -> selectedPhotoIds.add(p.getId()));
        updateSelectionBadge();
        applyFiltersAndRebuild();
    }

    private void deselectAllInFolder(String folderName) {
        allPhotos.stream().filter(p -> folderName.equals(p.getDriveFolderName()))
                .forEach(p -> selectedPhotoIds.remove(p.getId()));
        updateSelectionBadge();
        applyFiltersAndRebuild();
    }

    // =========================================================================
    // Background init
    // =========================================================================

    private void loadMembersAsync() {
        new Thread(() -> {
            try {
                List<Member> members = memberDAO.findActive();
                Platform.runLater(() -> allMembers = members);
            } catch (Exception ignored) {}
        }).start();
    }

    private void loadAIModelAsync() {
        setStatus("Loading AI face detection model…");
        new Thread(() -> {
            try {
                faceService.load();
                Platform.runLater(() -> setStatus("AI ready — connect Google Drive to get started"));
            } catch (Exception e) {
                Platform.runLater(() -> setStatus(
                        "AI model unavailable — manual tagging still works"));
            }
        }).start();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void setStatus(String msg) { statusLabel.setText(msg); }
    private void setStatusLater(String msg) { Platform.runLater(() -> setStatus(msg)); }
    private String nullSafe(String s) { return s != null ? s : ""; }

    private String extractDate(String filename) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(filename);
        return m.find() ? m.group(1) : null;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".jpg";
    }
}
