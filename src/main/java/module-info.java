module com.choirmanager {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires java.net.http;
    requires com.google.api.client.auth;
    requires com.google.api.client.extensions.java6.auth;
    requires com.google.api.client.extensions.jetty.auth;
    requires google.api.client;
    requires com.google.api.client;
    requires com.google.api.services.drive;
    requires com.google.api.client.json.gson;
    requires ai.djl.api;

    opens com.choirmanager to javafx.fxml;
    opens com.choirmanager.model to javafx.base;
    opens com.choirmanager.ui.roster to javafx.fxml;
    opens com.choirmanager.ui.photos to javafx.fxml;

    exports com.choirmanager;
}
