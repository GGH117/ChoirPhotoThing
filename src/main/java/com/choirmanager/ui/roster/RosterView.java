package com.choirmanager.ui.roster;

import com.choirmanager.db.MemberDAO;
import com.choirmanager.model.Member;
import com.choirmanager.model.Member.VoicePart;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Roster management panel — lists all members, supports add/edit/delete
 * and filtering by voice part.
 */
public class RosterView extends BorderPane {

    private final MemberDAO memberDAO;
    private final ObservableList<Member> memberList = FXCollections.observableArrayList();
    private final TableView<Member> table = new TableView<>();
    private final ComboBox<String> filterCombo = new ComboBox<>();

    public RosterView() throws SQLException {
        this.memberDAO = new MemberDAO();
        buildUI();
        loadMembers("All");
    }

    // -------------------------------------------------------------------------
    // UI Construction
    // -------------------------------------------------------------------------

    private void buildUI() {
        // Top toolbar
        Label title = new Label("Choir Roster");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        filterCombo.getItems().addAll("All", "Active Only",
                "Soprano", "Alto", "Tenor", "Bass", "Other");
        filterCombo.setValue("All");
        filterCombo.setOnAction(e -> loadMembers(filterCombo.getValue()));

        Button addBtn    = new Button("+ Add Member");
        Button editBtn   = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        Button exportBtn = new Button("Export CSV");

        addBtn.setOnAction(e    -> openMemberDialog(null));
        editBtn.setOnAction(e   -> editSelected());
        deleteBtn.setOnAction(e -> deleteSelected());
        exportBtn.setOnAction(e -> exportCSV());

        HBox toolbar = new HBox(10, title,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                new Label("Filter:"), filterCombo,
                new Region(), addBtn, editBtn, deleteBtn, exportBtn);
        HBox.setHgrow(toolbar.getChildren().get(5), Priority.ALWAYS); // spacer
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10));

        // Table
        buildTable();
        table.setItems(memberList);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Status bar
        Label statusLabel = new Label();
        memberList.addListener((javafx.collections.ListChangeListener<Member>) c ->
                statusLabel.setText(memberList.size() + " member(s)"));

        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(4, 10, 4, 10));
        statusBar.setStyle("-fx-background-color: #f0f0f0;");

        setTop(toolbar);
        setCenter(table);
        setBottom(statusBar);
    }

    @SuppressWarnings("unchecked")
    private void buildTable() {
        TableColumn<Member, String> lastCol = new TableColumn<>("Last Name");
        lastCol.setCellValueFactory(new PropertyValueFactory<>("lastName"));

        TableColumn<Member, String> firstCol = new TableColumn<>("First Name");
        firstCol.setCellValueFactory(new PropertyValueFactory<>("firstName"));

        TableColumn<Member, String> vpCol = new TableColumn<>("Voice Part");
        vpCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getVoicePart() != null
                        ? c.getValue().getVoicePart().name() : ""));

        TableColumn<Member, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));

        TableColumn<Member, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(new PropertyValueFactory<>("phone"));

        TableColumn<Member, String> joinCol = new TableColumn<>("Join Date");
        joinCol.setCellValueFactory(new PropertyValueFactory<>("joinDate"));

        TableColumn<Member, String> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isActive() ? "Yes" : "No"));

        table.getColumns().addAll(lastCol, firstCol, vpCol, emailCol, phoneCol, joinCol, activeCol);
        table.setOnMouseClicked(e -> { if (e.getClickCount() == 2) editSelected(); });
    }

    // -------------------------------------------------------------------------
    // Data Operations
    // -------------------------------------------------------------------------

    private void loadMembers(String filter) {
        try {
            List<Member> members = switch (filter) {
                case "Active Only" -> memberDAO.findActive();
                case "Soprano"     -> memberDAO.findByVoicePart(VoicePart.Soprano);
                case "Alto"        -> memberDAO.findByVoicePart(VoicePart.Alto);
                case "Tenor"       -> memberDAO.findByVoicePart(VoicePart.Tenor);
                case "Bass"        -> memberDAO.findByVoicePart(VoicePart.Bass);
                case "Other"       -> memberDAO.findByVoicePart(VoicePart.Other);
                default            -> memberDAO.findAll();
            };
            memberList.setAll(members);
        } catch (SQLException ex) {
            showError("Failed to load members: " + ex.getMessage());
        }
    }

    private void editSelected() {
        Member selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Please select a member to edit.");
            return;
        }
        openMemberDialog(selected);
    }

    private void deleteSelected() {
        Member selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Please select a member to delete.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + selected.getFullName() + "? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                memberDAO.delete(selected.getId());
                loadMembers(filterCombo.getValue());
            } catch (SQLException ex) {
                showError("Failed to delete member: " + ex.getMessage());
            }
        }
    }

    private void exportCSV() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Save Roster as CSV");
        fc.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fc.setInitialFileName("roster.csv");
        java.io.File file = fc.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        try (java.io.PrintWriter pw = new java.io.PrintWriter(file)) {
            pw.println("Last Name,First Name,Voice Part,Email,Phone,Join Date,Active,Notes");
            for (Member m : memberList) {
                pw.printf("%s,%s,%s,%s,%s,%s,%s,\"%s\"%n",
                        escape(m.getLastName()), escape(m.getFirstName()),
                        m.getVoicePart() != null ? m.getVoicePart().name() : "",
                        escape(m.getEmail()), escape(m.getPhone()),
                        escape(m.getJoinDate()),
                        m.isActive() ? "Yes" : "No",
                        escape(m.getNotes()));
            }
            showInfo("Exported " + memberList.size() + " members to " + file.getName());
        } catch (Exception ex) {
            showError("Export failed: " + ex.getMessage());
        }
    }

    private void openMemberDialog(Member existing) {
        MemberDialog dialog = new MemberDialog(existing);
        Optional<Member> result = dialog.showAndWait();
        result.ifPresent(m -> {
            try {
                if (m.getId() == 0) memberDAO.insert(m);
                else                memberDAO.update(m);
                loadMembers(filterCombo.getValue());
            } catch (SQLException ex) {
                showError("Failed to save member: " + ex.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\"\"");
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}
