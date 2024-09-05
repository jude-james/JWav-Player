package com.audioplayer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.text.*;
import java.util.Objects;
import java.util.ResourceBundle;

public class AudioPlayerController implements Initializable {
    @FXML
    private TextArea textArea;

    @FXML
    private Slider pitchSlider;

    @FXML
    private Text sliderValue;

    @FXML
    private TextField tempo;

    @FXML
    private LineChart<Number, Number> lineChart1;

    @FXML
    private LineChart<Number, Number> lineChart2;

    private File file;
    private File fileToSave;
    private Playback playback;

    @FXML
    private void onSelectFileClick() {
        FileChooser fileChooser = new FileChooser();
        file = fileChooser.showOpenDialog(new Stage());

        if (file == null) {
            return;
        }

        fileToSave = file;

        WavParser parser = new WavParser(file);
        WavData wavData = parser.read();

        if (wavData != null) {
            if (playback != null) {
                playback.reset();
            }

            playback = new Playback(wavData);
            displayFileAttributes(wavData);
            populateChart(wavData);
        }
        else {
            textArea.setText("Invalid wav file");
        }
    }

    @FXML
    private void onPlayPauseClick() {
        if (playback != null) {
            playback.transposePitch((int) pitchSlider.getValue());

            Thread taskThread = new Thread(() -> playback.playPause());
            taskThread.start();
        }
    }

    @FXML
    private void onResetClick() {
        if (playback != null) {
            playback.reset();
        }
    }

    @FXML
    private void onReverseClick() {
        if (playback != null) {
            playback.reverse();
        }
    }

    @FXML
    private void onBeatSwapClick() {
        if (tempo.getText().length() > 3) {
            tempo.setText("1000");
        }

        if (playback != null) {
            if (!Objects.equals(tempo.getText(), "")) {
                playback.beatSwap(Integer.parseInt(tempo.getText()));
            }
        }
    }

    @FXML
    private void onSaveClick() {
        if (playback == null) {
            return;
        }

        String oldName = fileToSave.getName();
        String newName = new StringBuffer(oldName)
                .insert(oldName.length() - 4, " Copy").toString();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(newName);

        File newFile = fileChooser.showSaveDialog(new Stage());

        if (newFile == null) {
            return;
        }

        WavData wavData = playback.getWavData();
        WavParser.write(newFile, wavData);
    }

    @FXML
    private void onLeftChannelClick() {
        if (playback != null) {
            playback.setChannel(0);
        }
    }

    @FXML
    private void onRightChannelClick() {
        if (playback != null) {
            playback.setChannel(1);
        }
    }

    @FXML
    private void onMonoClick() {
        if (playback != null) {
            WavData wavData = playback.getWavData();
            float[][] samples = WavParser.getSamples(wavData);
        }
        // sum samples then divide by num channels
        // convert samples back to data
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        updateSliderValue();
        restrictTempoValueToNumbers();
    }

    private void displayFileAttributes(WavData wavData) {
        String nameExcludingExtension = file.getName().substring(0, file.getName().length() - 4);
        textArea.setText(nameExcludingExtension);

        textArea.appendText(STR."\n\{file.getPath()}");

        String duration = String.format("%02d:%02d", ((int) wavData.duration % 3600) / 60, (int) wavData.duration % 60);
        textArea.appendText(STR."\nDuration: \{duration}");

        textArea.appendText(STR."\n\{wavData.format.sampleRate} Hz");

        String fileSize = convertByteCountToReadableSize(file.length());
        textArea.appendText(STR."\n\{fileSize}");
    }

    private String convertByteCountToReadableSize(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return STR."\{bytes} B";
        }

        CharacterIterator iterator = new StringCharacterIterator("KMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            iterator.next();
        }

        return String.format("%.1f %cB", bytes / 1000.0, iterator.current());
    }

    private void updateSliderValue() {
        pitchSlider.valueProperty().addListener((_, _, new_val) -> {
            pitchSlider.setValue(new_val.intValue());

            if (pitchSlider.getValue() > 0) {
                sliderValue.setText(STR."+\{pitchSlider.getValue()} st");
            }
            else {
                sliderValue.setText(STR."\{pitchSlider.getValue()} st");
            }
        });
    }

    private void restrictTempoValueToNumbers() {
        tempo.textProperty().addListener((_, _, newValue) -> {
            if (!newValue.matches("\\d*")) {
                tempo.setText(newValue.replaceAll("\\D", ""));
            }
        });
    }

    public void populateChart(WavData wavData) {
        float[][] samples = WavParser.getSamples(wavData);

        NumberAxis yAxis1 = (NumberAxis) lineChart1.getYAxis();
        NumberAxis xAxis1 = (NumberAxis) lineChart1.getXAxis();
        NumberAxis xAxis2 = (NumberAxis) lineChart2.getXAxis();
        NumberAxis yAxis2 = (NumberAxis) lineChart2.getYAxis();

        yAxis1.setUpperBound(Math.pow(2, wavData.format.bitsPerSample - 1) - 1);
        yAxis1.setLowerBound(Math.pow(-2, wavData.format.bitsPerSample - 1) - 1);
        yAxis2.setUpperBound(Math.pow(2, wavData.format.bitsPerSample - 1) - 1);
        yAxis2.setLowerBound(Math.pow(-2, wavData.format.bitsPerSample - 1) - 1);
        xAxis1.setUpperBound(samples[0].length);
        xAxis2.setUpperBound(samples[1].length);

        XYChart.Series<Number, Number> leftChannel = new XYChart.Series<>();
        XYChart.Series<Number, Number> rightChannel = new XYChart.Series<>();

        int n = 10_000;
        int downSampleScale = samples[0].length / n;

        for (int i = 0; i < samples[0].length; i+= downSampleScale) {
            int x = i;
            float yL = samples[0][i];
            float yR = samples[1][i];

            Platform.runLater(() -> {
                leftChannel.getData().add(new XYChart.Data<>(x, yL));
                rightChannel.getData().add(new XYChart.Data<>(x, yR));
            });
        }

        lineChart1.getData().clear();
        lineChart2.getData().clear();

        lineChart1.getData().add(leftChannel);
        lineChart2.getData().add(rightChannel);
    }
}