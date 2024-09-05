package com.audioplayer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

public class WavParser {
    private final File file;
    private final WavData wavData;

    public WavParser(File file) {
        this.file = file;
        wavData = new WavData();
    }

    public WavData read() {
        try {
            DataInputStream inputStream = new DataInputStream(new FileInputStream(file));

            LinkedHashMap<String, Long> chunkLookup = readSubChunks(inputStream);

            if (chunkLookup == null) {
                return null;
            }

            long fmtChunkOffset = chunkLookup.get("fmt ");
            long dataChunkOffset = chunkLookup.get("data");

            inputStream = new DataInputStream(new FileInputStream(file));
            inputStream.skip(fmtChunkOffset + 8);
            readFmtChunk(inputStream);

            inputStream = new DataInputStream(new FileInputStream(file));
            inputStream.skip(dataChunkOffset + 4);
            readDataChunk(inputStream);

            inputStream.close();

            return wavData;
        }
        catch (IOException e) {
            System.out.println("Invalid File");
            return null;
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
            WavFormat wavFormat = new WavFormat();
            wavFormat.audioFormat = Short.reverseBytes(in.readShort());
            wavFormat.numChannels = Short.reverseBytes(in.readShort());
            wavFormat.sampleRate = Integer.reverseBytes(in.readInt());
            wavFormat.byteRate = Integer.reverseBytes(in.readInt());
            wavFormat.blockAlign = Short.reverseBytes(in.readShort());
            wavFormat.bitsPerSample = Short.reverseBytes(in.readShort());

            wavData.format = wavFormat;

            // 8-bit (or lower) sample sizes are always unsigned. 9 bits or higher are always signed
            wavData.signed = wavFormat.bitsPerSample >= 9;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readDataChunk(DataInputStream in) {
        try {
            int numBytesData = Integer.reverseBytes(in.readInt());

            byte[] data = new byte[numBytesData];
            in.readFully(data);
            wavData.data = data;

            int numSamplesPerChannel = numBytesData / wavData.format.blockAlign;
            wavData.duration = numSamplesPerChannel / wavData.format.sampleRate;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static float[][] getSamples(WavData wavData) {
        int numSamplesPerChannel = wavData.data.length / wavData.format.blockAlign;
        float[][] samples = new float[wavData.format.numChannels][numSamplesPerChannel];

        ByteBuffer buffer = ByteBuffer.wrap(wavData.data).order(ByteOrder.LITTLE_ENDIAN);

        int sampleBits = wavData.format.bitsPerSample;
        if (sampleBits != 8 && sampleBits != 16 && sampleBits != 32) {
            System.out.println(STR."Unsupported bits per sample: \{sampleBits}");
            return samples;
        }

        for (int sample = 0; sample < numSamplesPerChannel; sample++) {
            for (int channel = 0; channel < wavData.format.numChannels; channel++) {
                if (buffer.position() < wavData.data.length) {
                    if (sampleBits == 8) samples[channel][sample] = (buffer.get() & 0xff) - 128; // converting to unsigned
                    else if (sampleBits == 16) samples[channel][sample] = buffer.getShort();
                    else samples[channel][sample] = buffer.getInt();
                }
            }
        }

        return samples;
    }

    public static void write(File file, WavData wavData) {
        try {
            DataOutputStream outputStream = new DataOutputStream(new FileOutputStream(file));

            // Writes RIFF chunk
            outputStream.writeBytes("RIFF");
            outputStream.write(intToByteArray(36 + wavData.data.length)); // chunkSize (= 36 + subChunk2Size)
            outputStream.writeBytes("WAVE");

            // Format chunk
            outputStream.writeBytes("fmt ");
            outputStream.write(intToByteArray(16)); // subChunk1Size (=16)
            outputStream.write(shortToByteArray((short) wavData.format.audioFormat));
            outputStream.write(shortToByteArray((short) wavData.format.numChannels));
            outputStream.write(intToByteArray((int) wavData.format.sampleRate));
            outputStream.write(intToByteArray((int) wavData.format.byteRate));
            outputStream.write(shortToByteArray((short) wavData.format.blockAlign));
            outputStream.write(shortToByteArray((short) wavData.format.bitsPerSample));

            // Data chunk
            outputStream.writeBytes("data");
            outputStream.write(intToByteArray(wavData.data.length));
            outputStream.write(wavData.data);

            outputStream.flush();
            outputStream.close();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] intToByteArray(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(value);
        return buffer.array();
    }

    private static byte[] shortToByteArray(short value) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(value);
        return buffer.array();
    }
}