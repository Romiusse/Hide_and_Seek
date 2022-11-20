package ru.yandex.romiusse.hideandseek;

public interface Transform {
    public float[][] transform(float[] real) throws UnsupportedOperationException;
    public float[][] transform(float[] real, float[] imaginary) throws UnsupportedOperationException;
    public float[][] inverseTransform(float[] real, float[] imaginary) throws UnsupportedOperationException;
}