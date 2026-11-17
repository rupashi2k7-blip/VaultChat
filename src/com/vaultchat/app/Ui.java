package com.vaultchat.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers so the whole UI can be built in code, with no XML layouts. */
public final class Ui {

    public static final int BG = 0xFF0B0E13;
    public static final int CARD = 0xFF161B23;
    public static final int CARD2 = 0xFF1F2630;
    public static final int ACCENT = 0xFF34D399;
    public static final int TEXT = 0xFFE9EEF4;
    public static final int SUB = 0xFF8B95A6;
    public static final int DANGER = 0xFFEF5B5B;

    private Ui() {}

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable round(int color, int radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable roundStroke(int color, int stroke, int radiusDp, Context c) {
        GradientDrawable g = round(color, radiusDp, c);
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    public static TextView text(Context c, String s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, s, 24f, TEXT);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    public static TextView label(Context c, String s) {
        return text(c, s, 13f, SUB);
    }

    public static Button button(Context c, String s, int bg, int fg) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15f);
        b.setTextColor(fg);
        b.setBackground(round(bg, 14, c));
        b.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        b.setMinHeight(0);
        b.setMinWidth(0);
        return b;
    }

    public static Button primary(Context c, String s) {
        return button(c, s, ACCENT, 0xFF06251B);
    }

    public static Button ghost(Context c, String s) {
        return button(c, s, CARD2, TEXT);
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static View spacer(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, h)));
        return v;
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(SUB);
        e.setTextSize(15f);
        e.setBackground(round(CARD2, 12, c));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        return e;
    }

    public static void pad(View v, Context c, int l, int t, int r, int b) {
        v.setPadding(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }

    public static void toast(Context c, String s) {
        android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    public static int mix(int a, int b, float f) {
        return Color.rgb(
                (int) (Color.red(a) * (1 - f) + Color.red(b) * f),
                (int) (Color.green(a) * (1 - f) + Color.green(b) * f),
                (int) (Color.blue(a) * (1 - f) + Color.blue(b) * f));
    }
}
