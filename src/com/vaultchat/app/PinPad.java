package com.vaultchat.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Numeric keypad with six indicator dots. */
public class PinPad extends LinearLayout {

    public interface OnPin {
        void onPin(String pin);
    }

    private final View[] dots = new View[6];
    private final StringBuilder buf = new StringBuilder();
    private final TextView status;
    private final OnPin cb;

    public PinPad(Context c, OnPin cb) {
        super(c);
        this.cb = cb;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < 6; i++) {
            View v = new View(c);
            v.setBackground(Ui.round(Ui.CARD2, 8, c));
            LinearLayout.LayoutParams p = Ui.lp(Ui.dp(c, 13), Ui.dp(c, 13));
            p.setMargins(Ui.dp(c, 7), 0, Ui.dp(c, 7), 0);
            row.addView(v, p);
            dots[i] = v;
        }
        addView(row);
        addView(Ui.spacer(c, 18));

        status = Ui.text(c, "", 14f, Ui.SUB);
        status.setGravity(Gravity.CENTER);
        addView(status);
        addView(Ui.spacer(c, 22));

        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "del", "0", ""};
        for (int r = 0; r < 4; r++) {
            LinearLayout line = Ui.row(c);
            for (int col = 0; col < 3; col++) {
                final String k = keys[r * 3 + col];
                Button b = new Button(c);
                b.setAllCaps(false);
                b.setTextSize(20f);
                b.setTextColor(Ui.TEXT);
                b.setText("del".equals(k) ? "<" : k);
                b.setBackground(Ui.round(Ui.CARD, 16, c));
                b.setEnabled(!"".equals(k));
                if ("".equals(k)) b.setVisibility(View.INVISIBLE);
                LinearLayout.LayoutParams p = Ui.lp(0, Ui.dp(c, 62), 1f);
                p.setMargins(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
                b.setLayoutParams(p);
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        onKey(k);
                    }
                });
                line.addView(b);
            }
            addView(line);
        }
        paint();
    }

    private void onKey(String k) {
        if ("del".equals(k)) {
            if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
        } else if (buf.length() < 6) {
            buf.append(k);
        }
        paint();
        if (buf.length() == 6) {
            String pin = buf.toString();
            buf.setLength(0);
            cb.onPin(pin);
        }
    }

    private void paint() {
        for (int i = 0; i < 6; i++) {
            dots[i].setBackground(Ui.round(i < buf.length() ? Ui.ACCENT : Ui.CARD2, 8, getContext()));
        }
    }

    public void reset() {
        buf.setLength(0);
        paint();
    }

    public void setStatus(String s) {
        status.setText(s);
    }
}
