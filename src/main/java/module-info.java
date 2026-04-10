module com.choirmanager {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.xerial.sqlitejdbc;

    // Google Drive API jars are non-modular — loaded via classpath, not requires.
    // Access is granted via opens so reflection still works at runtime.
    requires java.net.http;

    opens com.choirmanager to javafx.fxml;
    opens com.choirmanager.model to javafx.base;
    opens com.choirmanager.ui.roster to javafx.fxml;

    exports com.choirmanager;
}
