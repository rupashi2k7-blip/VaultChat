package com.vaultchat.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
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
import java.util.List;

/** Peer to peer encrypted chat over Wi-Fi/hotspot or Bluetooth. */
public class ChatActivity extends Activity implements Net.Listener {

    private static final int REQ_IMG = 21;
    private static final int REQ_BT = 41;

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
    private LinearLayout modeRow;
    private LinearLayout panelBody;
    private Net net;
    private boolean connecting;
    private int mode = 0;
    private Runnable pendingBt;

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
        Ui.pad(connectPanel, this, 18, 20, 18, 0);
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

    // ---- connection UI ----

    private void buildConnectPanel() {
        connectPanel.removeAllViews();
        connectPanel.addView(Ui.title(this, "Connect two phones"));
        connectPanel.addView(Ui.spacer(this, 6));
        connectPanel.addView(Ui.label(this,
                "Wi-Fi: same network. Bluetooth: no network at all. "
                        + "Internet: anywhere, both phones need data and the same server."));
        connectPanel.addView(Ui.spacer(this, 16));

        modeRow = Ui.row(this);
        modeRow.addView(seg("Wi-Fi", 0), segParams(0));
        modeRow.addView(seg("Bluetooth", 1), segParams(1));
        modeRow.addView(seg("Internet", 2), segParams(2));
        connectPanel.addView(modeRow);
        connectPanel.addView(Ui.spacer(this, 16));

        panelBody = Ui.column(this);
        connectPanel.addView(panelBody);
        showMode(0);
    }

    private LinearLayout.LayoutParams segParams(int i) {
        LinearLayout.LayoutParams p = Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int m = Ui.dp(this, 4);
        p.setMargins(i == 0 ? 0 : m, 0, 0, 0);
        return p;
    }

    private Button seg(String label, final int m) {
        Button b = Ui.ghost(this, label);
        b.setTag("seg" + m);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showMode(m);
            }
        });
        return b;
    }

    private void showMode(int m) {
        mode = m;
        for (int i = 0; i < modeRow.getChildCount(); i++) {
            View v = modeRow.getChildAt(i);
            boolean on = v.getTag().toString().equals("seg" + m);
            v.setBackground(Ui.round(on ? Ui.ACCENT : Ui.CARD2, 14, this));
            ((Button) v).setTextColor(on ? 0xFF06251B : Ui.TEXT);
        }
        panelBody.removeAllViews();
        if (m == 0) wifiPanel();
        else if (m == 1) btPanel();
        else internetPanel();
    }

    private void wifiPanel() {
        Button host = Ui.primary(this, "Host on this phone");
        host.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                connectAsync("Waiting for the other phone...", new Connector() {
                    public Net open() throws Exception {
                        return Net.hostTcp();
                    }
                });
                String ip = Net.localIp();
                Ui.toast(ChatActivity.this, "Your IP: " + (ip == null ? "check Wi-Fi" : ip));
            }
        });
        panelBody.addView(host, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panelBody.addView(Ui.spacer(this, 16));
        panelBody.addView(Ui.label(this, "Or join the host by IP address"));
        panelBody.addView(Ui.spacer(this, 8));
        final EditText ip = Ui.input(this, "192.168.1.23");
        panelBody.addView(ip);
        panelBody.addView(Ui.spacer(this, 10));
        Button join = Ui.ghost(this, "Join");
        join.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String addr = ip.getText().toString().trim();
                if (addr.isEmpty()) {
                    Ui.toast(ChatActivity.this, "Enter the host IP first");
                    return;
                }
                connectAsync("Connecting to " + addr + "...", new Connector() {
                    public Net open() throws Exception {
                        return Net.joinTcp(addr);
                    }
                });
            }
        });
        panelBody.addView(join, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void btPanel() {
        if (!Bt.available()) {
            panelBody.addView(Ui.label(this, "Bluetooth is off or not available on this phone."));
            panelBody.addView(Ui.spacer(this, 10));
            panelBody.addView(Ui.label(this, "Turn Bluetooth on in the system settings, then come back."));
            return;
        }
        panelBody.addView(Ui.label(this,
                "No Wi-Fi, no hotspot, no internet. Pair the two phones once in Bluetooth settings, then connect here. Range is about 10 metres."));
        panelBody.addView(Ui.spacer(this, 14));

        Button host = Ui.primary(this, "Host: wait for the other phone");
        host.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ensureBt(new Runnable() {
                    public void run() {
                        connectAsync("Waiting for Bluetooth connection...", new Connector() {
                            public Net open() throws Exception {
                                return Bt.host();
                            }
                        });
                    }
                });
            }
        });
        panelBody.addView(host, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panelBody.addView(Ui.spacer(this, 16));
        panelBody.addView(Ui.label(this, "Or pick a paired device to connect to"));
        panelBody.addView(Ui.spacer(this, 8));

        ensureBt(new Runnable() {
            public void run() {
                List<String[]> devices = Bt.bonded();
                if (devices.isEmpty()) {
                    panelBody.addView(Ui.label(ChatActivity.this,
                            "No paired devices. Pair the other phone in Bluetooth settings first."));
                    return;
                }
                for (final String[] d : devices) {
                    Button b = Ui.ghost(ChatActivity.this, d[0]);
                    b.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            connectAsync("Connecting to " + d[0] + "...", new Connector() {
                                public Net open() throws Exception {
                                    return Bt.join(d[1]);
                                }
                            });
                        }
                    });
                    LinearLayout.LayoutParams p = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    p.setMargins(0, 0, 0, Ui.dp(ChatActivity.this, 8));
                    panelBody.addView(b, p);
                }
            }
        });
    }

    private void internetPanel() {
        final android.content.SharedPreferences sp = getSharedPreferences("vc", 0);
        String server = sp.getString("srv", "");
        String myId = sp.getString("myid", "");

        if (server.isEmpty() || myId.isEmpty()) {
            panelBody.addView(Ui.label(this,
                    "One-time setup. Both phones use the same server address, and each phone needs "
                            + "its own name or phone number. The server never sees your messages."));
            panelBody.addView(Ui.spacer(this, 12));
            panelBody.addView(Ui.label(this, "Server (host:port)"));
            panelBody.addView(Ui.spacer(this, 6));
            final EditText srv = Ui.input(this, "my-server.example.com:9000");
            srv.setText(server);
            panelBody.addView(srv);
            panelBody.addView(Ui.spacer(this, 12));
            panelBody.addView(Ui.label(this, "Your name or phone number"));
            panelBody.addView(Ui.spacer(this, 6));
            final EditText me = Ui.input(this, "9876543210");
            me.setText(myId);
            panelBody.addView(me);
            panelBody.addView(Ui.spacer(this, 12));
            Button save = Ui.primary(this, "Save");
            save.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String a = srv.getText().toString().trim();
                    String b = me.getText().toString().trim().replace(" ", "");
                    if (a.isEmpty() || b.isEmpty()) {
                        Ui.toast(ChatActivity.this, "Fill both fields");
                        return;
                    }
                    sp.edit().putString("srv", a).putString("myid", b).apply();
                    showMode(2);
                }
            });
            panelBody.addView(save, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            return;
        }

        panelBody.addView(Ui.label(this, "You are " + myId + " on " + server));
        Button change = Ui.ghost(this, "Change setup");
        change.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sp.edit().remove("srv").remove("myid").apply();
                showMode(2);
            }
        });
        LinearLayout.LayoutParams cp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, Ui.dp(this, 8), 0, 0);
        panelBody.addView(change, cp);
        panelBody.addView(Ui.spacer(this, 16));

        panelBody.addView(Ui.label(this, "Call by name or number"));
        panelBody.addView(Ui.spacer(this, 6));
        final EditText target = Ui.input(this, "9876543210");
        panelBody.addView(target);
        panelBody.addView(Ui.spacer(this, 8));

        final String serverAddr = server;
        final String meId = myId;

        Button call = Ui.primary(this, "Call");
        call.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String other = target.getText().toString().trim().replace(" ", "");
                if (other.isEmpty()) {
                    Ui.toast(ChatActivity.this, "Enter the other name or number");
                    return;
                }
                final String[] hp = splitHostPort(serverAddr);
                connectAsync("Calling " + other + "...", new Connector() {
                    public Net open() throws Exception {
                        return Net.overSwitchboard(hp[0], Integer.parseInt(hp[1]), meId, other, false);
                    }
                });
            }
        });
        panelBody.addView(call, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        panelBody.addView(Ui.spacer(this, 18));
        panelBody.addView(Ui.label(this, "Or keep the app open and wait for someone to call you"));
        panelBody.addView(Ui.spacer(this, 8));

        Button wait = Ui.ghost(this, "Wait for a call");
        wait.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String[] hp = splitHostPort(serverAddr);
                connectAsync("Waiting for a call...", new Connector() {
                    public Net open() throws Exception {
                        return Net.overSwitchboard(hp[0], Integer.parseInt(hp[1]), meId, "", true);
                    }
                });
            }
        });
        panelBody.addView(wait, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static String[] splitHostPort(String addr) {
        String host = addr;
        int port = 9000;
        int c = addr.lastIndexOf(':');
        if (c > 0) {
            host = addr.substring(0, c);
            try {
                port = Integer.parseInt(addr.substring(c + 1));
            } catch (Exception ignored) {
            }
        }
        return new String[]{host, String.valueOf(port)};
    }

    private void ensureBt(final Runnable then) {
        if (Build.VERSION.SDK_INT < 31) {
            then.run();
            return;
        }
        if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED) {
            then.run();
            return;
        }
        pendingBt = then;
        requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, REQ_BT);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] granted) {
        super.onRequestPermissionsResult(code, perms, granted);
        if (code == REQ_BT) {
            boolean ok = granted.length > 0 && granted[0] == PackageManager.PERMISSION_GRANTED;
            if (ok && pendingBt != null) {
                pendingBt.run();
            } else if (!ok) {
                Ui.toast(this, "Bluetooth permission is needed for this mode");
            }
            pendingBt = null;
        }
    }

    private interface Connector {
        Net open() throws Exception;
    }

    private void connectAsync(final String what, final Connector c) {
        if (connecting) return;
        connecting = true;
        status.setText(what);
        new Thread(new Runnable() {
            public void run() {
                try {
                    final Net n = c.open();
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
                            String msg = e.getMessage();
                            status.setText("Could not connect: "
                                    + (msg == null || msg.isEmpty() ? e.getClass().getSimpleName() : msg));
                        }
                    });
                }
            }
        }).start();
    }

    private void onConnected() {
        String who = net.peerName();
        status.setText("Connected" + (who == null || who.isEmpty() ? "" : " to " + who)
                + " · " + net.transport() + " · encrypted");
        connectPanel.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
        send.setEnabled(true);
        photo.setEnabled(true);
        voice.setEnabled(true);
        video.setEnabled(true);
        safety.setVisibility(View.VISIBLE);
        safety.setText("Safety number: " + (net.safetyCode().isEmpty() ? "..." : net.safetyCode()));
        addBubble("Connected over " + net.transport()
                + ". Compare the safety number on both phones to rule out an interceptor.", true);
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

    private void startCall(int callMode, boolean incoming) {
        if (net == null) return;
        if (!incoming) net.send(Net.T_CALL_REQ, new byte[]{(byte) callMode});
        Intent i = new Intent(this, CallActivity.class);
        i.putExtra("mode", callMode);
        i.putExtra("incoming", incoming);
        startActivity(i);
    }

    private void addBubble(String text, boolean mine) {
        TextView tv = Ui.text(this, text, 15f, mine ? 0xFF06251B : Ui.TEXT);
        tv.setBackground(Ui.round(mine ? Ui.ACCENT : Ui.CARD2, 16, this));
        Ui.pad(tv, this, 14, 10, 14, 10);
        LinearLayout wrap = Ui.row(this);
        wrap.setGravity(mine ? Gravity.END : Gravity.START);
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
            int callMode = payload.length > 0 ? payload[0] : 1;
            startCall(callMode, true);
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
