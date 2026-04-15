package com.choirmanager.ui.attendance;

import com.choirmanager.db.AttendanceDAO;
import com.choirmanager.db.AttendanceDAO.AttendanceSummary;
import com.choirmanager.db.EventDAO;
import com.choirmanager.model.AttendanceRecord;
import com.choirmanager.model.AttendanceRecord.Status;
import com.choirmanager.model.Event;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Attendance module — two-panel layout.
 *
 * LEFT:  list of events with add/edit/delete controls.
 * RIGHT: attendance sheet for the selected event — one row per active member,
 *        editable status dropdown, save button, and a summary bar.
 */
public class AttendanceView extends BorderPane {

    private final EventDAO      eventDAO;
    private final AttendanceDAO attendanceDAO;

    // Left panel
    private final ObservableList<Event> eventList = FXCollections.observableArrayList();
    private final ListView<Event> eventListView   = new ListView<>(eventList);

    // Right panel
    private final ObservableList<AttendanceRecord> records = FXCollections.observableArrayList();
    private final TableView<AttendanceRecord> sheet        = new TableView<>(records);
    private final Label summaryLabel  = new Label();
    private final Label eventTitle    = new Label("← Select an event");
    private final Button saveBtn      = new Button("💾 Save Attendance");
    private final Button markAllBtn   = new Button("Mark All Present");

    public AttendanceView() throws SQLException {
        this.eventDAO      = new EventDAO();
        this.attendanceDAO = new AttendanceDAO();
        buildUI();
        loadEvents();
    }

    // =========================================================================
    // UI Construction
    // =========================================================================

    private void buildUI() {
        // ---- LEFT: event list -----------------------------------------------
        Label eventsHeader = new Label("Events");
        eventsHeader.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Button addEventBtn    = new Button("+ Add");
        Button editEventBtn   = new Button("Edit");
        Button deleteEventBtn = new Button("Delete");

        addEventBtn.setOnAction(e    -> openEventDialog(null));
        editEventBtn.setOnAction(e   -> editSelectedEvent());
        deleteEventBtn.setOnAction(e -> deleteSelectedEvent());

        HBox eventBtns = new HBox(6, addEventBtn, editEventBtn, deleteEventBtn);

        eventListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Event item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.getEventDate() + "\n" + item.getTitle()
                        + " [" + item.getEventType() + "]");
            }
        });
        eventListView.setPrefWidth(220);
        eventListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> {
                    if (selected != null) loadAttendanceSheet(selected);
                });

        VBox leftPanel = new VBox(8, eventsHeader, eventBtns, eventListView);
        VBox.setVgrow(eventListView, Priority.ALWAYS);
        leftPanel.setPadding(new Insets(12));

        // ---- RIGHT: attendance sheet ----------------------------------------
        eventTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        saveBtn.setDisable(true);
        saveBtn.setOnAction(e -> saveAttendance());

        markAllBtn.setDisable(true);
        markAllBtn.setOnAction(e -> markAll(Status.Present));

        Button markAllAbsentBtn = new Button("Mark All Absent");
        markAllAbsentBtn.setDisable(true);
        markAllAbsentBtn.setOnAction(e -> markAll(Status.Absent));
        // Keep refs so we can toggle disabled state
        markAllBtn.setUserData(markAllAbsentBtn);

        HBox rightToolbar = new HBox(10, eventTitle, new Region(),
                markAllBtn, markAllAbsentBtn, saveBtn);
        HBox.setHgrow(rightToolbar.getChildren().get(1), Priority.ALWAYS);
        rightToolbar.setAlignment(Pos.CENTER_LEFT);
        rightToolbar.setPadding(new Insets(12, 12, 6, 12));

        buildSheet();

        HBox summaryBar = new HBox(summaryLabel);
        summaryBar.setPadding(new Insets(4, 12, 4, 12));
        summaryBar.setStyle("-fx-background-color: #e8edf2;");

        VBox rightPanel = new VBox(rightToolbar, sheet, summaryBar);
        VBox.setVgrow(sheet, Priority.ALWAYS);

        // ---- Splitter -------------------------------------------------------
        SplitPane split = new SplitPane(leftPanel, rightPanel);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.25);

        setCenter(split);
    }

    @SuppressWarnings("unchecked")
    private void buildSheet() {
        sheet.setEditable(true);
        sheet.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<AttendanceRecord, String> nameCol = new TableColumn<>("Member");
        nameCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getMemberName()));

        TableColumn<AttendanceRecord, String> vpCol = new TableColumn<>("Voice Part");
        vpCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getVoicePart() != null
                        ? c.getValue().getVoicePart() : ""));
        vpCol.setMaxWidth(110);

        TableColumn<AttendanceRecord, Status> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getStatus()));
        statusCol.setCellFactory(ComboBoxTableCell.forTableColumn(Status.values()));
        statusCol.setOnEditCommit(e -> {
            e.getRowValue().setStatus(e.getNewValue());
            updateSummary();
        });
        statusCol.setMaxWidth(130);

        TableColumn<AttendanceRecord, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getNotes() != null
                        ? c.getValue().getNotes() : ""));
        notesCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        notesCol.setOnEditCommit(e -> e.getRowValue().setNotes(e.getNewValue()));

        sheet.getColumns().addAll(nameCol, vpCol, statusCol, notesCol);
    }

    // =========================================================================
    // Data Loading
    // =========================================================================

    private void loadEvents() {
        try {
            eventList.setAll(eventDAO.findAll());
        } catch (SQLException ex) {
            showError("Failed to load events: " + ex.getMessage());
        }
    }

    private void loadAttendanceSheet(Event event) {
        try {
            List<AttendanceRecord> data = attendanceDAO.findForEvent(event.getId());
            // Default unrecorded members to Present
            data.stream().filter(r -> r.getStatus() == null)
                    .forEach(r -> r.setStatus(Status.Present));
            records.setAll(data);

            eventTitle.setText(event.getEventDate() + "  —  " + event.getTitle());
            saveBtn.setDisable(false);
            markAllBtn.setDisable(false);
            if (markAllBtn.getUserData() instanceof Button b) b.setDisable(false);
            updateSummary();
        } catch (SQLException ex) {
            showError("Failed to load attendance: " + ex.getMessage());
        }
    }

    // =========================================================================
    // Actions
    // =========================================================================

    private void saveAttendance() {
        Event selected = eventListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            attendanceDAO.saveForEvent(selected.getId(), records);
            updateSummary();
            showInfo("Attendance saved for " + selected.getTitle() + ".");
        } catch (SQLException ex) {
            showError("Failed to save attendance: " + ex.getMessage());
        }
    }

    private void markAll(Status status) {
        records.forEach(r -> r.setStatus(status));
        sheet.refresh();
        updateSummary();
    }

    private void updateSummary() {
        long present = records.stream().filter(r -> r.getStatus() == Status.Present).count();
        long late    = records.stream().filter(r -> r.getStatus() == Status.Late).count();
        long absent  = records.stream().filter(r -> r.getStatus() == Status.Absent).count();
        long excused = records.stream().filter(r -> r.getStatus() == Status.Excused).count();
        long total   = records.size();
        long attending = present + late;

        summaryLabel.setText(String.format(
                "Total: %d  |  ✅ Present: %d  |  ⏰ Late: %d  |  ❌ Absent: %d  |  🔵 Excused: %d  |  Attendance rate: %.0f%%",
                total, present, late, absent, excused,
                total > 0 ? attending * 100.0 / total : 0.0));
    }

    private void openEventDialog(Event existing) {
        EventDialog dialog = new EventDialog(existing);
        Optional<Event> result = dialog.showAndWait();
        result.ifPresent(ev -> {
            try {
                if (ev.getId() == 0) eventDAO.insert(ev);
                else                 eventDAO.update(ev);
                loadEvents();
            } catch (SQLException ex) {
                showError("Failed to save event: " + ex.getMessage());
            }
        });
    }

    private void editSelectedEvent() {
        Event selected = eventListView.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Select an event to edit."); return; }
        openEventDialog(selected);
    }

    private void deleteSelectedEvent() {
        Event selected = eventListView.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Select an event to delete."); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selected.getTitle() + "\" and all its attendance records?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        Optional<ButtonType> r = confirm.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.YES) {
            try {
                eventDAO.delete(selected.getId());
                records.clear();
                eventTitle.setText("← Select an event");
                saveBtn.setDisable(true);
                markAllBtn.setDisable(true);
                summaryLabel.setText("");
                loadEvents();
            } catch (SQLException ex) {
                showError("Failed to delete event: " + ex.getMessage());
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}
