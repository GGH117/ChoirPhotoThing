package com.choirmanager.ui.photos;

import com.choirmanager.db.PhotoDAO;
import com.choirmanager.model.DetectedFace;
import com.choirmanager.model.Member;
import com.choirmanager.model.Photo;
import com.choirmanager.service.FaceRecognitionService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Shows a single photo with:
 *  - Full-size image + face bounding box overlay (drawn on a Canvas)
 *  - Right panel: list of tagged members
 *  - "Run AI Tagging" button to trigger detection + TagConfirmDialog
 *  - "Enroll Member" button to use this photo as a member reference face
 */
public class PhotoDetailView extends BorderPane {

    private final Photo photo;
    private final Path photoPath;
    private final List<Member> allMembers;
    private final PhotoDAO photoDAO;
    private final FaceRecognitionService faceService;
    private final Consumer<Void> onTagsChanged; // callback to refresh gallery

    // UI elements
    private final ImageView imageView = new ImageView();
    private Canvas overlayCanvas;
    private final VBox tagList = new VBox(6);
    private List<DetectedFace> lastDetectedFaces;
    private Image loadedImage;

    public PhotoDetailView(Photo photo, Path photoPath, List<Member> allMembers,
                           PhotoDAO photoDAO, FaceRecognitionService faceService,
                           Consumer<Void> onTagsChanged) {
        this.photo = photo;
        this.photoPath = photoPath;
        this.allMembers = allMembers;
        this.photoDAO = photoDAO;
        this.faceService = faceService;
        this.onTagsChanged = onTagsChanged;
        buildUI();
        loadPhoto();
        loadExistingTags();
    }

    // -------------------------------------------------------------------------
    // UI Construction
    // -------------------------------------------------------------------------

    private void buildUI() {
        // Image + overlay canvas stacked
        StackPane imageStack = new StackPane();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(700);
        imageView.setFitHeight(500);
        overlayCanvas = new Canvas(700, 500);
        imageStack.getChildren().addAll(imageView, overlayCanvas);
        imageStack.setStyle("-fx-background-color: #1a1a1a;");

        // Right panel
        Label tagTitle = new Label("Tagged Members");
        tagTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Button runAIBtn     = new Button("🤖 Run AI Tagging");
        Button enrollBtn    = new Button("📸 Enroll Member Face");
        Button addTagBtn    = new Button("+ Tag Member Manually");
        Button exportBtn    = new Button("💾 Export Photo");

        runAIBtn.setMaxWidth(Double.MAX_VALUE);
        enrollBtn.setMaxWidth(Double.MAX_VALUE);
        addTagBtn.setMaxWidth(Double.MAX_VALUE);
        exportBtn.setMaxWidth(Double.MAX_VALUE);

        runAIBtn.setOnAction(e  -> runAITagging());
        enrollBtn.setOnAction(e -> enrollMemberFace());
        addTagBtn.setOnAction(e -> manualTag());
        exportBtn.setOnAction(e -> exportPhoto());

        Label photoName = new Label(photo.getDisplayName());
        photoName.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        photoName.setWrapText(true);

        VBox rightPanel = new VBox(10,
                tagTitle,
                new Separator(),
                tagList,
                new Separator(),
                runAIBtn,
                enrollBtn,
                addTagBtn,
                exportBtn,
                new Region(),
                photoName);
        VBox.setVgrow(rightPanel.getChildren().get(6), Priority.ALWAYS);
        rightPanel.setPadding(new Insets(12));
        rightPanel.setPrefWidth(220);
        rightPanel.setStyle("-fx-background-color: #f8f8f8; -fx-border-color: #ddd; -fx-border-width: 0 0 0 1;");

        setCenter(imageStack);
        setRight(rightPanel);
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    private void loadPhoto() {
        try {
            loadedImage = new Image(photoPath.toUri().toString(), true); // background loading
            imageView.setImage(loadedImage);
            loadedImage.progressProperty().addListener((o, old, prog) -> {
                if (prog.doubleValue() >= 1.0) resizeCanvasToImage();
            });
        } catch (Exception e) {
            showError("Could not load image: " + e.getMessage());
        }
    }

    private void resizeCanvasToImage() {
        double imgW = loadedImage.getWidth();
        double imgH = loadedImage.getHeight();
        double viewW = imageView.getFitWidth();
        double viewH = imageView.getFitHeight();
        double scale = Math.min(viewW / imgW, viewH / imgH);
        overlayCanvas.setWidth(imgW * scale);
        overlayCanvas.setHeight(imgH * scale);
    }

    private void loadExistingTags() {
        new Thread(() -> {
            try {
                List<Member> tags = photoDAO.getTaggedMembers(photo.getId());
                Platform.runLater(() -> renderTagList(tags));
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to load tags: " + e.getMessage()));
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Face overlay
    // -------------------------------------------------------------------------

    private void drawFaceBoxes(List<DetectedFace> faces) {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());

        double cw = overlayCanvas.getWidth();
        double ch = overlayCanvas.getHeight();

        for (DetectedFace face : faces) {
            double x = face.getX() * cw;
            double y = face.getY() * ch;
            double w = face.getWidth()  * cw;
            double h = face.getHeight() * ch;

            Member resolved = face.getResolvedMember();
            Color boxColor = resolved != null ? Color.LIMEGREEN : Color.ORANGERED;

            gc.setStroke(boxColor);
            gc.setLineWidth(2.5);
            gc.strokeRect(x, y, w, h);

            // Label
            String label = resolved != null ? resolved.getFullName() : "Unknown";
            if (face.getConfidence() > 0) {
                label += String.format(" (%.0f%%)", face.getConfidence() * 100);
            }

            gc.setFill(boxColor.deriveColor(0, 1, 1, 0.75));
            gc.fillRect(x, y - 18, Math.min(w, label.length() * 7.5 + 8), 18);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font(12));
            gc.fillText(label, x + 4, y - 4);
        }
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void runAITagging() {
        if (!faceService.isLoaded()) {
            showInfo("AI model is still loading — please wait a moment and try again.");
            return;
        }
        Button btn = (Button) getRight().lookup(".button");
        setDisableButtons(true);

        new Thread(() -> {
            try {
                List<DetectedFace> faces = faceService.detectAndMatch(photoPath, allMembers);
                lastDetectedFaces = faces;

                Platform.runLater(() -> {
                    drawFaceBoxes(faces);
                    setDisableButtons(false);

                    if (faces.isEmpty()) {
                        showInfo("No faces detected in this photo.");
                        return;
                    }

                    TagConfirmDialog dialog = new TagConfirmDialog(faces, allMembers, photoPath);
                    Optional<List<DetectedFace>> result = dialog.showAndWait();
                    result.ifPresent(this::saveConfirmedTags);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setDisableButtons(false);
                    showError("AI tagging failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void saveConfirmedTags(List<DetectedFace> confirmedFaces) {
        new Thread(() -> {
            try {
                photoDAO.removeAllTags(photo.getId());
                for (DetectedFace face : confirmedFaces) {
                    Member m = face.getConfirmedMember();
                    if (m != null) {
                        photoDAO.tagMember(photo.getId(), m.getId());
                    }
                }
                List<Member> updated = photoDAO.getTaggedMembers(photo.getId());
                Platform.runLater(() -> {
                    renderTagList(updated);
                    drawFaceBoxes(confirmedFaces);
                    if (onTagsChanged != null) onTagsChanged.accept(null);
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to save tags: " + e.getMessage()));
            }
        }).start();
    }

    private void enrollMemberFace() {
        // Prompt user to select the member to enroll
        ChoiceDialog<Member> choice = new ChoiceDialog<>(null, allMembers);
        choice.setTitle("Enroll Member Face");
        choice.setHeaderText("This photo will be used as a reference face for the selected member.\nMake sure the member's face is clearly visible.");
        choice.setContentText("Select member:");

        Optional<Member> result = choice.showAndWait();
        result.ifPresent(member -> {
            new Thread(() -> {
                try {
                    faceService.enrollMember(photoPath, member, photo.getId());
                    Platform.runLater(() ->
                        showInfo(member.getFullName() + " has been enrolled. Future photos will be matched against their face."));
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Enrollment failed: " + e.getMessage()));
                }
            }).start();
        });
    }

    private void manualTag() {
        ChoiceDialog<Member> choice = new ChoiceDialog<>(null, allMembers);
        choice.setTitle("Tag Member");
        choice.setHeaderText("Select a member to tag in this photo:");
        choice.showAndWait().ifPresent(member -> {
            new Thread(() -> {
                try {
                    photoDAO.tagMember(photo.getId(), member.getId());
                    List<Member> updated = photoDAO.getTaggedMembers(photo.getId());
                    Platform.runLater(() -> {
                        renderTagList(updated);
                        if (onTagsChanged != null) onTagsChanged.accept(null);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Failed to tag: " + e.getMessage()));
                }
            }).start();
        });
    }

    private void exportPhoto() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export Photo");
        fc.setInitialFileName(photo.getDisplayName());
        fc.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        java.io.File dest = fc.showSaveDialog(getScene().getWindow());
        if (dest == null) return;
        try {
            java.nio.file.Files.copy(photoPath, dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            showInfo("Photo exported to " + dest.getAbsolutePath());
        } catch (Exception e) {
            showError("Export failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Tag list rendering
    // -------------------------------------------------------------------------

    private void renderTagList(List<Member> tags) {
        tagList.getChildren().clear();
        if (tags.isEmpty()) {
            tagList.getChildren().add(new Label("No members tagged yet."));
            return;
        }
        for (Member m : tags) {
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(m.getFullName());
            Button removeBtn = new Button("✕");
            removeBtn.setStyle("-fx-padding: 1 5 1 5; -fx-font-size: 10px;");
            removeBtn.setOnAction(e -> removeTag(m, tags));
            row.getChildren().addAll(name, removeBtn);
            tagList.getChildren().add(row);
        }
    }

    private void removeTag(Member m, List<Member> currentTags) {
        new Thread(() -> {
            try {
                photoDAO.removeTag(photo.getId(), m.getId());
                List<Member> updated = photoDAO.getTaggedMembers(photo.getId());
                Platform.runLater(() -> {
                    renderTagList(updated);
                    if (onTagsChanged != null) onTagsChanged.accept(null);
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to remove tag: " + e.getMessage()));
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setDisableButtons(boolean disabled) {
        if (getRight() instanceof VBox vbox) {
            vbox.getChildren().stream()
                .filter(n -> n instanceof Button)
                .forEach(n -> n.setDisable(disabled));
        }
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}
