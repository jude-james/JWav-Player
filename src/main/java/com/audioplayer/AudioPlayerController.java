package com.audioplayer;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.text.*;
import java.util.ResourceBundle;

public class AudioPlayerController implements Initializable {
    @FXML
    private TextArea textArea;

    @FXML
    private Slider pitchSlider;

    @FXML
    private Text sliderValue;

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
            DisplayFileAttributes(wavData);
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
    private void onSaveClick() {
        if (playback == null) {
            return;
        }

        String oldName = fileToSave.getName();
        String newName = new StringBuffer(oldName)
                .insert(oldName.length() - 4, "_save").toString();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(newName);

        File newFile = fileChooser.showSaveDialog(new Stage());

        if (newFile == null) {
            return;
        }

        WavParser parser = new WavParser(newFile);
        WavData wavData = playback.getWavData();
        parser.write(newFile, wavData);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        updateSliderValue();
    }

    private void DisplayFileAttributes(WavData wavData) {
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
}

