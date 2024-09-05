module com.audioplayer {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;

    opens com.audioplayer to javafx.fxml;
    exports com.audioplayer;
}