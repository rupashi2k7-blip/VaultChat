package com.vaultchat.app;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Collections;

import javax.crypto.KeyAgreement;

/**
 * End-to-end encrypted peer session over the local network.
 *
 * Handshake: ephemeral ECDH (secp256r1) -> HKDF-SHA256 -> two independent
 * AES-256-GCM keys, one per direction. Both phones show the same six-group
 * safety number, derived from both public keys, so a man in the middle can be
 * spotted by comparing the screen.
 *
 * Frame format: [4 byte length][12 byte IV][AES-GCM(type || payload)]
 */
public class Net {

    public static final int PORT = 8988;

    public static final int T_TEXT = 1;
    public static final int T_PHOTO = 2;
    public static final int T_AUDIO = 3;
    public static final int T_VIDEO = 4;
    public static final int T_CALL_REQ = 5;
    public static final int T_CALL_ACCEPT = 6;
    public static final int T_CALL_END = 7;

    public interface Listener {
        void onMessage(int type, byte[] payload);

        void onSafety(String code);

        void onClosed(String reason);
    }

    public static volatile Net active;

    private final Socket sock;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final boolean client;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private byte[] outKey;
    private byte[] inKey;
    private String safety = "";
    private volatile boolean closed;
    private volatile Listener listener;

    public static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static Net host() throws Exception {
        ServerSocket ss = new ServerSocket(PORT);
        ss.setSoTimeout(180000);
        try {
            Socket s = ss.accept();
            return new Net(s, false);
        } finally {
            try { ss.close(); } catch (Exception ignored) {}
        }
    }

    public static Net join(String ip) throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(ip, PORT), 12000);
        return new Net(s, true);
    }

    private Net(Socket s, boolean client) throws Exception {
        this.sock = s;
        this.client = client;
        s.setTcpNoDelay(true);
        s.setKeepAlive(true);
        in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 1 << 16));
        out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 1 << 16));
        handshake();
        startReader();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public String safetyCode() {
        return safety;
    }

    public boolean alive() {
        return !closed && sock.isConnected() && !sock.isClosed();
    }

    private static byte[] pubBytes(PublicKey k) {
        ECPublicKey e = (ECPublicKey) k;
        byte[] x = e.getW().getAffineX().toByteArray();
        byte[] y = e.getW().getAffineY().toByteArray();
        byte[] out = new byte[65];
        out[0] = 4;
        int lx = Math.min(32, x.length);
        System.arraycopy(x, x.length - lx, out, 1 + 32 - lx, lx);
        int ly = Math.min(32, y.length);
        System.arraycopy(y, y.length - ly, out, 33 + 32 - ly, ly);
        return out;
    }

    private static ECPublicKey decodePub(byte[] b) throws Exception {
        AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
        p.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = p.getParameterSpec(ECParameterSpec.class);
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(b, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(b, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), spec));
    }

    private void handshake() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();
        byte[] mine = pubBytes(kp.getPublic());
        byte[] theirs = new byte[65];

        if (client) {
            out.write(mine);
            out.flush();
            in.readFully(theirs);
        } else {
            in.readFully(theirs);
            out.write(mine);
            out.flush();
        }

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(kp.getPrivate());
        ka.doPhase(decodePub(theirs), true);
        byte[] secret = ka.generateSecret();

        // Both sides must hash the two public keys in the same order, so sort them first.
        byte[] lo = mine;
        byte[] hi = theirs;
        if (compare(mine, theirs) > 0) {
            lo = theirs;
            hi = mine;
        }

        byte[] salt = Crypto.sha256(lo, hi);
        byte[] okm = Crypto.hkdf(secret, salt, "VaultChat-v1", 64);
        byte[] k1 = Arrays.copyOfRange(okm, 0, 32);
        byte[] k2 = Arrays.copyOfRange(okm, 32, 64);
        outKey = client ? k1 : k2;
        inKey = client ? k2 : k1;

        byte[] h = Crypto.sha256(lo, hi);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i += 2) {
            int v = ((h[i] & 0xFF) << 8 | (h[i + 1] & 0xFF)) % 100000;
            if (i > 0) sb.append(' ');
            sb.append(String.format("%05d", v));
        }
        safety = sb.toString();
    }

    private static int compare(byte[] a, byte[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int x = a[i] & 0xFF, y = b[i] & 0xFF;
            if (x != y) return x - y;
        }
        return a.length - b.length;
    }

    private void startReader() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                readLoop();
            }
        }, "net-reader");
        t.setDaemon(true);
        t.start();
        final Listener l = listener;
        ui.post(new Runnable() {
            public void run() {
                if (listener != null) listener.onSafety(safety);
                else if (l != null) l.onSafety(safety);
            }
        });
    }

    private void readLoop() {
        try {
            while (!closed) {
                int len = in.readInt();
                if (len <= 0 || len > 8 * 1024 * 1024) break;
                byte[] blob = new byte[len];
                in.readFully(blob);
                final byte[] plain = Crypto.decrypt(inKey, blob);
                if (plain.length < 1) continue;
                final int type = plain[0] & 0xFF;
                final byte[] payload = Arrays.copyOfRange(plain, 1, plain.length);
                ui.post(new Runnable() {
                    public void run() {
                        Listener l = listener;
                        if (l != null) l.onMessage(type, payload);
                    }
                });
            }
        } catch (Exception ignored) {
        }
        close("Connection closed");
    }

    public void send(int type, byte[] payload) {
        if (closed) return;
        try {
            byte[] plain = new byte[1 + payload.length];
            plain[0] = (byte) type;
            System.arraycopy(payload, 0, plain, 1, payload.length);
            byte[] blob = Crypto.encrypt(outKey, plain);
            synchronized (out) {
                out.writeInt(blob.length);
                out.write(blob);
                out.flush();
            }
        } catch (Exception e) {
            close("Send failed");
        }
    }

    public void sendText(String s) {
        try {
            send(T_TEXT, s.getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    public void close(final String reason) {
        if (closed) return;
        closed = true;
        if (active == this) active = null;
        try { sock.close(); } catch (Exception ignored) {}
        ui.post(new Runnable() {
            public void run() {
                Listener l = listener;
                if (l != null) l.onClosed(reason);
            }
        });
    }
}
