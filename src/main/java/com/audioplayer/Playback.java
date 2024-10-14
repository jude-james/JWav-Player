package com.audioplayer;

import javax.sound.sampled.*;

public class Playback {
    private final WavData wavData;
    private final AudioPlayerController controller;
    private SourceDataLine line;

    private byte[] currentData;
    private float currentSampleRate;
    private int currentNumChannels;
    private boolean paused = true;
    private int offset;

    private float pan;
    private float gain;

    private boolean monoOn = false;
    private int monoSampleRateAdjustment = 1;

    public Playback(AudioPlayerController controller, WavData wavData) {
        this.controller = controller;
        this.wavData = wavData;

        currentData = wavData.data;
        currentSampleRate = wavData.format.sampleRate;
        currentNumChannels = wavData.format.numChannels;

        createLine();
    }

    private void createLine() {
        AudioFormat audioFormat = new AudioFormat(currentSampleRate,
                wavData.format.bitsPerSample, currentNumChannels, wavData.signed, wavData.endianness);

        try {
            line = AudioSystem.getSourceDataLine(audioFormat);
            line.open(audioFormat);
            line.start();

            setLineControls();
        }
        catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
        catch (IllegalArgumentException e) {
            controller.updateStatusText("Java does not support the specified audio format");
        }
    }

    private void setLineControls() {
        if (line.isControlSupported(FloatControl.Type.PAN)) {
            FloatControl panControl = (FloatControl) line.getControl(FloatControl.Type.PAN); //TODO move to start to see if performance is better
            panControl.setValue(pan);
        }

        if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            gainControl.setValue(gain); // gain range -80 to 6 dB
        }
    }

    public void playPause() {
        if (paused) {
            play();
        }
        else {
            pause();
        }
    }

    private void play() {
        createLine();

        if (line == null) {
            return;
        }

        controller.canUpdateTimeline = true;
        paused = false;

        line.write(currentData, offset, currentData.length - offset);

        if (!paused) {
            line.drain();
            // skipTo(0); // for looping
            reset();
        }
    }

    private void pause() {
        if (line == null) {
            return;
        }

        controller.canUpdateTimeline = false;
        paused = true;

        int framePosition = line.getFramePosition() / monoSampleRateAdjustment;
        line.close();

        int frameSize = wavData.format.blockAlign;
        int dataPosition = framePosition * frameSize;
        offset += dataPosition;
    }

    public void reset() {
        if (line == null) {
            return;
        }

        controller.canUpdateTimeline = true;
        paused = true;

        line.close();
        offset = 0;
    }

    public void skipTo(int frame) {
        // caller must start a new thread for this to work, not the best approach
        int bytes = frame * wavData.format.blockAlign;

        if (paused) {
            offset = bytes;
        }
        else {
            pause();
            offset = bytes;
            play();
        }
    }

    public int getFrameOffset() {
        if (line == null) {
            return 0;
        }

        return (offset / wavData.format.blockAlign) + line.getFramePosition() / monoSampleRateAdjustment;
    }

    public int getNumFrames() {
        return currentData.length / wavData.format.blockAlign;
    }

    public void reverse() {
        int frameSize = wavData.format.blockAlign;

        for (int i = 0; i < currentData.length / 2; i += frameSize) {
            for (int j = 0; j < frameSize; j++) {
                byte temp = currentData[i+j];
                currentData[i+j] = currentData[currentData.length - i - (frameSize-j)];
                currentData[currentData.length - i - (frameSize-j)] = temp;
            }
        }
    }

    public boolean invertChannels() {
        int frameSize = wavData.format.blockAlign;
        int bytesPerSample = wavData.format.bitsPerSample / 8;

        if (wavData.format.numChannels == 2) {
            for (int i = 0; i < wavData.data.length; i += frameSize) {
                for (int j = 0; j < bytesPerSample; j++) {
                    byte temp = currentData[i + j];
                    currentData[i + j] = currentData[i + j + bytesPerSample];
                    currentData[i + j + bytesPerSample] = temp;
                }
            }

            return true;
        }

        return false;
    }

    public void beatSwap(int tempo) {
        /*
            The given sample rate is num samples per second per channel
            => samples per minute per channel = sample rate * 60
            => samples per minute = sample rate * 60 * num channels
            => frames per minute = samples per minute / num channels
            num channels cancel out
            => frames per minute = samples per minute = sample rate * 60
            frames per minute = sample rate * 60
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

    public boolean setMono() {
        if (wavData.format.numChannels > 1 && paused) {
            if (!monoOn) {
                currentNumChannels = 1;
                monoSampleRateAdjustment = 2;
            }
            else {
                currentNumChannels = wavData.format.numChannels;
                monoSampleRateAdjustment = 1;
            }

            monoOn = !monoOn;

            return true;
        }

        return false;
    }

    public void setPan(float value) {
        if (line != null) {
            pan = value;
            setLineControls();
        }
    }

    public void setGain(float value) {
        if (line != null) {
            gain = value;
            setLineControls();
        }
    }

    public void transposePitch(int semiTones) {
        currentSampleRate = (float) (wavData.format.sampleRate * Math.pow(2d, (double) semiTones / 12d) * monoSampleRateAdjustment);
    }

    public WavData getWavData() {
        WavFormat currentFormat = new WavFormat();
        currentFormat.audioFormat = wavData.format.audioFormat;
        currentFormat.numChannels = currentNumChannels;
        currentFormat.sampleRate = currentSampleRate;
        currentFormat.byteRate =  wavData.format.byteRate;
        currentFormat.blockAlign =  currentNumChannels * wavData.format.bitsPerSample / 8; // can't use existing blockAlign in case num channels changed
        currentFormat.bitsPerSample =  wavData.format.bitsPerSample;

        WavData currentWavData = new WavData();
        currentWavData.format = currentFormat;
        currentWavData.signed = wavData.signed;

        //TODO take pan and gain values to save
        // use linear scale calculation shown on documentation for volume and handle clipping somehow
        currentWavData.data = currentData;

        currentWavData.duration = wavData.duration;

        return currentWavData;
    }
}