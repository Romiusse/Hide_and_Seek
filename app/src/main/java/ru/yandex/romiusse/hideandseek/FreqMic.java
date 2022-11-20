package ru.yandex.romiusse.hideandseek;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;


public class FreqMic extends AsyncTask<Void, Void, Void> {

    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord recorder = null;

    private static final float NORMALIZATION_FACTOR_2_BYTES = Short.MAX_VALUE + 1.0f;

    private int[] pD = new int[] {-1, -1, -1, -1, -1};



    @SuppressLint("MissingPermission")
    protected Void doInBackground(Void... params) {
        // use only 1 channel, to make this easier
        int bufferSize = 2;

        int bufferElements2Rec = 2048; // want to play 2048 (2K) since 2 bytes we use only 1024
        int bytesPerElement = 2; // 2 bytes in 16bit format

        short[] sData = new short[bufferElements2Rec];
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, bufferElements2Rec * bytesPerElement);


        recorder.startRecording();

        final byte[] buf = new byte[2048]; // <--- increase this for higher frequency resolution
        final int numberOfSamples = buf.length / bufferSize;
        final JavaFFT fft = new JavaFFT(numberOfSamples);
        int pos = 0;
        int pos1 = 0;
        final int WINDOW_SIZE = 25;
        double[] window = new double[WINDOW_SIZE];
        int[] playerDistance = new int[] {-1, -1, -1, -1, -1};
        int lastplayer = -1;
        int avrPlayerDistanceNow = 0;
        while (MainActivity.ISRUNNING) {
            // in real impl, don't just ignore how many bytes you read
            recorder.read(buf, 0, 2048);
            // the stream represents each sample as two bytes -> decode
            final float[] samples = decode(buf, bufferSize);
            final float[][] transformed = fft.transform(samples);
            final float[] realPart = transformed[0];
            final float[] imaginaryPart = transformed[1];
            final double[] magnitudes = toMagnitudes(realPart, imaginaryPart);

            if(pos < WINDOW_SIZE){
                window[pos] = magnitudes[256];
                pos++;
            }
            else{
                for(int i = 0;i< WINDOW_SIZE - 1;i++){
                    window[i] = window[i + 1];
                }
                window[WINDOW_SIZE - 1] = magnitudes[256];
                double avrSum = 0;
                for(int i = 0;i<WINDOW_SIZE;i++){
                    avrSum += window[i];
                }
                int timeStamp = Integer.parseInt(new SimpleDateFormat("ss").format(Calendar.getInstance().getTime()));
                timeStamp %= 10; timeStamp /= 2;
                int soundVol = (int) (avrSum / WINDOW_SIZE);

                if(timeStamp != lastplayer){

                    if(lastplayer != -1){
                        int dist = avrPlayerDistanceNow / pos1;
                        if(dist > 25)
                            playerDistance[lastplayer] = (-dist + 300);
                        else playerDistance[lastplayer] = -1;
                    }

                    pos1 = 1;
                    avrPlayerDistanceNow = 0;
                    avrPlayerDistanceNow += soundVol;
                    lastplayer = timeStamp;
                }
                else{
                    pos1++;
                    avrPlayerDistanceNow += soundVol;
                }

                pD = playerDistance.clone();
                //if(!Arrays.equals(playerDistance, new int[] {-1, -1, -1, -1, -1}))

                //System.out.println(Arrays.toString(playerDistance));
                //System.out.println(soundVol);
            }



            // do something with magnitudes...
        }
        return null;
    }

    private static float[] decode(final byte[] buf, int format) {
        final float[] fbuf = new float[buf.length / format];
        for (int pos = 0; pos < buf.length; pos += format) {
            final int sample = byteToIntLittleEndian(buf, pos, format);
            // normalize to [0,1] (not strictly necessary, but makes things easier)
            fbuf[pos / format] = sample / NORMALIZATION_FACTOR_2_BYTES;
        }
        return fbuf;
    }

    private static double[] toMagnitudes(final float[] realPart, final float[] imaginaryPart) {
        final double[] powers = new double[realPart.length / 2];
        for (int i = 0; i < powers.length; i++) {
            powers[i] = Math.sqrt(realPart[i] * realPart[i] + imaginaryPart[i] * imaginaryPart[i]);
        }
        return powers;
    }

    private static int byteToIntLittleEndian(final byte[] buf, final int offset, final int bytesPerSample) {
        int sample = 0;
        for (int byteIndex = 0; byteIndex < bytesPerSample; byteIndex++) {
            final int aByte = buf[offset + byteIndex] & 0xff;
            sample += aByte << 8 * (byteIndex);
        }
        return sample;
    }

    private static int byteToIntBigEndian(final byte[] buf, final int offset, final int bytesPerSample) {
        int sample = 0;
        for (int byteIndex = 0; byteIndex < bytesPerSample; byteIndex++) {
            final int aByte = buf[offset + byteIndex] & 0xff;
            sample += aByte << (8 * (bytesPerSample - byteIndex - 1));
        }
        return sample;
    }

    public int[] getpD() {
        return pD;
    }
}