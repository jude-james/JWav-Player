package com.audioplayer;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.text.*;

public class AudioPlayerController {
    @FXML
    private TextArea textArea;

    private Playback playback;

    @FXML
    protected void onSelectFileClick() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(new Stage());

        if (file == null) {
            return;
        }

        WavParser parser = new WavParser(file);
        WavData wavData = parser.read();

        if (wavData != null) {
            if (playback != null) {
                playback.reset(); // resets in case previous audio is still playing
            }

            playback = new Playback(wavData);
            DisplayFileAttributes(file, wavData);
        }
        else {
            textArea.setText("Invalid wav file");
        }
    }

    @FXML
    protected void onPlayPauseClick() {
        if (playback != null) {
            Thread taskThread = new Thread(() -> {
                playback.playPause();
            });
            taskThread.start();
        }
    }

    @FXML
    protected void onResetClick() {
        if (playback != null) {
            playback.reset();
        }
    }

    private void DisplayFileAttributes(File file, WavData wavData) {
        String nameExcludingExtension = file.getName().substring(0, file.getName().length() - 4);
        textArea.setText(STR."\{nameExcludingExtension}");

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

        CharacterIterator iterator = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            iterator.next();
        }

        return String.format("%.1f %cB", bytes / 1000.0, iterator.current());
    }
}

