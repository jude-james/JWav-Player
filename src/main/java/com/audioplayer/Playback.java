package com.audioplayer;

import javax.sound.sampled.*;

public class Playback {
    private WavData wavData;

    private byte[] currentData;
    private byte[] reverseData;

    private float currentSampleRate;

    private boolean paused = true;
    private boolean reversed = false;

    private SourceDataLine line;

    public Playback(WavData wavData) {
        this.wavData = wavData;

        currentData = wavData.data;
        currentSampleRate = wavData.format.sampleRate;

        reverseFrameOrder();
    }

    private void createLine() {
        AudioFormat audioFormat = new AudioFormat(currentSampleRate,
                wavData.format.bitsPerSample, wavData.format.numChannels, wavData.signed, wavData.endianness);

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

    private void reverseFrameOrder() {
        int frameSize = wavData.format.blockAlign;
        reverseData = new byte[wavData.data.length];

        for (int i = 0; i < wavData.data.length; i += frameSize) {
            for (int j = 0; j < frameSize; j++) {
                reverseData[i+j] = wavData.data[wavData.data.length - i - (frameSize-j)];
            }
        }
    }

    public void transposePitch(int semiTones) {
        currentSampleRate = (float) (wavData.format.sampleRate * Math.pow(2d, (double) semiTones / 12d));
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

        if (reversed) {
            currentData = wavData.data;
            reversed = false;
        }
        else {
            currentData = reverseData;
            reversed = true;
        }
    }

    public WavData getWavData() {
        // Atm the only values that can change are the sample rate & the data
        WavFormat currentFormat = new WavFormat();
        currentFormat.audioFormat = wavData.format.audioFormat;
        currentFormat.numChannels = wavData.format.numChannels;
        currentFormat.sampleRate = currentSampleRate;
        currentFormat.byteRate =  wavData.format.byteRate;
        currentFormat.blockAlign =  wavData.format.blockAlign;
        currentFormat.bitsPerSample =  wavData.format.bitsPerSample;

        WavData currentWavData = new WavData();
        currentWavData.format = currentFormat;
        currentWavData.signed = wavData.signed;
        currentWavData.data = currentData;
        currentWavData.samples = wavData.samples;
        currentWavData.duration = wavData.duration;

        return currentWavData;
    }
}
