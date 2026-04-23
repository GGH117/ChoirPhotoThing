package com.choirmanager.ui.photos;

import com.choirmanager.service.DriveAutoSortService;
import com.choirmanager.service.DriveAutoSortService.ResultStatus;
import com.choirmanager.service.DriveAutoSortService.SortResult;
import com.choirmanager.service.DriveAutoSortService.SortSummary;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Modal dialog that drives the auto-sort process and displays live progress.
 *
 * Phases:
 *   1. "Ready" — shows options (root folder ID, dry-run toggle)
 *   2. "Running" — live progress bar + scrolling log
 *   3. "Done" — summary stats + per-photo result table
 */
public class AutoSortDialog extends Dialog<SortSummary> {

    private final DriveAutoSortService sortService;

    // Phase 1 — config
    private final TextField rootFolderField = new TextField();

    // Phase 2 — progress
    private final ProgressBar progressBar   = new ProgressBar(0);
    private final Label statusLabel         = new Label("Starting…");
    private final TextArea logArea          = new TextArea();

    // Phase 3 — results
    private final TableView<SortResult> resultTable = new TableView<>();

    // Nav buttons (kept as fields so we can swap labels)
    private Button runButton;
    private Button closeButton;

    private SortSummary finalSummary;

    // =========================================================================
    // Construction
    // =========================================================================

    public AutoSortDialog(DriveAutoSortService sortService) {
        this.sortService = sortService;

        setTitle("Auto-Sort Google Drive Photos");
        setResizable(true);
        getDialogPane().setPrefWidth(720);
        getDialogPane().setPrefHeight(520);

        buildContent();
        buildButtons();
        setResultConverter(bt -> finalSummary);
    }

    // =========================================================================
    // UI
    // =========================================================================

    private void buildContent() {
        // ── Config section ────────────────────────────────────────────────
        Label heading = new Label("Auto-Sort Drive Photos");
        heading.setFont(Font.font(null, FontWeight.BOLD, 16));

        Label desc = new Label(
            "Scans every folder in your Drive, downloads thumbnails, and links photos\n" +
            "to matching choir events by date. New events are created automatically\n" +
            "for unrecognised folders.");
        desc.setStyle("-fx-text-fill: #555; -fx-font-size: 12px;");

        rootFolderField.setPromptText("Leave blank to scan all of My Drive");
        rootFolderField.setPrefWidth(320);

        GridPane configGrid = new GridPane();
        configGrid.setHgap(10);
        configGrid.setVgap(8);
        configGrid.add(new Label("Drive folder ID (optional):"), 0, 0);
        configGrid.add(rootFolderField, 1, 0);

        // ── Progress section (hidden until run starts) ────────────────────
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(18);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #444;");

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(160);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        logArea.setVisible(false);
        logArea.setManaged(false);

        // ── Results table (hidden until done) ────────────────────────────
        buildResultTable();
        resultTable.setVisible(false);
        resultTable.setManaged(false);
        resultTable.setPrefHeight(200);

        // Summary row (shown after run)
        Label summaryRow = new Label();
        summaryRow.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 4 0 0 0;");
        summaryRow.setVisible(false);
        summaryRow.setManaged(false);

        // ── Root layout ───────────────────────────────────────────────────
        VBox content = new VBox(12,
                heading, desc, new Separator(), configGrid,
                progressBar, statusLabel, logArea,
                summaryRow, resultTable);
        content.setPadding(new Insets(16));

        getDialogPane().setContent(content);

        // Store references for later updates
        getDialogPane().getProperties().put("summaryRow", summaryRow);
    }

    @SuppressWarnings("unchecked")
    private void buildResultTable() {
        TableColumn<SortResult, String> folderCol = new TableColumn<>("Folder");
        folderCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().folderName()));
        folderCol.setPrefWidth(170);

        TableColumn<SortResult, String> photoCol = new TableColumn<>("Photo");
        photoCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        c.getValue().photoName() != null ? c.getValue().photoName() : "—"));
        photoCol.setPrefWidth(170);

        TableColumn<SortResult, String> eventCol = new TableColumn<>("Matched Event");
        eventCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        c.getValue().eventTitle() != null ? c.getValue().eventTitle() : "—"));
        eventCol.setPrefWidth(170);

        TableColumn<SortResult, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        c.getValue().status().name()));
        statusCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "IMPORTED" -> "-fx-text-fill: #1a7a1a; -fx-font-weight: bold;";
                    case "ERROR"    -> "-fx-text-fill: #b00; -fx-font-weight: bold;";
                    default         -> "-fx-text-fill: #888;";
                });
            }
        });
        statusCol.setPrefWidth(90);

        TableColumn<SortResult, String> detailCol = new TableColumn<>("Detail");
        detailCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        c.getValue().detail() != null ? c.getValue().detail() : ""));
        detailCol.setPrefWidth(110);

        resultTable.getColumns().addAll(folderCol, photoCol, eventCol, statusCol, detailCol);
        resultTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void buildButtons() {
        ButtonType runType   = new ButtonType("▶  Start Auto-Sort", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeType = new ButtonType("Close",              ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(runType, closeType);

        runButton   = (Button) getDialogPane().lookupButton(runType);
        closeButton = (Button) getDialogPane().lookupButton(closeType);

        // Prevent the dialog from closing when Run is clicked
        runButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            startSort();
        });
    }

    // =========================================================================
    // Sort execution
    // =========================================================================

    private void startSort() {
        runButton.setDisable(true);
        runButton.setText("Running…");

        // Show log, hide config
        logArea.setVisible(true);
        logArea.setManaged(true);
        logArea.clear();

        String rootId = rootFolderField.getText().trim();
        String effectiveRoot = rootId.isBlank() ? null : rootId;

        new Thread(() -> {
            try {
                SortSummary summary = sortService.sortAll(effectiveRoot, (msg, frac) ->
                    Platform.runLater(() -> {
                        statusLabel.setText(msg);
                        progressBar.setProgress(frac);
                        logArea.appendText(msg + "\n");
                        logArea.setScrollTop(Double.MAX_VALUE);
                    })
                );
                Platform.runLater(() -> showResults(summary));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    logArea.appendText("\n❌ Fatal error: " + e.getMessage() + "\n");
                    runButton.setDisable(false);
                    runButton.setText("▶  Retry");
                });
            }
        }).start();
    }

    // =========================================================================
    // Results display
    // =========================================================================

    private void showResults(SortSummary summary) {
        finalSummary = summary;

        progressBar.setProgress(1.0);
        statusLabel.setText("Complete!");

        // Show summary label
        Label summaryRow = (Label) getDialogPane().getProperties().get("summaryRow");
        summaryRow.setText(summary.summary());
        summaryRow.setVisible(true);
        summaryRow.setManaged(true);

        // Colour summary by whether errors occurred
        if (summary.errors() > 0) {
            summaryRow.setStyle("-fx-text-fill: #b00; -fx-font-weight: bold;");
        } else {
            summaryRow.setStyle("-fx-text-fill: #1a7a1a; -fx-font-weight: bold;");
        }

        // Populate results table
        resultTable.getItems().setAll(summary.details());
        resultTable.setVisible(true);
        resultTable.setManaged(true);

        // Shrink log area now that results table is showing
        logArea.setPrefHeight(80);

        runButton.setText("▶  Run Again");
        runButton.setDisable(false);

        // Close button now returns the summary
        closeButton.setText("Done");
    }
}
