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

import java.io.File;
import java.net.URL;
import java.text.*;
import java.util.Objects;
import java.util.ResourceBundle;

public class AudioPlayerController implements Initializable {
    @FXML
    private TextArea textArea;

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
    private Text currentTime;

    @FXML
    private Text totalTime;

    @FXML
    private TextField tempo;

    @FXML
    private Line crosshair;

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
            displayFileAttributes(wavData);
            setChartYBounds(wavData);
            populateChart(wavData);
            resetUI();

            playback.setGain((float) volumeSlider.getValue());
            playback.setPan((float) panSlider.getValue());

            currentFileName = file.getName();
            duration = wavData.duration;
            totalTime.setText(convertDurationToReadableTime(duration));
            timelineSlider.setMax(playback.getNumFrames());
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

        WavData wavData = playback.getWavData();
        WavParser.write(newFile, wavData);
    }

    @FXML
    private void onPlayPauseClick() {
        if (playback != null) {
            playback.transposePitch((int) pitchSlider.getValue());

            Thread taskThread = new Thread(() -> playback.playPause());
            taskThread.setPriority(10);
            taskThread.setDaemon(true); // unsafe?
            taskThread.start();
        }
    }

    @FXML
    private void onInfoClick() {
        System.out.println("Show file info");
        //TODO new window popout
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

            //lineChart1.setScaleX(lineChart1.getScaleX() * -1);
            //lineChart2.setScaleX(lineChart2.getScaleX() * -1);
            populateChart(playback.getWavData()); // or flip charts
        }
    }

    @FXML
    private void onInvertClick() {
        if (playback != null) {
            if (playback.invertChannels()) {
                double temp = invertIndicatorOff.getOpacity();
                invertIndicatorOff.setOpacity(invertIndicatorOn.getOpacity());
                invertIndicatorOn.setOpacity(temp);

                populateChart(playback.getWavData()); // or swap charts
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
            if (!Objects.equals(tempo.getText(), "")) {
                playback.beatSwap(Integer.parseInt(tempo.getText()));

                double temp = swapIndicatorOff.getOpacity();
                swapIndicatorOff.setOpacity(swapIndicatorOn.getOpacity());
                swapIndicatorOn.setOpacity(temp);

                populateChart(playback.getWavData());
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
            taskThread.setDaemon(true); // unsafe?
            taskThread.start();
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        updateTimeline();
        updateSliderValue();
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

    private void updateSliderValue() {
        pitchSlider.valueProperty().addListener((_, _, new_val) -> {
            pitchSlider.setValue(new_val.intValue());

            if (pitchSlider.getValue() > 0) {
                pitchSliderValue.setText(STR."+\{pitchSlider.getValue()} st");
            }
            else {
                pitchSliderValue.setText(STR."\{pitchSlider.getValue()} st");
            }
        });

        volumeSlider.valueProperty().addListener((_, _, new_val) -> {
            volumeSlider.setValue(new_val.intValue());

            if (volumeSlider.getValue() > 0) {
                volumeSliderValue.setText(STR."+\{(int) volumeSlider.getValue()} dB");
            }
            else {
                volumeSliderValue.setText(STR."\{(int) volumeSlider.getValue()} dB");
            }

            if (playback != null) {
                playback.setGain((float) volumeSlider.getValue());
            }
        });

        panSlider.valueProperty().addListener((_, _, _) -> {
            float rounded = Math.round(panSlider.getValue() * 10) / 10f;
            panSlider.setValue(rounded);

            if (panSlider.getValue() > 0) {
                panSliderValue.setText(STR."+\{rounded}");
            }
            else {
                panSliderValue.setText(String.valueOf(rounded));
            }

            if (playback != null) {
                playback.setPan((float) panSlider.getValue());
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

    private void displayFileAttributes(WavData wavData) {
        String nameExcludingExtension = file.getName().substring(0, file.getName().length() - 4);
        textArea.setText(nameExcludingExtension);

        textArea.appendText(STR."\n\{file.getPath()}");

        String duration = convertDurationToReadableTime(wavData.duration);
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
    }

    public void updateStatusText(String text) {
        statusTextArea.setText(text);
    }

    private void setChartYBounds(WavData wavData) {
        NumberAxis yAxis1 = (NumberAxis) lineChart1.getYAxis();
        NumberAxis yAxis2 = (NumberAxis) lineChart2.getYAxis();
        yAxis1.setUpperBound(Math.pow(2, wavData.format.bitsPerSample - 1) - 1);
        yAxis2.setUpperBound(Math.pow(2, wavData.format.bitsPerSample - 1) - 1);
        yAxis1.setLowerBound(Math.pow(-2, wavData.format.bitsPerSample - 1));
        yAxis2.setLowerBound(Math.pow(-2, wavData.format.bitsPerSample - 1));
    }

    public void populateChart(WavData wavData) {
        float[][] samples = WavParser.getSamples(this, wavData);

        // TODO optimise

        int l = 0;
        int r = l;
        if (wavData.format.numChannels > 1)
            r = 1;

        NumberAxis xAxis1 = (NumberAxis) lineChart1.getXAxis();
        NumberAxis xAxis2 = (NumberAxis) lineChart2.getXAxis();
        xAxis1.setUpperBound(samples[l].length);
        xAxis2.setUpperBound(samples[r].length);

        XYChart.Series<Number, Number> leftChannel = new XYChart.Series<>();
        XYChart.Series<Number, Number> rightChannel = new XYChart.Series<>();

        int n = 10_000;
        int downSampleScale = samples[l].length / n;

        for (int i = 0; i < samples[l].length; i+= downSampleScale) {
            int x = i;
            float yL = samples[l][i];
            float yR = samples[r][i];

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