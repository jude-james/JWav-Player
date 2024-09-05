package com.audioplayer;

public class WavData {
    public WavFormat format;
    public boolean signed;
    public boolean endianness = false; // WAV uses little-endian order (false = little)
    public byte[] data;
    public float duration;
}