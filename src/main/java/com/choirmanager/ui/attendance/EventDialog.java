package com.choirmanager.ui.attendance;

import com.choirmanager.model.Event;
import com.choirmanager.model.Event.EventType;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;

/**
 * Dialog for creating or editing an event.
 */
public class EventDialog extends Dialog<Event> {

    private final TextField titleField      = new TextField();
    private final DatePicker datePicker     = new DatePicker();
    private final TextField startTimeField  = new TextField();
    private final TextField endTimeField    = new TextField();
    private final TextField locationField   = new TextField();
    private final ComboBox<EventType> typeBox = new ComboBox<>();
    private final TextArea notesArea        = new TextArea();

    private final Event existing;

    public EventDialog(Event existing) {
        this.existing = existing;
        setTitle(existing == null ? "Add Event" : "Edit Event");
        setHeaderText(existing == null ? "Create a new event" : "Edit event details");

        buildContent();
        addButtons();
        populateIfEditing();
        setResultConverter(this::convertResult);
    }

    private void buildContent() {
        typeBox.getItems().addAll(EventType.values());
        typeBox.setValue(EventType.Rehearsal);

        startTimeField.setPromptText("e.g. 18:30");
        endTimeField.setPromptText("e.g. 20:00");
        datePicker.setValue(LocalDate.now());

        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(20));

        int row = 0;
        grid.add(new Label("Title*:"),      0, row); grid.add(titleField,     1, row++);
        grid.add(new Label("Type:"),        0, row); grid.add(typeBox,        1, row++);
        grid.add(new Label("Date*:"),       0, row); grid.add(datePicker,     1, row++);
        grid.add(new Label("Start Time:"),  0, row); grid.add(startTimeField, 1, row++);
        grid.add(new Label("End Time:"),    0, row); grid.add(endTimeField,   1, row++);
        grid.add(new Label("Location:"),    0, row); grid.add(locationField,  1, row++);
        grid.add(new Label("Notes:"),       0, row); grid.add(notesArea,      1, row);

        getDialogPane().setContent(grid);
        titleField.requestFocus();
    }

    private void addButtons() {
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        Button save = (Button) getDialogPane().lookupButton(saveBtn);
        save.setDisable(true);
        titleField.textProperty().addListener((o, old, n) ->
                save.setDisable(n.isBlank() || datePicker.getValue() == null));
        datePicker.valueProperty().addListener((o, old, n) ->
                save.setDisable(titleField.getText().isBlank() || n == null));
    }

    private void populateIfEditing() {
        if (existing == null) return;
        titleField.setText(existing.getTitle());
        typeBox.setValue(existing.getEventType());
        if (existing.getEventDate() != null)
            datePicker.setValue(LocalDate.parse(existing.getEventDate()));
        startTimeField.setText(existing.getStartTime() != null ? existing.getStartTime() : "");
        endTimeField.setText(existing.getEndTime() != null ? existing.getEndTime() : "");
        locationField.setText(existing.getLocation() != null ? existing.getLocation() : "");
        notesArea.setText(existing.getNotes() != null ? existing.getNotes() : "");
    }

    private Event convertResult(ButtonType bt) {
        if (bt.getButtonData() != ButtonBar.ButtonData.OK_DONE) return null;
        Event e = existing != null ? existing : new Event();
        e.setTitle(titleField.getText().trim());
        e.setEventType(typeBox.getValue());
        e.setEventDate(datePicker.getValue() != null ? datePicker.getValue().toString() : null);
        e.setStartTime(startTimeField.getText().trim());
        e.setEndTime(endTimeField.getText().trim());
        e.setLocation(locationField.getText().trim());
        e.setNotes(notesArea.getText().trim());
        return e;
    }
}
