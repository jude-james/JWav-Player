module com.audioplayer {
    requires javafx.controls;
    requires javafx.fxml;
    requires io.fair_acc.chartfx;
    requires io.fair_acc.dataset;
    requires io.fair_acc.math;

    opens com.audioplayer to javafx.fxml;
    exports com.audioplayer;
}