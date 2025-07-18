package com.audioplayer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.RangeSlider;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.ResourceBundle;

public class AudioPlayerController implements Initializable {
    @FXML
    private TextArea textArea;

    @FXML
    private TextArea infoTextArea;

    @FXML
    private TextArea statusTextArea;

    @FXML
    private Slider pitchSlider;

    @FXML
    private Text pitchSliderValue;

    @FXML
    private Slider volumeSlider;

    @FXML
    private Text volumeSliderValue;

    @FXML
    private Slider panSlider;

    @FXML
    private Text panSliderValue;

    @FXML
    private Slider timelineSlider;

    @FXML
    private RangeSlider rangeSlider;

    @FXML
    private Text currentTime;

    @FXML
    private Text totalTime;

    @FXML
    private Text trimTimeLow;

    @FXML
    private Text trimTimeHigh;

    @FXML
    private TextField tempo;

    @FXML
    private Line crosshair;

    @FXML
    private Line crosshairLow;

    @FXML
    private Line crosshairHigh;

    @FXML
    private LineChart<Number, Number> lineChart1;

    @FXML
    private LineChart<Number, Number> lineChart2;

    @FXML
    private Circle reverseIndicatorOn;

    @FXML
    private Circle reverseIndicatorOff;

    @FXML
    private Circle monoIndicatorOn;

    @FXML
    private Circle monoIndicatorOff;

    @FXML
    private Circle invertIndicatorOn;

    @FXML
    private Circle invertIndicatorOff;

    @FXML
    private Circle swapIndicatorOn;

    @FXML
    private Circle swapIndicatorOff;

    @FXML
    private Button leftChannel;

    @FXML
    private Button rightChannel;

    private File file;
    private String currentFileName;
    private Playback playback;

    private boolean leftChannelToggle = true;
    private boolean rightChannelToggle = true;

    public boolean canUpdateTimeline = true;

    private float duration;

    private final XYChart.Series<Number, Number> leftSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> rightSeries = new XYChart.Series<>();
    private int sampleJump;
    private int l = 0;
    private int r = 1;

    @FXML
    private void onSelectFileClick() {
        FileChooser fileChooser = new FileChooser();
        file = fileChooser.showOpenDialog(new Stage());

        if (file == null) {
            return;
        }

        WavParser parser = new WavParser(this, file);
        WavData wavData = parser.read();

        if (wavData != null) {
            if (playback != null) {
                playback.reset();
            }

            playback = new Playback(this, wavData);

            playback.setGain((float) volumeSlider.getValue());
            playback.setPan((float) panSlider.getValue());

            currentFileName = file.getName();
            duration = wavData.duration;

            displayFileAttributes(wavData);
            displayFileInfo(wavData);
            initialiseChart(wavData);
            populateChart(wavData);
            initialiseChart(wavData); // ??????????
            populateChart(wavData); // ??????????

            resetUI();
            setUI();
        }
    }

    @FXML
    private void onSaveClick() {
        if (playback == null) {
            return;
        }

        String newName = new StringBuffer(currentFileName)
                .insert(currentFileName.length() - 4, " Copy").toString();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(newName);

        File newFile = fileChooser.showSaveDialog(new Stage());

        if (newFile == null) {
            return;
        }

        WavData wavData = playback.getWavData(true);
        WavParser.write(newFile, wavData);
    }

    @FXML
    private void onPlayPauseClick() {
        if (playback != null) {
            Thread taskThread = new Thread(() -> playback.playPause());
            taskThread.setPriority(10);
            taskThread.setDaemon(true);
            taskThread.start();
        }
    }

    @FXML
    private void onInfoClick() {
        if (playback != null) {
            infoTextArea.setVisible(!infoTextArea.isVisible());
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

            double temp = reverseIndicatorOff.getOpacity();
            reverseIndicatorOff.setOpacity(reverseIndicatorOn.getOpacity());
            reverseIndicatorOn.setOpacity(temp);

            populateChart(playback.getWavData(false));
        }
    }

    @FXML
    private void onInvertClick() {
        if (playback != null) {
            if (playback.invertChannels()) {
                double temp = invertIndicatorOff.getOpacity();
                invertIndicatorOff.setOpacity(invertIndicatorOn.getOpacity());
                invertIndicatorOn.setOpacity(temp);

                populateChart(playback.getWavData(false));
            }
            else {
                updateStatusText("Cannot invert non stereo");
            }
        }
    }

    @FXML
    private void onMonoClick() {
        if (playback != null) {
            if (playback.setMono()) {
                double temp = monoIndicatorOff.getOpacity();
                monoIndicatorOff.setOpacity(monoIndicatorOn.getOpacity());
                monoIndicatorOn.setOpacity(temp);
            }
        }
    }

    @FXML
    private void onBeatSwapClick() {
        if (tempo.getText().length() > 3) {
            tempo.setText("1000");
        }

        if (playback != null) {
            if (!Objects.equals(tempo.getText(), "") && !Objects.equals(tempo.getText(), "0")) {
                playback.beatSwap(Integer.parseInt(tempo.getText()));

                double temp = swapIndicatorOff.getOpacity();
                swapIndicatorOff.setOpacity(swapIndicatorOn.getOpacity());
                swapIndicatorOn.setOpacity(temp);

                populateChart(playback.getWavData(false));
            }
        }
    }

    @FXML
    private void onLeftChannelClick() {
        if (playback != null) {
            if (leftChannelToggle) {
                playback.setPan(1f);
                lineChart2.setOpacity(1d);
                rightChannel.setOpacity(1d);
                lineChart1.setOpacity(0.5d);
                leftChannel.setOpacity(0.5d);
                rightChannelToggle = true;
            }
            else {
                playback.setPan(0f);
                lineChart1.setOpacity(1d);
                leftChannel.setOpacity(1d);
            }

            leftChannelToggle = !leftChannelToggle;
        }
    }

    @FXML
    private void onRightChannelClick() {
        if (playback != null) {
            if (rightChannelToggle) {
                playback.setPan(-1f);
                lineChart1.setOpacity(1d);
                leftChannel.setOpacity(1d);
                lineChart2.setOpacity(0.5d);
                rightChannel.setOpacity(0.5d);
                leftChannelToggle = true;
            }
            else {
                playback.setPan(0f);
                lineChart2.setOpacity(1d);
                rightChannel.setOpacity(1d);
            }

            rightChannelToggle = !rightChannelToggle;
        }
    }

    @FXML
    private void onTimelineSliderPressed() {
        canUpdateTimeline = false;
    }

    @FXML
    private void onTimelineSliderReleased() {
        if (playback != null) {
            int frame = (int) timelineSlider.getValue();
            Thread taskThread = new Thread(() -> playback.skipTo(frame));
            taskThread.setDaemon(true);
            taskThread.start();
        }
    }

    @FXML
    private void onPitchSliderReleased() {
        if (playback != null) {
            Thread taskThread = new Thread(() -> playback.skipTo(playback.getFrameOffset()));
            taskThread.setDaemon(true);
            taskThread.start();
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        updateTimeline();
        updateSliderValues();
        restrictTempoValueToNumbers();
    }

    private void updateTimeline() {
        final int refreshRate = 10;
        final int startX = -47;
        final int endX = 1200;

        Thread taskThread = new Thread(() -> {
            while (true) {
                if (playback != null) {
                    Platform.runLater(() -> {
                        if (canUpdateTimeline) {
                            timelineSlider.setValue(playback.getFrameOffset());
                        }

                        float ratioSlider = (float) timelineSlider.getValue() / playback.getNumFrames();

                        float currentSecond = duration * ratioSlider;
                        currentTime.setText(convertDurationToReadableTime(currentSecond));

                        float position = (ratioSlider * (endX - startX)) + startX;
                        crosshair.setStartX(position);
                        crosshair.setEndX(position);

                        // range slider
                        float ratioRangeSliderLow = (float) rangeSlider.getLowValue() / playback.getNumFrames();
                        float ratioRangeSliderHigh = (float) rangeSlider.getHighValue() / playback.getNumFrames();

                        float lowTime = duration * ratioRangeSliderLow;
                        float highTime = duration * ratioRangeSliderHigh;

                        trimTimeLow.setText(convertDurationToReadableTime(lowTime));
                        trimTimeHigh.setText(convertDurationToReadableTime(highTime));

                        float positionLow = (ratioRangeSliderLow * (endX - startX)) + startX;
                        float positionHigh = (ratioRangeSliderHigh * (endX - startX)) + startX;

                        crosshairLow.setStartX(positionLow);
                        crosshairLow.setEndX(positionLow);

                        crosshairHigh.setStartX(positionHigh);
                        crosshairHigh.setEndX(positionHigh);
                    });
                }

                try {
                    Thread.sleep(refreshRate);
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        taskThread.setDaemon(true);
        taskThread.start();
    }

    private void updateSliderValues() {
        pitchSlider.valueProperty().addListener((_, _, new_val) -> {
            pitchSlider.setValue(new_val.intValue());

            if (pitchSlider.getValue() > 0) {
                pitchSliderValue.setText("+" + pitchSlider.getValue() + " st");
            }
            else {
                pitchSliderValue.setText(pitchSlider.getValue() + " st");
            }
        });

        volumeSlider.valueProperty().addListener((_, _, new_val) -> {
            volumeSlider.setValue(new_val.intValue());

            if (volumeSlider.getValue() > 0) {
                volumeSliderValue.setText("+" + (int) volumeSlider.getValue() + " dB");
            }
            else {
                volumeSliderValue.setText((int) volumeSlider.getValue() + " dB");
            }

            if (playback != null) {
                playback.setGain((float) volumeSlider.getValue());
            }
        });

        panSlider.valueProperty().addListener((_, _, _) -> {
            float rounded = Math.round(panSlider.getValue() * 10) / 10f;
            panSlider.setValue(rounded);

            if (panSlider.getValue() > 0) {
                panSliderValue.setText("+" + rounded);
            }
            else {
                panSliderValue.setText(String.valueOf(rounded));
            }

            if (playback != null) {
                playback.setPan((float) panSlider.getValue());
            }
        });

        timelineSlider.valueProperty().addListener((_, _, new_val) -> {
            double percentage = 100 * new_val.doubleValue() / timelineSlider.getMax();
            String style = String.format(
                    "-track-color: linear-gradient(to right, " +
                            "-fx-accent 0%%, " +
                            "-fx-accent %1$.1f%%, " +
                            "-default-track-color %1$.1f%%, " +
                            "-default-track-color 100%%);",
                    percentage);
            timelineSlider.setStyle(style);
        });
    }

    private void restrictTempoValueToNumbers() {
        tempo.textProperty().addListener((_, _, newValue) -> {
            if (!newValue.matches("\\d*")) {
                tempo.setText(newValue.replaceAll("\\D", ""));
            }
        });
    }

    private void displayFileAttributes(WavData wavData) {
        String nameExcludingExtension = file.getName().substring(0, file.getName().length() - 4);
        textArea.setText(nameExcludingExtension);

        textArea.appendText("\n" + file.getPath());

        String duration = convertDurationToReadableTime(wavData.duration);
        textArea.appendText("\nDuration: " + duration);

        textArea.appendText("\n" + wavData.format.sampleRate + " Hz");

        String fileSize = convertByteCountToReadableSize(file.length());
        textArea.appendText("\n" + fileSize);
    }

    private void displayFileInfo(WavData wavData) {
        infoTextArea.setText("Kind: Waveform audio");

        String fileLengthBytes = new DecimalFormat("#,###").format(file.length());
        infoTextArea.appendText("\nSize: " + fileLengthBytes + " bytes");

        String duration = convertDurationToReadableTime(wavData.duration);
        infoTextArea.appendText("\nDuration: " + duration);

        infoTextArea.appendText("\nSample rate: " + wavData.format.sampleRate + " Hz");

        String audioChannels;
        if (wavData.format.numChannels == 1)
            audioChannels = "Mono";
        else if (wavData.format.numChannels == 2)
            audioChannels = "Stereo";
        else
            audioChannels = Integer.toString(wavData.format.numChannels);
        infoTextArea.appendText("\nAudio channels: " + audioChannels);

        infoTextArea.appendText("\nBits per sample: " + wavData.format.bitsPerSample);

        try {
            BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);

            infoTextArea.appendText("\n");
            infoTextArea.appendText("\nCreated: " + formatDateTime(attributes.creationTime()));
            infoTextArea.appendText("\nLast accessed: " + formatDateTime(attributes.lastAccessTime()));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String formatDateTime(FileTime fileTime) {
        final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        LocalDateTime localDateTime = fileTime
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        return localDateTime.format(DATE_FORMATTER);
    }

    private String convertByteCountToReadableSize(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }

        CharacterIterator iterator = new StringCharacterIterator("KMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            iterator.next();
        }

        return String.format("%.1f %cB", bytes / 1000.0, iterator.current());
    }

    private String convertDurationToReadableTime(float duration) {
        int roundedDuration = Math.round(duration);
        return String.format("%02d:%02d", (roundedDuration % 3600) / 60, roundedDuration % 60);
    }

    private void resetUI() {
        reverseIndicatorOn.setOpacity(0.4f);
        reverseIndicatorOff.setOpacity(1f);
        monoIndicatorOn.setOpacity(0.4f);
        monoIndicatorOff.setOpacity(1f);
        invertIndicatorOn.setOpacity(0.4f);
        invertIndicatorOff.setOpacity(1f);
        swapIndicatorOn.setOpacity(0.4f);
        swapIndicatorOff.setOpacity(1f);
        lineChart1.setOpacity(1f);
        lineChart2.setOpacity(1f);
        leftChannel.setOpacity(1f);
        rightChannel.setOpacity(1f);
        infoTextArea.setVisible(false);
        updateStatusText("");
    }

    private void setUI() {
        totalTime.setText(convertDurationToReadableTime(duration));
        timelineSlider.setMax(playback.getNumFrames());

        rangeSlider.setMax(playback.getNumFrames());
        rangeSlider.setHighValue(rangeSlider.getMax());
        rangeSlider.setLowValue(0);
    }

    public void updateStatusText(String text) {
        statusTextArea.setText(text);
    }

    public int getPitchValue() {
        return (int) pitchSlider.getValue();
    }

    public int getTrimLow() {
        return (int) rangeSlider.getLowValue();
    }

    public int getTrimHigh() {
        return (int) rangeSlider.getHighValue();
    }

    public void initialiseChart(WavData wavData) {
        float[][] samples = WavParser.getSamples(this, wavData);

        if (wavData.format.numChannels == 1)
            r = l;

        NumberAxis xAxis1 = (NumberAxis) lineChart1.getXAxis();
        NumberAxis xAxis2 = (NumberAxis) lineChart2.getXAxis();
        NumberAxis yAxis1 = (NumberAxis) lineChart1.getYAxis();
        NumberAxis yAxis2 = (NumberAxis) lineChart2.getYAxis();

        xAxis1.setUpperBound(samples[l].length);
        xAxis2.setUpperBound(samples[r].length);

        if (wavData.signed) {
            yAxis1.setUpperBound(Math.pow(2, wavData.format.bitsPerSample - 1) - 1);
            yAxis2.setUpperBound(Math.pow(2, wavData.format.bitsPerSample - 1) - 1);
            yAxis1.setLowerBound(Math.pow(-2, wavData.format.bitsPerSample - 1));
            yAxis2.setLowerBound(Math.pow(-2, wavData.format.bitsPerSample - 1));
        }
        else {
            // Unsigned data only stored as positive integers with min value 0
            yAxis1.setUpperBound(Math.pow(2, wavData.format.bitsPerSample) - 1);
            yAxis2.setUpperBound(Math.pow(2, wavData.format.bitsPerSample) - 1);
            yAxis1.setLowerBound(0);
            yAxis2.setLowerBound(0);
        }

        final int n = 10_000;
        sampleJump = samples[l].length / n;
        if (sampleJump == 0) {
            sampleJump = 1;
        }

        leftSeries.getData().clear();
        rightSeries.getData().clear();

        for (int i = 0; i < samples[l].length; i+= sampleJump) {
            leftSeries.getData().add(new XYChart.Data<>(i, 0));
            rightSeries.getData().add(new XYChart.Data<>(i, 0));
        }
    }

    private void populateChart(WavData wavData) {
        float[][] samples = WavParser.getSamples(this, wavData);

        int seriesIndex = 0; // will be roughly n (= 10,000)
        for (int i = 0; i < samples[l].length; i+= sampleJump) {
            float yL = samples[l][i];
            float yR = samples[r][i];

            leftSeries.getData().get(seriesIndex).setYValue(yL);
            rightSeries.getData().get(seriesIndex).setYValue(yR);
            seriesIndex++;
        }

        lineChart1.getData().clear();
        lineChart2.getData().clear();

        lineChart1.getData().add(leftSeries);
        lineChart2.getData().add(rightSeries);
    }
}