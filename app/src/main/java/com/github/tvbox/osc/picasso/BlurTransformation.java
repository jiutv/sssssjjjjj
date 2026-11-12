package com.github.tvbox.osc.picasso;

import android.graphics.Bitmap;

import com.squareup.picasso.Transformation;

/**
 * 轻量模糊（降采样 + 两次盒式模糊），用于详情页封面高斯模糊背景。
 * 在 Picasso 的工作线程执行，不占用主线程。
 */
public class BlurTransformation implements Transformation {

    private static final int SAMPLE = 8;
    private static final int RADIUS = 6;

    private final String key;

    public BlurTransformation() {
        this("detail_bg_blur");
    }

    public BlurTransformation(String key) {
        this.key = key;
    }

    @Override
    public Bitmap transform(Bitmap source) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        int w = Math.max(1, sw / SAMPLE);
        int h = Math.max(1, sh / SAMPLE);
        Bitmap small = Bitmap.createScaledBitmap(source, w, h, true);
        boolean smallIsSource = small == source;

        int[] pixels = new int[w * h];
        int[] temp = new int[w * h];
        small.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int i = 0; i < 2; i++) {
            blurHorizontal(pixels, temp, w, h, RADIUS);
            blurVertical(temp, pixels, w, h, RADIUS);
        }
        small.setPixels(pixels, 0, w, 0, 0, w, h);

        Bitmap out = Bitmap.createScaledBitmap(small, sw, sh, true);
        if (!smallIsSource && out != small) {
            small.recycle();
        }
        if (out != source) {
            source.recycle();
        }
        return out;
    }

    private static void blurHorizontal(int[] src, int[] dst, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int ar = 0, ag = 0, ab = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[row + clamp(i, 0, w - 1)];
                ar += (c >> 16) & 0xFF;
                ag += (c >> 8) & 0xFF;
                ab += c & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                dst[row + x] = 0xFF000000 | ((ar / div) << 16) | ((ag / div) << 8) | (ab / div);
                int out = src[row + clamp(x - r, 0, w - 1)];
                int in = src[row + clamp(x + r + 1, 0, w - 1)];
                ar += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                ag += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                ab += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    private static void blurVertical(int[] src, int[] dst, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int ar = 0, ag = 0, ab = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[clamp(i, 0, h - 1) * w + x];
                ar += (c >> 16) & 0xFF;
                ag += (c >> 8) & 0xFF;
                ab += c & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = 0xFF000000 | ((ar / div) << 16) | ((ag / div) << 8) | (ab / div);
                int out = src[clamp(y - r, 0, h - 1) * w + x];
                int in = src[clamp(y + r + 1, 0, h - 1) * w + x];
                ar += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                ag += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                ab += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    @Override
    public String key() {
        return key;
    }
}
