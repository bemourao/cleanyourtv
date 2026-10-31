package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * The owner's font and text colour, picked in Launcher settings (kept with the other on-TV choices) and applied to
 * Home's text once the views exist. A change recreates Home, so views are only ever styled when they are new.
 * "default" leaves the theme's font (Outfit) and colours alone.
 */
final class UiStyle {
    static final String FONT_KEY = "ui_font";
    static final String COLOR_KEY = "ui_color";

    static final String[] FONT_IDS = {"default", "inter", "nunito", "poppins", "lexend", "montserrat", "robotoslab"};
    static final String[] FONT_NAMES = {"Outfit", "Inter", "Nunito", "Poppins", "Lexend", "Montserrat", "Roboto Slab"};
    private static final int[] FONT_RES = {0, R.font.ui_inter, R.font.ui_nunito, R.font.ui_poppins,
            R.font.ui_lexend, R.font.ui_montserrat, R.font.ui_robotoslab};

    static final String[] COLOR_IDS = {"default", "amber", "cyan", "green", "pink", "violet"};
    static final int[] COLOR_LABELS = {R.string.color_white, R.string.color_amber, R.string.color_cyan,
            R.string.color_green, R.string.color_pink, R.string.color_violet};
    /** RGB of each colour above; -1 keeps the original. */
    private static final int[] COLOR_RGB = {-1, 0xFFC107, 0x4DD0E1, 0x81C784, 0xF48FB1, 0xB39DDB};

    private final Typeface typeface;
    private final int rgb;

    private UiStyle(Typeface typeface, int rgb) {
        this.typeface = typeface;
        this.rgb = rgb;
    }

    static int indexOf(String[] ids, String id) {
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(id)) return i;
        return 0;
    }

    static UiStyle load(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        int font = indexOf(FONT_IDS, prefs.getString(FONT_KEY, "default"));
        int color = indexOf(COLOR_IDS, prefs.getString(COLOR_KEY, "default"));
        Typeface tf = null;
        if (font > 0) {
            try {
                tf = ctx.getResources().getFont(FONT_RES[font]);
            } catch (RuntimeException e) {
                tf = null;   // a font that fails to load leaves the original
            }
        }
        return new UiStyle(tf, COLOR_RGB[color]);
    }

    /** Styles the view and everything below it. withColour false keeps the colours (the settings page stays readable). */
    void apply(View root, boolean withColour) {
        if (root == null || (typeface == null && (!withColour || rgb < 0))) return;
        walk(root, withColour ? rgb : -1);
    }

    private void walk(View v, int colour) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (typeface != null) {
                Typeface current = t.getTypeface();
                t.setTypeface(typeface, current == null ? Typeface.NORMAL : current.getStyle());
            }
            if (colour >= 0) {
                int c = t.getCurrentTextColor();
                if (Color.red(c) > 0xC0 && Color.green(c) > 0xC0 && Color.blue(c) > 0xC0) {
                    t.setTextColor((c & 0xFF000000) | colour);   // only near-white text; the alpha (dimming) is kept
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), colour);
        }
    }
}
