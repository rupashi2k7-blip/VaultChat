package com.vaultchat.app;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Captures camera frames as small JPEGs and streams them through the Net session. */
public class VideoEngine {

    public interface Local {
        void onLocalFrame(byte[] jpeg);
    }

    private final Net net;
    private final Local local;
    private HandlerThread ht;
    private Handler bg;
    private CameraDevice dev;
    private CameraCaptureSession sess;
    private ImageReader reader;
    private volatile long last = 0;
    private volatile boolean running;

    public VideoEngine(Net n, Local l) {
        net = n;
        local = l;
    }

    public void start(Context ctx) throws Exception {
        running = true;
        ht = new HandlerThread("vaultchat-cam");
        ht.start();
        bg = new Handler(ht.getLooper());

        CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        String id = null;
        for (String c : cm.getCameraIdList()) {
            CameraCharacteristics ch = cm.getCameraCharacteristics(c);
            Integer face = ch.get(CameraCharacteristics.LENS_FACING);
            if (face != null && face == CameraCharacteristics.LENS_FACING_FRONT) {
                id = c;
                break;
            }
            if (id == null) id = c;
        }
        if (id == null) throw new IllegalStateException("No camera found");

        CameraCharacteristics ch = cm.getCameraCharacteristics(id);
        StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
        Size pick = null;
        for (Size s : sizes) {
            if (s.getWidth() <= 640 && s.getHeight() <= 480) {
                if (pick == null || s.getWidth() * s.getHeight() > pick.getWidth() * pick.getHeight()) {
                    pick = s;
                }
            }
        }
        if (pick == null) pick = sizes[sizes.length - 1];

        reader = ImageReader.newInstance(pick.getWidth(), pick.getHeight(), ImageFormat.YUV_420_888, 3);
        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            public void onImageAvailable(ImageReader r) {
                handle(r);
            }
        }, bg);

        cm.openCamera(id, new CameraDevice.StateCallback() {
            public void onOpened(CameraDevice d) {
                dev = d;
                openSession();
            }

            public void onDisconnected(CameraDevice d) {
                d.close();
                dev = null;
            }

            public void onError(CameraDevice d, int e) {
                d.close();
                dev = null;
            }
        }, bg);
    }

    private void openSession() {
        try {
            final Surface surface = reader.getSurface();
            dev.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession cs) {
                    sess = cs;
                    try {
                        CaptureRequest.Builder b = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(surface);
                        b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                        cs.setRepeatingRequest(b.build(), null, bg);
                    } catch (Exception ignored) {
                    }
                }

                public void onConfigureFailed(CameraCaptureSession cs) {
                }
            }, bg);
        } catch (Exception ignored) {
        }
    }

    private void handle(ImageReader r) {
        Image img = null;
        try {
            img = r.acquireLatestImage();
            if (img == null || !running) return;
            long now = System.currentTimeMillis();
            if (now - last < 100) return;
            last = now;

            int w = img.getWidth();
            int h = img.getHeight();
            byte[] nv21 = toNv21(img, w, h);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            new YuvImage(nv21, ImageFormat.NV21, w, h, null)
                    .compressToJpeg(new Rect(0, 0, w, h), 45, bo);
            byte[] jpeg = bo.toByteArray();
            if (net != null && net.alive()) net.send(Net.T_VIDEO, jpeg);
            if (local != null) local.onLocalFrame(jpeg);
        } catch (Exception ignored) {
        } finally {
            if (img != null) {
                try {
                    img.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static byte[] toNv21(Image img, int w, int h) {
        Image.Plane[] p = img.getPlanes();
        ByteBuffer yB = p[0].getBuffer();
        ByteBuffer uB = p[1].getBuffer();
        ByteBuffer vB = p[2].getBuffer();
        int yRow = p[0].getRowStride();
        int uvRow = p[1].getRowStride();
        int uvPix = p[1].getPixelStride();

        byte[] out = new byte[w * h * 3 / 2];
        int pos = 0;
        for (int r = 0; r < h; r++) {
            yB.position(r * yRow);
            yB.get(out, pos, w);
            pos += w;
        }
        pos = w * h;
        byte[] uRow = new byte[uvRow];
        byte[] vRow = new byte[uvRow];
        for (int r = 0; r < h / 2; r++) {
            uB.position(r * uvRow);
            uB.get(uRow, 0, Math.min(uvRow, uB.remaining()));
            vB.position(r * uvRow);
            vB.get(vRow, 0, Math.min(uvRow, vB.remaining()));
            for (int c = 0; c < w / 2; c++) {
                out[pos++] = vRow[c * uvPix];
                out[pos++] = uRow[c * uvPix];
            }
        }
        return out;
    }

    public void stop() {
        running = false;
        try {
            if (sess != null) sess.close();
        } catch (Exception ignored) {
        }
        sess = null;
        try {
            if (dev != null) dev.close();
        } catch (Exception ignored) {
        }
        dev = null;
        try {
            if (reader != null) reader.close();
        } catch (Exception ignored) {
        }
        reader = null;
        try {
            if (ht != null) ht.quitSafely();
        } catch (Exception ignored) {
        }
        ht = null;
    }
}
