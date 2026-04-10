package com.choirmanager.ui.roster;

import com.choirmanager.model.Member;
import com.choirmanager.model.Member.VoicePart;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;

/**
 * Dialog for adding or editing a choir member.
 * Returns the populated Member object on confirmation.
 */
public class MemberDialog extends Dialog<Member> {

    private final TextField firstNameField  = new TextField();
    private final TextField lastNameField   = new TextField();
    private final TextField emailField      = new TextField();
    private final TextField phoneField      = new TextField();
    private final ComboBox<VoicePart> vpBox = new ComboBox<>();
    private final DatePicker joinDatePicker = new DatePicker();
    private final CheckBox  activeCheck     = new CheckBox("Active member");
    private final TextArea  notesArea       = new TextArea();

    private final Member existing;

    public MemberDialog(Member existing) {
        this.existing = existing;
        setTitle(existing == null ? "Add Member" : "Edit Member");
        setHeaderText(existing == null ? "Enter new member details" : "Edit member details");

        buildContent();
        addButtons();
        populateIfEditing();
        setResultConverter(this::convertResult);
    }

    private void buildContent() {
        vpBox.getItems().addAll(VoicePart.values());
        vpBox.setPromptText("Select voice part");

        joinDatePicker.setPromptText("YYYY-MM-DD");

        notesArea.setPromptText("Optional notes...");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);

        activeCheck.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(20));

        int row = 0;
        grid.add(new Label("First Name*:"), 0, row);   grid.add(firstNameField, 1, row++);
        grid.add(new Label("Last Name*:"),  0, row);   grid.add(lastNameField,  1, row++);
        grid.add(new Label("Voice Part:"),  0, row);   grid.add(vpBox,          1, row++);
        grid.add(new Label("Email:"),       0, row);   grid.add(emailField,     1, row++);
        grid.add(new Label("Phone:"),       0, row);   grid.add(phoneField,     1, row++);
        grid.add(new Label("Join Date:"),   0, row);   grid.add(joinDatePicker, 1, row++);
        grid.add(activeCheck,               1, row++);
        grid.add(new Label("Notes:"),       0, row);   grid.add(notesArea,      1, row);

        getDialogPane().setContent(grid);

        // Auto-focus first field
        firstNameField.requestFocus();
    }

    private void addButtons() {
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        // Disable Save if required fields are empty
        Button save = (Button) getDialogPane().lookupButton(saveBtn);
        save.setDisable(true);

        firstNameField.textProperty().addListener((o, old, n) -> validateForm(save));
        lastNameField.textProperty().addListener((o, old, n)  -> validateForm(save));
    }

    private void validateForm(Button save) {
        save.setDisable(firstNameField.getText().isBlank() || lastNameField.getText().isBlank());
    }

    private void populateIfEditing() {
        if (existing == null) return;
        firstNameField.setText(existing.getFirstName());
        lastNameField.setText(existing.getLastName());
        emailField.setText(existing.getEmail() != null ? existing.getEmail() : "");
        phoneField.setText(existing.getPhone() != null ? existing.getPhone() : "");
        vpBox.setValue(existing.getVoicePart());
        if (existing.getJoinDate() != null && !existing.getJoinDate().isBlank()) {
            joinDatePicker.setValue(LocalDate.parse(existing.getJoinDate()));
        }
        activeCheck.setSelected(existing.isActive());
        notesArea.setText(existing.getNotes() != null ? existing.getNotes() : "");
    }

    private Member convertResult(ButtonType bt) {
        if (bt.getButtonData() != ButtonBar.ButtonData.OK_DONE) return null;
        Member m = existing != null ? existing : new Member();
        m.setFirstName(firstNameField.getText().trim());
        m.setLastName(lastNameField.getText().trim());
        m.setEmail(emailField.getText().trim());
        m.setPhone(phoneField.getText().trim());
        m.setVoicePart(vpBox.getValue());
        m.setJoinDate(joinDatePicker.getValue() != null
                ? joinDatePicker.getValue().toString() : null);
        m.setActive(activeCheck.isSelected());
        m.setNotes(notesArea.getText().trim());
        return m;
    }
}
