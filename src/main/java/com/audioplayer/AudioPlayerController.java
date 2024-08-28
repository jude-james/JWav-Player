package com.audioplayer;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.text.DecimalFormat;

public class AudioPlayerController {
    @FXML
    private TextArea textArea;

    @FXML
    protected void onSelectFileClick() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(new Stage());

        if (file == null) {
            return;
        }

        WavParser parser = new WavParser(file);
        parser.read();

        byte[] data = parser.getData();
        WavFormat format = parser.getWavFormat();
        // send data and format to Playback object

        DisplayFileAttributes(file, format);
    }

    private void DisplayFileAttributes(File file, WavFormat format) {
        textArea.setText(STR."\{file.getName()}");
        textArea.appendText(STR."\n\{file.getPath()}");
        textArea.appendText(STR."\n\{format.sampleRate} Hz");

        double size = file.length() * Math.pow(10, -6);
        String fileSize = new DecimalFormat("#.0").format(size);
        textArea.appendText(STR."\n\{fileSize} MB");

        String duration = String.format("%02d:%02d", ((int) format.duration % 3600) / 60, (int) format.duration % 60);
        textArea.appendText(STR."\nDuration: \{duration}");
    }
}

