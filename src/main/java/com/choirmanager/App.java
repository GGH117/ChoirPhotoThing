package com.choirmanager;

import com.choirmanager.db.DatabaseManager;
import com.choirmanager.ui.attendance.AttendanceView;
import com.choirmanager.ui.photos.PhotoView;
import com.choirmanager.ui.roster.RosterView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Main entry point for Choir Manager.
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab rosterTab     = new Tab("👥 Roster",    new RosterView());
        Tab attendanceTab = new Tab("✅ Attendance", new AttendanceView());
        Tab photosTab     = new Tab("📷 Photos",     new PhotoView());

        tabPane.getTabs().addAll(rosterTab, attendanceTab, photosTab);

        BorderPane root = new BorderPane(tabPane);
        Scene scene = new Scene(root, 1100, 700);
        scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());

        primaryStage.setTitle("Choir Manager");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            try { DatabaseManager.getInstance().close(); } catch (Exception ignored) {}
        });
        primaryStage.show();
    }

    private BorderPane placeholder(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 16px; -fx-text-fill: #888;");
        return new BorderPane(lbl);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
