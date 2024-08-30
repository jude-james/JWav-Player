package com.audioplayer;

import javax.sound.sampled.*;

public class Playback {
    private WavData wavData;

    private byte[] currentData;
    private byte[] reverseData;

    private float currentSampleRate;

    private boolean bigEndian = false;

    private boolean paused = true;
    private boolean reversed = false;

    private SourceDataLine line;

    public Playback(WavData wavData) {
        this.wavData = wavData;

        currentData = wavData.data;
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
        catch (IllegalArgumentException e) {
            System.out.println("Java does not support the specified audio format");
        }
    }

    public void transposePitch(int st) {
        currentSampleRate = (float) (wavData.format.sampleRate * Math.pow(2d, (double) st / 12d));
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

        if (line == null) {
            return;
        }

        line.write(currentData, 0, currentData.length);

        line.drain();
        reset();
    }

    private void pause() {
        if (line == null) {
            return;
        }

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
        if (line == null) {
            return;
        }

        paused = true;

        line.close();

        if (reversed) {
            currentData = reverseData;
        }
        else {
            currentData = wavData.data;
        }
    }

    public void reverse() {
        if (!paused) {
            return;
        }

        bigEndian = !bigEndian;

        if (reversed) {
            currentData = wavData.data;
            reversed = false;
        }
        else {
            currentData = reverseData;
            reversed = true;
        }
    }
}
