package com.audioplayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

public class WavParser {
    private byte[] data;
    private byte[][] channelData;
    private float[][] channelSamples;

    private int numSamplesPerChannel;
    private int numBytesDataPerChannel;

    private File file;
    private WavFormat wavFormat;

    public WavParser(File file) {
        this.file = file;
    }

    public byte[] getData() {
        return data;
    }

    public float[][] getChannelSamples() {
        return channelSamples;
    }

    public WavFormat getWavFormat() {
        return wavFormat;
    }

    public void read() {
        try {
            DataInputStream inputStream = new DataInputStream(new FileInputStream(file));

            LinkedHashMap<String, Long> chunkLookup = readSubChunks(inputStream);

            if (chunkLookup == null) {
                return;
            }

            System.out.println(chunkLookup);

            long fmtChunkOffset = chunkLookup.get("fmt ");
            long dataChunkOffset = chunkLookup.get("data");

            inputStream = new DataInputStream(new FileInputStream(file));
            inputStream.skip(fmtChunkOffset + 8);
            readFmtChunk(inputStream);

            inputStream = new DataInputStream(new FileInputStream(file));
            inputStream.skip(dataChunkOffset + 4);
            readDataChunk(inputStream);

            inputStream = new DataInputStream(new FileInputStream(file));
            inputStream.skip(dataChunkOffset + 8);
            readSamples(inputStream);
        }
        catch (IOException e) {
            System.out.println("Invalid File");
        }
    }

    private LinkedHashMap<String, Long> readSubChunks(DataInputStream in) {
        try {
            byte[] bytes = new byte[4];

            // Reads the "RIFF" chunk descriptor
            if (in.read(bytes) < 0) {
                return null;
            }
            String chunkID = new String(bytes, StandardCharsets.ISO_8859_1);

            int chunkSize = Integer.reverseBytes(in.readInt());

            if (in.read(bytes) < 0) {
                return null;
            }
            String format = new String(bytes, StandardCharsets.ISO_8859_1);

            if (!chunkID.equals("RIFF") || !format.equals("WAVE")) {
                System.out.println("Invalid .wav file");
                return null;
            }

            if (file.length() - 8 != chunkSize) {
                System.out.println(STR."Invalid file size: \{file.length()}. Expected: \{chunkSize + 8}");
                return null;
            }

            LinkedHashMap<String, Long> chunkLookup = new LinkedHashMap<>();
            chunkLookup.put(chunkID, 0L);

            long chunkOffset = 12; // First sub-chunk always starts at byte 12

            // Reads every other chunk descriptor
            while (in.read(bytes) != -1) {
                chunkID = new String(bytes, StandardCharsets.ISO_8859_1);
                chunkLookup.put(chunkID, chunkOffset);

                // read chunk size & skip to next sub-chunk
                chunkSize = Integer.reverseBytes(in.readInt());
                long bytesSkipped = in.skip(chunkSize);
                chunkOffset += bytesSkipped + 8;
            }

            return chunkLookup;
        }
        catch (IOException e) {
            System.out.println("Invalid file");
            return null;
        }
    }

    private void readFmtChunk(DataInputStream in) {
        try {
            wavFormat = new WavFormat();
            wavFormat.audioFormat = Short.reverseBytes(in.readShort());
            wavFormat.numChannels = Short.reverseBytes(in.readShort());
            wavFormat.sampleRate = Integer.reverseBytes(in.readInt());
            wavFormat.byteRate = Integer.reverseBytes(in.readInt());
            wavFormat.blockAlign = Short.reverseBytes(in.readShort());
            wavFormat.bitsPerSample = Short.reverseBytes(in.readShort());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readDataChunk(DataInputStream in) {
        try {
            int numBytesData = Integer.reverseBytes(in.readInt());
            int numSamples = numBytesData / wavFormat.blockAlign;
            numSamplesPerChannel = numSamples / wavFormat.numChannels;
            numBytesDataPerChannel = numBytesData / wavFormat.numChannels;

            data = new byte[numBytesData];
            in.read(data);

            channelData = new byte[wavFormat.numChannels][numBytesDataPerChannel];
            channelSamples = new float[wavFormat.numChannels][numSamplesPerChannel];

            wavFormat.duration = numSamples / wavFormat.sampleRate;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readSamples(DataInputStream in) {
        try {
            //TODO: Move if-statements outside loop & move for loops into thread
            for (int sample = 0; sample < numSamplesPerChannel; sample++) {
                for (int channel = 0; channel < wavFormat.numChannels; channel++) {
                    if (wavFormat.bitsPerSample == 8) {
                        channelSamples[channel][sample] = (byte) (Integer.reverse(in.readByte()) >>> (Integer.SIZE - Byte.SIZE));
                    }
                    else if (wavFormat.bitsPerSample == 16) {
                        channelSamples[channel][sample] = Short.reverseBytes(in.readShort());
                    }
                    else if (wavFormat.bitsPerSample == 32) {
                        channelSamples[channel][sample] = Integer.reverseBytes(in.readInt());
                    }
                    else {
                        System.out.println(STR."Unsupported bits per sample: \{wavFormat.bitsPerSample}");
                    }
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readDataPerChannel(DataInputStream in) {
    }

    public void write() {
    }
}

