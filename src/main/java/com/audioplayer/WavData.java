package com.audioplayer;

public class WavData {
    public WavFormat format;
    public boolean signed;
    public byte[] data;
    public float[][] samples;
    public float duration;
    public boolean endianness = false; // WAV uses little-endian order (false = little)
}
