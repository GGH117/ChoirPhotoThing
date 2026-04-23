package com.choirmanager;
import com.choirmanager.db.DatabaseManager;
import com.choirmanager.ui.attendance.AttendanceView;
import com.choirmanager.ui.photos.PhotoView;
import com.choirmanager.ui.roster.RosterView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
public class App extends Application {
    @Override public void start(Stage primaryStage) throws Exception {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
            new Tab("👥 Roster",     new RosterView()),
            new Tab("✅ Attendance", new AttendanceView()),
            new Tab("📷 Photos",     new PhotoView())
        );
        BorderPane root = new BorderPane(tabPane);
        Scene scene = new Scene(root, 1100, 700);
        scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
        primaryStage.setTitle("Choir Manager");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> { try { DatabaseManager.getInstance().close(); } catch (Exception ignored) {} });
        primaryStage.show();
    }
    public static void main(String[] args) { launch(args); }
}
