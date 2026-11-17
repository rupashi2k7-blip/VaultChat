package com.vaultchat.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Peer to peer encrypted chat over the local network. */
public class ChatActivity extends Activity implements Net.Listener {

    private static final int REQ_IMG = 21;

    private LinearLayout msgs;
    private ScrollView scroll;
    private EditText input;
    private Button send;
    private Button photo;
    private Button voice;
    private Button video;
    private TextView status;
    private TextView safety;
    private LinearLayout connectPanel;
    private Net net;
    private boolean connecting;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Vault.get(this).unlocked()) {
            finish();
            return;
        }

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout head = Ui.row(this);
        head.setBackgroundColor(Ui.CARD);
        Ui.pad(head, this, 12, 34, 12, 12);
        Button back = Ui.ghost(this, "<");
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        head.addView(back);
        LinearLayout titles = Ui.column(this);
        Ui.pad(titles, this, 12, 0, 0, 0);
        TextView t = Ui.text(this, "Secure chat", 17f, Ui.TEXT);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        status = Ui.text(this, "Not connected", 12f, Ui.SUB);
        titles.addView(t);
        titles.addView(status);
        head.addView(titles, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        voice = Ui.ghost(this, "Voice");
        voice.setEnabled(false);
        voice.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startCall(1, false);
            }
        });
        video = Ui.ghost(this, "Video");
        video.setEnabled(false);
        video.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startCall(2, false);
            }
        });
        head.addView(voice);
        head.addView(video);
        root.addView(head);

        safety = Ui.text(this, "", 12f, Ui.ACCENT);
        safety.setGravity(Gravity.CENTER);
        safety.setVisibility(View.GONE);
        Ui.pad(safety, this, 12, 8, 12, 8);
        root.addView(safety);

        connectPanel = Ui.column(this);
        Ui.pad(connectPanel, this, 22, 24, 22, 0);
        root.addView(connectPanel, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        buildConnectPanel();

        scroll = new ScrollView(this);
        msgs = Ui.column(this);
        Ui.pad(msgs, this, 14, 12, 14, 12);
        scroll.addView(msgs);
        scroll.setVisibility(View.GONE);
        root.addView(scroll, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = Ui.row(this);
        bar.setBackgroundColor(Ui.CARD);
        Ui.pad(bar, this, 10, 8, 10, 8);
        photo = Ui.ghost(this, "Photo");
        photo.setEnabled(false);
        photo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pickPhoto();
            }
        });
        bar.addView(photo);
        input = Ui.input(this, "Message");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams ip = Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ip.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        bar.addView(input, ip);
        send = Ui.primary(this, "Send");
        send.setEnabled(false);
        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doSend();
            }
        });
        bar.addView(send);
        root.addView(bar);

        setContentView(root);

        Net a = Net.active;
        if (a != null && a.alive()) {
            net = a;
            net.setListener(this);
            onConnected();
        }
    }

    private void buildConnectPanel() {
        connectPanel.removeAllViews();
        TextView h = Ui.title(this, "Connect two phones");
        connectPanel.addView(h);
        connectPanel.addView(Ui.spacer(this, 8));
        connectPanel.addView(Ui.label(this,
                "Both phones must be on the same Wi-Fi or one phone's hotspot. "
                        + "No internet and no server is used."));
        connectPanel.addView(Ui.spacer(this, 20));

        Button host = Ui.primary(this, "Host on this phone");
        host.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doHost();
            }
        });
        connectPanel.addView(host, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        connectPanel.addView(Ui.spacer(this, 18));

        connectPanel.addView(Ui.label(this, "Or join a host by IP address"));
        connectPanel.addView(Ui.spacer(this, 8));
        final EditText ip = Ui.input(this, "192.168.1.23");
        ip.setInputType(InputType.TYPE_CLASS_TEXT);
        connectPanel.addView(ip);
        connectPanel.addView(Ui.spacer(this, 10));
        Button join = Ui.ghost(this, "Join");
        join.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doJoin(ip.getText().toString().trim());
            }
        });
        connectPanel.addView(join, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void doHost() {
        if (connecting) return;
        connecting = true;
        String ip = Net.localIp();
        status.setText("Waiting for the other phone...");
        Ui.toast(this, "Your IP: " + (ip == null ? "check Wi-Fi" : ip));
        new Thread(new Runnable() {
            public void run() {
                try {
                    final Net n = Net.host();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            connecting = false;
                            net = n;
                            Net.active = n;
                            n.setListener(ChatActivity.this);
                            onConnected();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            connecting = false;
                            status.setText("Could not host: " + e.getClass().getSimpleName());
                        }
                    });
                }
            }
        }).start();
    }

    private void doJoin(final String ip) {
        if (connecting) return;
        if (ip.isEmpty()) {
            Ui.toast(this, "Enter the host IP first");
            return;
        }
        connecting = true;
        status.setText("Connecting to " + ip + "...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    final Net n = Net.join(ip);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            connecting = false;
                            net = n;
                            Net.active = n;
                            n.setListener(ChatActivity.this);
                            onConnected();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            connecting = false;
                            status.setText("Could not connect: " + e.getClass().getSimpleName());
                        }
                    });
                }
            }
        }).start();
    }

    private void onConnected() {
        status.setText("Connected · encrypted");
        connectPanel.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
        send.setEnabled(true);
        photo.setEnabled(true);
        voice.setEnabled(true);
        video.setEnabled(true);
        safety.setVisibility(View.VISIBLE);
        safety.setText("Safety number: " + (net.safetyCode().isEmpty() ? "..." : net.safetyCode()));
        addBubble("Connected. Compare the safety number on both phones to rule out an interceptor.", true);
    }

    private void doSend() {
        String s = input.getText().toString().trim();
        if (s.isEmpty() || net == null) return;
        net.sendText(s);
        addBubble(s, true);
        input.setText("");
    }

    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_IMG);
        } catch (Exception e) {
            Ui.toast(this, "No file picker");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMG || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri u = data.getData();
            InputStream in = getContentResolver().openInputStream(u);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (o.outWidth > 0 && o.outWidth / (sample * 2) >= 1200) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            in = getContentResolver().openInputStream(u);
            Bitmap bm = BitmapFactory.decodeStream(in, null, o2);
            in.close();
            if (bm == null) {
                Ui.toast(this, "Cannot read image");
                return;
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 78, bo);
            byte[] bytes = bo.toByteArray();
            if (net != null) net.send(Net.T_PHOTO, bytes);
            addImage(bm, true);
        } catch (Exception e) {
            Ui.toast(this, "Send failed");
        }
    }

    private void startCall(int mode, boolean incoming) {
        if (net == null) return;
        if (!incoming) net.send(Net.T_CALL_REQ, new byte[]{(byte) mode});
        Intent i = new Intent(this, CallActivity.class);
        i.putExtra("mode", mode);
        i.putExtra("incoming", incoming);
        startActivity(i);
    }

    private void addBubble(String text, boolean mine) {
        TextView tv = Ui.text(this, text, 15f, mine ? 0xFF06251B : Ui.TEXT);
        tv.setBackground(Ui.round(mine ? Ui.ACCENT : Ui.CARD2, 16, this));
        Ui.pad(tv, this, 14, 10, 14, 10);
        LinearLayout wrap = Ui.row(this);
        wrap.setGravity(mine ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams p = Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.weight = 0;
        wrap.addView(tv);
        LinearLayout.LayoutParams outer = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        outer.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        wrap.setLayoutParams(outer);
        msgs.addView(wrap);
        scrollDown();
    }

    private void addImage(Bitmap bm, boolean mine) {
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bm);
        iv.setAdjustViewBounds(true);
        iv.setMaxWidth(Ui.dp(this, 220));
        iv.setMaxHeight(Ui.dp(this, 220));
        iv.setBackground(Ui.round(Ui.CARD2, 16, this));
        LinearLayout wrap = Ui.row(this);
        wrap.setGravity(mine ? Gravity.END : Gravity.START);
        wrap.addView(iv);
        LinearLayout.LayoutParams outer = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        outer.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        wrap.setLayoutParams(outer);
        msgs.addView(wrap);
        scrollDown();
    }

    private void scrollDown() {
        scroll.post(new Runnable() {
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    // ---- Net.Listener ----

    public void onMessage(int type, byte[] payload) {
        if (type == Net.T_TEXT) {
            try {
                addBubble(new String(payload, "UTF-8"), false);
            } catch (Exception ignored) {
            }
        } else if (type == Net.T_PHOTO) {
            Bitmap bm = BitmapFactory.decodeByteArray(payload, 0, payload.length);
            if (bm != null) {
                addImage(bm, false);
                try {
                    Vault.get(this).importBytes(payload, "received_" + System.currentTimeMillis() + ".jpg",
                            "image/jpeg", "photo");
                    Ui.toast(this, "Photo saved to vault");
                } catch (Exception ignored) {
                }
            }
        } else if (type == Net.T_CALL_REQ) {
            int mode = payload.length > 0 ? payload[0] : 1;
            startCall(mode, true);
        }
    }

    public void onSafety(String code) {
        safety.setVisibility(View.VISIBLE);
        safety.setText("Safety number: " + code);
    }

    public void onClosed(String reason) {
        status.setText("Disconnected");
        send.setEnabled(false);
        photo.setEnabled(false);
        voice.setEnabled(false);
        video.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (net != null) net.setListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && net != null) {
            net.close("Left chat");
            net = null;
        }
    }
}
