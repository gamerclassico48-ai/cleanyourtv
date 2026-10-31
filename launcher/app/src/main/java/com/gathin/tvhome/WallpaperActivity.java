package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Full-screen wallpaper preview: Left/Right browse, OK applies, Back leaves. */
public final class WallpaperActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    private SharedPreferences choices;
    private List<File> images;
    private File wallpapersDir;
    private int index;
    private ImageView preview;
    private TextView label;
    private TextView hint;
    private Bitmap shown;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        File dir = Config.dir(this);
        wallpapersDir = dir == null ? null : new File(dir, "wallpapers");
        images = new ArrayList<>(WallpaperRotation.images(wallpapersDir));
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setTextSize(22);
        label.setBackgroundColor(0x99000000);
        label.setPadding(48, 24, 48, 24);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        labelParams.bottomMargin = 48;
        root.addView(label, labelParams);

        hint = new TextView(this);
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(18);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(96, 0, 96, 0);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.gravity = Gravity.CENTER;
        root.addView(hint, hintParams);
        if (images.isEmpty()) {
            hint.setText("Nenhuma imagem encontrada.\nColoque arquivos .jpg, .png ou .webp em:\n"
                    + (wallpapersDir == null ? "(pasta indisponível)" : wallpapersDir.getAbsolutePath()));
        }

        setContentView(root);
        index = indexOfChoice(choices.getString(Config.WALLPAPER_CHOICE_KEY, null));
        show();
    }

    /** Pictures first, then "no wallpaper", then "theme default". */
    private int indexOfChoice(String current) {
        int n = images.size();
        if (current == null) return n + 1;
        if (WallpaperRotation.CHOICE_NONE.equals(current)) return n;
        for (int i = 0; i < n; i++) {
            if (images.get(i).getName().equals(current)) return i;
        }
        return n + 1;
    }

    private void show() {
        int n = images.size();
        preview.setImageDrawable(null);
        if (shown != null) {
            shown.recycle();
            shown = null;
        }
        String name;
        if (index < n) {
            name = images.get(index).getName();
            shown = decode(images.get(index));
            if (shown != null) preview.setImageBitmap(shown);
            else name = name + " (não foi possível abrir)";
        } else if (index == n) {
            name = "Sem wallpaper";
        } else {
            name = "Padrão do tema";
        }
        boolean inUse = index == indexOfChoice(choices.getString(Config.WALLPAPER_CHOICE_KEY, null));
        label.setText(name + "   (" + (index + 1) + "/" + (n + 2) + ")"
                + (inUse ? "   ✓ em uso" : "") + "\n← →  trocar     OK  aplicar     Voltar  sair");
    }

    /** Decodes a smaller copy so a big picture does not exhaust a 1 GB TV. */
    private static Bitmap decode(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth > 0 && bounds.outWidth / (sample * 2) >= 1280) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private void move(int delta) {
        int total = images.size() + 2;
        index = (index + delta + total) % total;
        show();
    }

    private void apply() {
        int n = images.size();
        SharedPreferences.Editor editor = choices.edit();
        if (index < n) editor.putString(Config.WALLPAPER_CHOICE_KEY, images.get(index).getName());
        else if (index == n) editor.putString(Config.WALLPAPER_CHOICE_KEY, WallpaperRotation.CHOICE_NONE);
        else editor.remove(Config.WALLPAPER_CHOICE_KEY);
        editor.putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        setResult(RESULT_OK);
        Toast.makeText(this, "Wallpaper aplicado", Toast.LENGTH_SHORT).show();
        show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                apply();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shown != null) {
            shown.recycle();
            shown = null;
        }
    }
}
