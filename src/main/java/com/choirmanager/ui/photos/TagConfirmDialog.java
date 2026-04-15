package com.choirmanager.ui.photos;

import com.choirmanager.model.DetectedFace;
import com.choirmanager.model.Member;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.nio.file.Path;
import java.util.List;

/**
 * Reworked tag confirmation dialog.
 *
 * Shows detected faces in a horizontal card row — each card has:
 *   ┌──────────────────┐
 *   │  [face crop]     │
 *   │  Face 1          │
 *   │  AI: John S. 87% │
 *   │  [dropdown ▼]    │
 *   └──────────────────┘
 *
 * Cards wrap into multiple rows if there are many faces.
 * Much easier to scan than a tall vertical scroll list.
 */
public class TagConfirmDialog extends Dialog<List<DetectedFace>> {

    private static final int CARD_WIDTH  = 160;
    private static final int THUMB_SIZE  = 120;

    private final List<DetectedFace> faces;
    private final List<Member> allMembers;
    private final Path photoPath;

    public TagConfirmDialog(List<DetectedFace> faces, List<Member> allMembers, Path photoPath) {
        this.faces      = faces;
        this.allMembers = allMembers;
        this.photoPath  = photoPath;

        setTitle("Confirm Face Tags");
        setResizable(true);
        buildContent();
        getDialogPane().getButtonTypes().addAll(
                new ButtonType("Save Tags", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL);
        setResultConverter(bt ->
                bt.getButtonData() == ButtonBar.ButtonData.OK_DONE ? faces : null);
    }

    private void buildContent() {
        // Header summary
        long suggested  = faces.stream().filter(f -> f.getSuggestedMember() != null).count();
        long unmatched  = faces.size() - suggested;
        Label header = new Label(
                faces.size() + " face(s) detected — "
                + suggested + " matched by AI, " + unmatched + " unmatched.");
        header.setStyle("-fx-font-size: 13px; -fx-padding: 0 0 8 0;");

        // Load full photo for face crops
        Image fullImage = null;
        try { fullImage = new Image(photoPath.toUri().toString()); }
        catch (Exception ignored) {}

        // Face cards in a wrapping flow
        FlowPane cardGrid = new FlowPane();
        cardGrid.setHgap(10);
        cardGrid.setVgap(10);
        cardGrid.setPadding(new Insets(8, 4, 8, 4));

        for (int i = 0; i < faces.size(); i++) {
            cardGrid.getChildren().add(buildCard(i, faces.get(i), fullImage));
        }

        VBox content = new VBox(8, header, cardGrid);
        content.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        // Show ~2 rows of cards without needing to scroll in most cases
        scroll.setPrefHeight(Math.min(600, (faces.size() / 4 + 1) * 260 + 80));
        scroll.setPrefWidth(Math.min(760, faces.size() * (CARD_WIDTH + 10) + 40));

        getDialogPane().setContent(scroll);
    }

    private VBox buildCard(int index, DetectedFace face, Image fullImage) {
        // ── Face crop ─────────────────────────────────────────────────────
        ImageView cropView = new ImageView();
        cropView.setFitWidth(THUMB_SIZE);
        cropView.setFitHeight(THUMB_SIZE);
        cropView.setPreserveRatio(true);

        if (fullImage != null && !fullImage.isError()) {
            PixelReader pr = fullImage.getPixelReader();
            int imgW = (int) fullImage.getWidth();
            int imgH = (int) fullImage.getHeight();
            int cx = clamp((int)(face.getX() * imgW), 0, imgW - 1);
            int cy = clamp((int)(face.getY() * imgH), 0, imgH - 1);
            int cw = clamp((int)(face.getWidth()  * imgW), 1, imgW - cx);
            int ch = clamp((int)(face.getHeight() * imgH), 1, imgH - cy);
            cropView.setImage(new WritableImage(pr, cx, cy, cw, ch));
        }

        // Rounded clip
        Rectangle clip = new Rectangle(THUMB_SIZE, THUMB_SIZE);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        cropView.setClip(clip);

        // ── Labels ────────────────────────────────────────────────────────
        Label faceNum = new Label("Face " + (index + 1));
        faceNum.setFont(Font.font(null, FontWeight.BOLD, 12));

        String aiText;
        Color  aiColor;
        if (face.getSuggestedMember() != null) {
            int pct = (int)(face.getConfidence() * 100);
            aiText  = "AI: " + face.getSuggestedMember().getFirstName() + " (" + pct + "%)";
            aiColor = pct >= 70 ? Color.web("#1a7a1a") : Color.web("#b36a00");
        } else {
            aiText  = "AI: no match";
            aiColor = Color.GRAY;
        }
        Label aiLabel = new Label(aiText);
        aiLabel.setTextFill(aiColor);
        aiLabel.setStyle("-fx-font-size: 11px;");

        // ── Dropdown ──────────────────────────────────────────────────────
        ComboBox<Member> combo = new ComboBox<>();
        combo.getItems().add(null);
        combo.getItems().addAll(allMembers);
        combo.setCellFactory(lv -> memberCell());
        combo.setButtonCell(memberCell());
        combo.setPromptText("— Unknown —");
        combo.setPrefWidth(CARD_WIDTH - 12);

        // Pre-select AI suggestion
        combo.setValue(face.getSuggestedMember());
        face.setConfirmedMember(face.getSuggestedMember());
        combo.valueProperty().addListener((o, old, v) -> face.setConfirmedMember(v));

        // ── Card container ────────────────────────────────────────────────
        VBox card = new VBox(6, cropView, faceNum, aiLabel, combo);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(10));
        card.setPrefWidth(CARD_WIDTH);
        card.setStyle("-fx-background-color: white; -fx-border-color: #ddd; " +
                "-fx-border-radius: 6; -fx-background-radius: 6; " +
                "-fx-effect: dropshadow(gaussian, #ccc, 3, 0, 0, 1);");
        return card;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ListCell<Member> memberCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Member m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) setText("— Unknown / skip —");
                else setText(m.getFullName()
                        + (m.getVoicePart() != null ? " · " + m.getVoicePart() : ""));
            }
        };
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
