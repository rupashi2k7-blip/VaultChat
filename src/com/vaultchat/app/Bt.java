package com.vaultchat.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bluetooth RFCOMM transport. No Wi-Fi, no hotspot, no internet, no server:
 * the two phones talk to each other directly. Pair the phones once in the
 * system Bluetooth settings, then use the app.
 */
public final class Bt {

    public static final UUID UUID_SPP = UUID.fromString("8f4e2b1a-9c3d-4a7e-b6f1-2d5c8e9a0b34");
    private static final String NAME = "VaultChat";

    private Bt() {}

    public static boolean available() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            return a != null && a.isEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Bonded (paired) devices as {name, address}. */
    public static List<String[]> bonded() {
        List<String[]> out = new ArrayList<String[]>();
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) return out;
            Set<BluetoothDevice> set = a.getBondedDevices();
            if (set == null) return out;
            for (BluetoothDevice d : set) {
                String name = null;
                try {
                    name = d.getName();
                } catch (Throwable ignored) {
                }
                out.add(new String[]{name == null ? d.getAddress() : name, d.getAddress()});
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static Net host() throws Exception {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null || !a.isEnabled()) throw new Exception("Bluetooth is off");
        BluetoothServerSocket ss = a.listenUsingRfcommWithServiceRecord(NAME, UUID_SPP);
        try {
            BluetoothSocket s = ss.accept();
            final BluetoothSocket sock = s;
            return Net.overStreams(s.getInputStream(), s.getOutputStream(), false, "Bluetooth",
                    new Net.Closer() {
                        public void close() {
                            try {
                                sock.close();
                            } catch (Exception ignored) {
                            }
                        }
                    });
        } finally {
            try {
                ss.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static Net join(String address) throws Exception {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null || !a.isEnabled()) throw new Exception("Bluetooth is off");
        BluetoothDevice d = a.getRemoteDevice(address);
        BluetoothSocket s = d.createRfcommSocketToServiceRecord(UUID_SPP);
        s.connect();
        final BluetoothSocket sock = s;
        return Net.overStreams(s.getInputStream(), s.getOutputStream(), true, "Bluetooth",
                new Net.Closer() {
                    public void close() {
                        try {
                            sock.close();
                        } catch (Exception ignored) {
                        }
                    }
                });
    }
}
