package com.audioplayer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class Playback {
    private WavData wavData;

    private byte[] currentData;
    private byte[] forwardData;
    private byte[] reverseData;

    private float currentSampleRate;

    private boolean bigEndian = false;

    private boolean paused = true;
    private boolean reversed = false;

    private SourceDataLine line;

    public Playback(WavData wavData) {
        this.wavData = wavData;

        currentData = wavData.data;
        forwardData = wavData.data;
        reverseData = new byte[wavData.data.length];
        for (int i = 0; i < wavData.data.length; i++) {
            reverseData[i] = wavData.data[wavData.data.length - 1 - i];
        }

        currentSampleRate = wavData.format.sampleRate;
    }

    private void createLine() {
        AudioFormat audioFormat = new AudioFormat(currentSampleRate,
                wavData.format.bitsPerSample, wavData.format.numChannels, wavData.signed, bigEndian);

        try {
            line = AudioSystem.getSourceDataLine(audioFormat);
            line.open(audioFormat);
            line.start();
        }
        catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    public void playPause() {
        if (paused) {
            paused = false;
            play();
        }
        else {
            paused = true;
            pause();
        }
    }

    private void play() {
        createLine();

        /*
        int frameSize = wavData.format.blockAlign;
        byte[] buffer = new byte[frameSize];

        for (int i = 0; i < currentData.length; i += frameSize) {
            for (int j = 0; j < frameSize; j++) {
                buffer[j] = currentData[i + j];
            }
            line.write(buffer, 0, frameSize);
        }
         */

        // Simpler way of playing data, but occasionally breaks after a while
        line.write(currentData, 0, currentData.length);

        line.drain();
        reset();
    }

    private void pause() {
        int framePosition = line.getFramePosition();
        int frameSize = wavData.format.blockAlign;
        int dataPosition = framePosition * frameSize;

        line.close();

        // Resizes data array to the current frame position, so it can be resumed
        byte[] temp = currentData;
        currentData = new byte[currentData.length - dataPosition];
        System.arraycopy(temp, dataPosition, currentData, 0, currentData.length);
    }

    public void reset() {
        paused = true;

        if (line != null) {
            line.close();
        }

        if (reversed) {
            currentData = reverseData;
        }
        else {
            currentData = forwardData;
        }
    }

    public void reverse() {
        bigEndian = !bigEndian;

        if (reversed) {
            currentData = forwardData;
            reversed = false;
        }
        else {
            currentData = reverseData;
            reversed = true;
        }
    }
}
