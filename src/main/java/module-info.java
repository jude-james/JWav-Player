module com.audioplayer {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;

    requires org.controlsfx.controls;

    opens com.audioplayer to javafx.fxml;
    exports com.audioplayer;
}