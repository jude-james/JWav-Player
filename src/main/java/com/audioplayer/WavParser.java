package com.audioplayer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

public class WavParser {
    private float[][] samples;
    private byte[][] channelData;

    private int numSamplesPerChannel;
    private int numBytesDataPerChannel;

    private File file;
    private WavData wavData;

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
            int numSamples = numBytesData / wavData.format.blockAlign;
            numSamplesPerChannel = numSamples / wavData.format.numChannels;
            numBytesDataPerChannel = numBytesData / wavData.format.numChannels;

            byte[] data = new byte[numBytesData];
            in.read(data);

            wavData.data = data;
            wavData.duration = numSamples / wavData.format.sampleRate;

            samples = new float[wavData.format.numChannels][numSamplesPerChannel];
            channelData = new byte[wavData.format.numChannels][numBytesDataPerChannel];
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readSamples(DataInputStream in) {
        try {
            //TODO: Move if-statements outside loop, move for loops into thread & read 24-bit samples
            for (int sample = 0; sample < numSamplesPerChannel; sample++) {
                for (int channel = 0; channel < wavData.format.numChannels; channel++) {
                    if (wavData.format.bitsPerSample == 8) {
                        samples[channel][sample] = (byte) (Integer.reverse(in.readByte()) >>> (Integer.SIZE - Byte.SIZE));
                    }
                    else if (wavData.format.bitsPerSample == 16) {
                        samples[channel][sample] = Short.reverseBytes(in.readShort());
                    }
                    else if (wavData.format.bitsPerSample == 32) {
                        samples[channel][sample] = Integer.reverseBytes(in.readInt());
                    }
                    else {
                        System.out.println(STR."Unsupported bits per sample: \{wavData.format.bitsPerSample}");
                    }
                }
            }

            wavData.samples = samples;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readDataPerChannel(DataInputStream in) {
        // for playing in mono
    }

    public void write(File file, WavData wavData) {
        try {
            DataOutputStream outputStream = new DataOutputStream(new FileOutputStream(file));

            // Writes "RIFF" chunk
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

    private byte[] intToByteArray(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(value);
        return buffer.array();
    }

    private byte[] shortToByteArray(short value) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(value);
        return buffer.array();
    }
}

