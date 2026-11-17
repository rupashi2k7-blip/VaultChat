package com.vaultchat.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Voice or video call carried inside the encrypted Net session. */
public class CallActivity extends Activity implements Net.Listener {

    private Net net;
    private AudioEngine audio;
    private VideoEngine videoEngine;
    private ImageView remote;
    private ImageView selfView;
    private TextView status;
    private LinearLayout incomingPanel;
    private int mode = 1;
    private boolean isIncoming;
    private boolean inCall;
    private boolean muted;
    private boolean camOn = true;
    private long lastRemoteDecode;
    private long lastSelfDecode;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getIntExtra("mode", 1);
        isIncoming = getIntent().getBooleanExtra("incoming", false);
        net = Net.active;
        if (net == null || !net.alive()) {
            Ui.toast(this, "Not connected");
            finish();
            return;
        }
        net.setListener(this);
        buildUi();
        if (!isIncoming) {
            requestPerms();
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        remote = new ImageView(this);
        remote.setScaleType(ImageView.ScaleType.FIT_CENTER);
        remote.setBackgroundColor(0xFF05070A);
        root.addView(remote, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = Ui.text(this, mode == 2 ? "Video call · encrypted" : "Voice call · encrypted", 14f, Ui.SUB);
        status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(this, 40);
        root.addView(status, sp);

        selfView = new ImageView(this);
        selfView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        selfView.setBackgroundColor(Ui.CARD);
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 128));
        vp.gravity = Gravity.TOP | Gravity.END;
        vp.topMargin = Ui.dp(this, 64);
        vp.rightMargin = Ui.dp(this, 16);
        selfView.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
        root.addView(selfView, vp);

        LinearLayout controls = Ui.row(this);
        controls.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.BOTTOM;
        cp.bottomMargin = Ui.dp(this, 40);
        controls.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);

        Button mute = Ui.ghost(this, "Mute");
        mute.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                muted = !muted;
                if (audio != null) audio.setMuted(muted);
                ((Button) v).setText(muted ? "Unmute" : "Mute");
            }
        });
        controls.addView(mute, weightParams(this, 0));

        Button cam = Ui.ghost(this, "Camera");
        cam.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                camOn = !camOn;
                if (videoEngine != null) {
                    if (camOn) {
                        try {
                            videoEngine.start(CallActivity.this);
                        } catch (Exception ignored) {
                        }
                    } else {
                        videoEngine.stop();
                    }
                }
                selfView.setVisibility(camOn ? View.VISIBLE : View.GONE);
                ((Button) v).setText(camOn ? "Camera" : "Camera off");
            }
        });
        controls.addView(cam, weightParams(this, 1));

        Button end = Ui.button(this, "End", Ui.DANGER, Color.WHITE);
        end.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                endCall(true);
            }
        });
        controls.addView(end, weightParams(this, 2));
        root.addView(controls, cp);

        incomingPanel = Ui.column(this);
        incomingPanel.setGravity(Gravity.CENTER);
        incomingPanel.setBackgroundColor(0xEE000000);
        TextView inc = Ui.title(this, isIncoming
                ? (mode == 2 ? "Incoming video call" : "Incoming voice call")
                : "Calling...");
        inc.setGravity(Gravity.CENTER);
        incomingPanel.addView(inc);
        incomingPanel.addView(Ui.spacer(this, 8));
        TextView hint = Ui.label(this, "Verify the safety number in the chat before sharing anything private.");
        hint.setGravity(Gravity.CENTER);
        incomingPanel.addView(hint);
        incomingPanel.addView(Ui.spacer(this, 26));

        if (isIncoming) {
            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.CENTER);
            Button decline = Ui.button(this, "Decline", Ui.CARD2, Ui.TEXT);
            decline.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    endCall(true);
                }
            });
            row.addView(decline);
            Button accept = Ui.primary(this, "Accept");
            accept.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    net.send(Net.T_CALL_ACCEPT, new byte[]{(byte) mode});
                    beginCall();
                }
            });
            LinearLayout.LayoutParams ap = Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            ap.setMargins(Ui.dp(this, 12), 0, 0, 0);
            row.addView(accept, ap);
            incomingPanel.addView(row);
        } else {
            incomingPanel.addView(Ui.label(this, "Waiting for the other phone to accept"));
        }
        root.addView(incomingPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private static LinearLayout.LayoutParams weightParams(Activity a, int i) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(Ui.dp(a, 5), 0, Ui.dp(a, 5), 0);
        return p;
    }

    private void requestPerms() {
        java.util.ArrayList<String> need = new java.util.ArrayList<String>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (mode == 2 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA);
        }
        if (need.isEmpty()) {
            beginCall();
        } else {
            requestPermissions(need.toArray(new String[0]), 33);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] granted) {
        super.onRequestPermissionsResult(code, perms, granted);
        boolean ok = true;
        for (int g : granted) {
            if (g != PackageManager.PERMISSION_GRANTED) ok = false;
        }
        if (ok) beginCall();
        else {
            status.setText("Permission denied");
            Ui.toast(this, "Microphone permission is needed for calls");
        }
    }

    private void beginCall() {
        if (inCall) return;
        inCall = true;
        incomingPanel.setVisibility(View.GONE);
        try {
            audio = new AudioEngine(net);
            audio.start();
        } catch (Exception e) {
            status.setText("Audio unavailable: " + e.getClass().getSimpleName());
        }
        if (mode == 2) {
            try {
                videoEngine = new VideoEngine(net, new VideoEngine.Local() {
                    public void onLocalFrame(byte[] jpeg) {
                        long now = System.currentTimeMillis();
                        if (now - lastSelfDecode < 300) return;
                        lastSelfDecode = now;
                        final Bitmap bm = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                        if (bm == null) return;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                selfView.setImageBitmap(bm);
                            }
                        });
                    }
                });
                videoEngine.start(this);
            } catch (Exception e) {
                status.setText("Camera unavailable");
            }
        }
    }

    private void endCall(boolean notify) {
        if (notify && net != null) net.send(Net.T_CALL_END, new byte[0]);
        cleanup();
        finish();
    }

    private void cleanup() {
        try {
            if (audio != null) audio.stop();
        } catch (Exception ignored) {
        }
        audio = null;
        try {
            if (videoEngine != null) videoEngine.stop();
        } catch (Exception ignored) {
        }
        videoEngine = null;
    }

    public void onMessage(int type, byte[] payload) {
        if (type == Net.T_AUDIO && audio != null) {
            audio.push(payload);
        } else if (type == Net.T_VIDEO) {
            long now = System.currentTimeMillis();
            if (now - lastRemoteDecode < 60) return;
            lastRemoteDecode = now;
            Bitmap bm = BitmapFactory.decodeByteArray(payload, 0, payload.length);
            if (bm != null) {
                remote.setImageBitmap(bm);
                status.setText("Video call · encrypted");
            }
        } else if (type == Net.T_CALL_ACCEPT) {
            incomingPanel.setVisibility(View.GONE);
        } else if (type == Net.T_CALL_END) {
            Ui.toast(this, "Call ended");
            cleanup();
            finish();
        }
    }

    public void onSafety(String code) {
    }

    public void onClosed(String reason) {
        cleanup();
        finish();
    }

    @Override
    public void onBackPressed() {
        endCall(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }
}
