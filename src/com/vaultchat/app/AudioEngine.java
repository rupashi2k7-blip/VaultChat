package com.vaultchat.app;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import java.util.concurrent.ArrayBlockingQueue;

/** 16 kHz mono voice, encrypted per frame by the Net session. */
public class AudioEngine {

    private static final int RATE = 16000;
    private static final int FRAME = 640; // 20 ms of 16-bit mono

    private final Net net;
    private AudioRecord rec;
    private AudioTrack trk;
    private Thread recThread;
    private Thread playThread;
    private volatile boolean running;
    private volatile boolean muted;
    private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<byte[]>(80);

    public AudioEngine(Net n) {
        net = n;
    }

    public void start() throws Exception {
        int minIn = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minIn <= 0) minIn = 4096;
        rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minIn * 2, 8192));
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("Microphone unavailable");
        }

        int minOut = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minOut <= 0) minOut = 4096;
        trk = new AudioTrack(AudioManager.STREAM_VOICE_CALL, RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(minOut * 2, 8192), AudioTrack.MODE_STREAM);
        trk.play();

        running = true;
        rec.startRecording();

        recThread = new Thread(new Runnable() {
            public void run() {
                byte[] buf = new byte[FRAME];
                while (running) {
                    int n = rec.read(buf, 0, buf.length);
                    if (n > 0 && !muted) {
                        byte[] out = new byte[n];
                        System.arraycopy(buf, 0, out, 0, n);
                        net.send(Net.T_AUDIO, out);
                    }
                }
            }
        }, "audio-tx");
        recThread.setDaemon(true);
        recThread.start();

        playThread = new Thread(new Runnable() {
            public void run() {
                while (running) {
                    try {
                        byte[] pcm = queue.take();
                        if (pcm == AudioEngine.END) break;
                        trk.write(pcm, 0, pcm.length);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }, "audio-rx");
        playThread.setDaemon(true);
        playThread.start();
    }

    private static final byte[] END = new byte[0];

    public void push(byte[] pcm) {
        if (!running) return;
        if (!queue.offer(pcm)) {
            queue.poll();
            queue.offer(pcm);
        }
    }

    public void setMuted(boolean m) {
        muted = m;
    }

    public void stop() {
        running = false;
        try {
            if (rec != null) {
                rec.stop();
                rec.release();
            }
        } catch (Exception ignored) {
        }
        rec = null;
        try {
            queue.offer(END);
        } catch (Exception ignored) {
        }
        try {
            if (trk != null) {
                trk.stop();
                trk.release();
            }
        } catch (Exception ignored) {
        }
        trk = null;
    }
}
