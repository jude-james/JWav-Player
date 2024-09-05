package com.audioplayer;

import javax.sound.sampled.*;

public class Playback {
    private WavData wavData;

    private byte[] currentData;
    private byte[] reverseData;
    private byte[][] dataPerChannel;

    private float currentSampleRate;

    private boolean paused = true;
    private boolean reversed = false;

    private SourceDataLine line;

    public Playback(WavData wavData) {
        this.wavData = wavData;

        currentData = wavData.data;
        currentSampleRate = wavData.format.sampleRate;

        reverseFrameOrder();
        splitDataPerChannel();
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

    private void splitDataPerChannel() {
        // do reverse data per channel
        dataPerChannel = new byte[wavData.format.numChannels][wavData.data.length];

        int frameSize = wavData.format.blockAlign;
        int bytesPerSample = wavData.format.bitsPerSample / 8;

        for (int n = 0; n < wavData.format.numChannels; n++) {
            for (int i = 0; i < wavData.data.length; i += frameSize) {
                for (int k = 0; k < bytesPerSample; k++) {
                    dataPerChannel[n][i+k+(n*bytesPerSample)] = wavData.data[i+k+(n*bytesPerSample)];
                }
            }
        }
    }

    public void setChannel(int channel) {
        if (!paused) {
            return;
        }

        if (channel <= wavData.format.numChannels) {
            /*
            for (int i = 0; i < currentData.length; i++) {
                currentData[i] = dataPerChannel[channel][i];
            }
             */
            currentData = dataPerChannel[channel];
        }
    }

    public void beatSwap(int tempo) {
        /*
            The given sample rate is actually num samples per second per channel
            so samples per minute per channel = sample rate * 60
            => samples per minute = sample rate * 60 * num channels
            => frames per minute = samples per minute / num channels
            num channels cancel out
            => frames per minute = samples per minute = sample rate * 60
            frames per minute = sample rate * 60 !
         */

        float framesPerMinute = wavData.format.sampleRate * 60;
        int framesPerBeat = Math.round(framesPerMinute / tempo);
        int bytesPerBeat = framesPerBeat * wavData.format.blockAlign; // frame size
        int numFrames = currentData.length / wavData.format.blockAlign;
        int numBeats = numFrames / framesPerBeat;

        byte[][] dataPerBeat = new byte[numBeats][bytesPerBeat];
        for (int i = 0; i < numBeats; i++) {
            for (int k = 0; k < bytesPerBeat; k++) {
                dataPerBeat[i][k] = currentData[k + (i * bytesPerBeat)];
            }
        }

        int offset = 2;
        int direction = -2;

        for (int i = 0; i < numBeats; i++) {
            if (i + offset >= numBeats) {
                break;
            }

            // swaps current data at beat i with the beat which is +- 2 from i
            byte[] nextBeat = dataPerBeat[i + offset];
            for (int j = 0; j < dataPerBeat[i].length; j++) {
                currentData[j + (i * bytesPerBeat)] = nextBeat[j];
            }

            offset += direction;
            if (offset == -2) direction = 2;
            else if (offset == 2) direction = -2;
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
        currentWavData.duration = wavData.duration;

        return currentWavData;
    }
}
