package io.github.bags156.hyundailock;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Colors and small components for the screen, built in code with platform views. Uses the
 * phone's Material You palette on Android 12+, and a fixed blue palette before that.
 */
final class Ui {

    final Context context;
    final boolean night;

    final int background;
    final int card;
    final int field;
    final int text;
    final int textSecondary;
    final int outline;
    final int accent;
    final int onAccent;
    final int tonal;
    final int onTonal;
    final int good;
    final int warn;
    final int bad;

    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    Ui(Context context) {
        this.context = context;
        night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        good = night ? 0xFF81C995 : 0xFF1E8E3E;
        warn = night ? 0xFFFDD663 : 0xFFB06000;
        bad = night ? 0xFFF28B82 : 0xFFC5221F;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int[] c = dynamicColors(context, night);
            background = c[0];
            card = c[1];
            field = c[2];
            text = c[3];
            textSecondary = c[4];
            outline = c[5];
            accent = c[6];
            onAccent = c[7];
            tonal = c[8];
            onTonal = c[9];
        } else if (night) {
            background = 0xFF121316;
            card = 0xFF1F2125;
            field = 0xFF2A2D33;
            text = 0xFFE3E2E6;
            textSecondary = 0xFFC4C6D0;
            outline = 0xFF8E9099;
            accent = 0xFFA9C7FF;
            onAccent = 0xFF0A2A66;
            tonal = 0xFF2E3A55;
            onTonal = 0xFFDCE4F7;
        } else {
            background = 0xFFFAFAFD;
            card = 0xFFEEF0F6;
            field = 0xFFE2E5EE;
            text = 0xFF1B1B1F;
            textSecondary = 0xFF44474F;
            outline = 0xFF74777F;
            accent = 0xFF2F5FD0;
            onAccent = 0xFFFFFFFF;
            tonal = 0xFFDCE4F7;
            onTonal = 0xFF13223F;
        }
    }

    @TargetApi(Build.VERSION_CODES.S)
    private static int[] dynamicColors(Context c, boolean night) {
        if (night) {
            return new int[] {
                c.getColor(android.R.color.system_neutral1_900),
                c.getColor(android.R.color.system_neutral2_800),
                c.getColor(android.R.color.system_neutral2_700),
                c.getColor(android.R.color.system_neutral1_50),
                c.getColor(android.R.color.system_neutral2_200),
                c.getColor(android.R.color.system_neutral2_400),
                c.getColor(android.R.color.system_accent1_200),
                c.getColor(android.R.color.system_accent1_800),
                c.getColor(android.R.color.system_accent2_700),
                c.getColor(android.R.color.system_accent2_100),
            };
        }
        return new int[] {
            c.getColor(android.R.color.system_neutral1_10),
            c.getColor(android.R.color.system_neutral2_50),
            c.getColor(android.R.color.system_neutral2_100),
            c.getColor(android.R.color.system_neutral1_900),
            c.getColor(android.R.color.system_neutral2_700),
            c.getColor(android.R.color.system_neutral2_500),
            c.getColor(android.R.color.system_accent1_600),
            c.getColor(android.R.color.system_accent1_0),
            c.getColor(android.R.color.system_accent2_100),
            c.getColor(android.R.color.system_accent2_900),
        };
    }

    int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    static int alpha(int color, float alpha) {
        return Color.argb(Math.round(alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    // ---- shapes ----

    GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    GradientDrawable outlined(int strokeColor, float radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.TRANSPARENT);
        shape.setStroke(dp(1), strokeColor);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    GradientDrawable circle(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        return shape;
    }

    /** A ripple over {@code content}, clipped to a rounded mask. */
    Drawable ripple(Drawable content, int rippleColor, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(alpha(rippleColor, 0.2f)), content,
                rounded(Color.WHITE, radiusDp));
    }

    // ---- text ----

    TextView text(float sp, int color) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    TextView medium(float sp, int color) {
        TextView view = text(sp, color);
        view.setTypeface(MEDIUM);
        return view;
    }

    // ---- containers ----

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout row() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    /** A rounded surface added to {@code parent} with a gap above it. */
    LinearLayout card(LinearLayout parent) {
        LinearLayout card = column();
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(rounded(this.card, 28));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        parent.addView(card, params);
        return card;
    }

    TextView cardTitle(String title) {
        TextView view = medium(18, text);
        view.setText(title);
        return view;
    }

    static LinearLayout.LayoutParams weighted(int marginPx) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(marginPx, 0, marginPx, 0);
        return params;
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams fullWidth(float topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    /** Places views side by side with equal widths and a small gap. */
    LinearLayout split(View... views) {
        LinearLayout row = row();
        for (int i = 0; i < views.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                params.leftMargin = dp(10);
            }
            row.addView(views[i], params);
        }
        return row;
    }

    // ---- buttons ----

    private Button baseButton(String label, View.OnClickListener onClick, float heightDp) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTypeface(MEDIUM);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setStateListAnimator(null);
        button.setMinHeight(dp(heightDp));
        button.setMinimumHeight(dp(heightDp));
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setOnClickListener(onClick);
        return button;
    }

    Button filled(String label, View.OnClickListener onClick) {
        Button button = baseButton(label, onClick, 52);
        button.setTextColor(onAccent);
        button.setBackground(ripple(rounded(accent, 26), onAccent, 26));
        return button;
    }

    Button tonal(String label, View.OnClickListener onClick) {
        Button button = baseButton(label, onClick, 52);
        button.setTextColor(onTonal);
        button.setBackground(ripple(rounded(tonal, 26), onTonal, 26));
        return button;
    }

    Button textButton(String label, View.OnClickListener onClick) {
        Button button = baseButton(label, onClick, 40);
        button.setTextColor(accent);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(ripple(rounded(Color.TRANSPARENT, 20), accent, 20));
        return button;
    }

    /** A small selectable pill: filled when selected, outlined when not. */
    Button chip(String label, boolean selected, View.OnClickListener onClick) {
        Button button = baseButton(label, onClick, 36);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setPadding(dp(4), 0, dp(4), 0);
        styleChip(button, selected);
        return button;
    }

    void styleChip(Button button, boolean selected) {
        if (selected) {
            button.setTextColor(onTonal);
            button.setBackground(ripple(rounded(tonal, 18), onTonal, 18));
        } else {
            button.setTextColor(textSecondary);
            button.setBackground(ripple(outlined(alpha(outline, 0.6f), 18), text, 18));
        }
    }

    /** A round tonal "−" or "+" button for steppers. */
    Button round(String symbol, View.OnClickListener onClick) {
        Button button = new Button(context);
        button.setText(symbol);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        button.setTextColor(onTonal);
        button.setStateListAnimator(null);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(onTonal, 0.2f)),
                circle(tonal), circle(Color.WHITE)));
        button.setOnClickListener(onClick);
        return button;
    }

    /** Disabled controls fade, since custom backgrounds don't do it themselves. */
    static void enable(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.38f);
    }

    // ---- inputs ----

    EditText field(String hint, int inputType) {
        EditText field = new EditText(context);
        field.setHint(hint);
        field.setInputType(inputType);
        field.setSingleLine(true);
        // Password input types switch to monospace; keep the regular font.
        field.setTypeface(Typeface.DEFAULT);
        field.setTextColor(text);
        field.setHintTextColor(alpha(textSecondary, 0.8f));
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setBackground(rounded(this.field, 16));
        field.setPadding(dp(16), dp(14), dp(16), dp(14));
        return field;
    }

    Switch toggle() {
        Switch toggle = new Switch(context);
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[] {onAccent, outline}));
        toggle.setTrackTintList(new ColorStateList(states, new int[] {accent, alpha(outline, 0.35f)}));
        return toggle;
    }

    ImageView icon(int resId, int color, float sizeDp) {
        ImageView view = new ImageView(context);
        view.setImageResource(resId);
        view.setImageTintList(ColorStateList.valueOf(color));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return view;
    }
}
