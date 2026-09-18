package io.ougist;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * 震えの作りかた。設定画面の試し打ちと本番で同じものを使う。
 *
 * <p>1〜3 は端末が用意している定義済みの効果をそのまま使う（今までどおりの感触）。
 * 定義済みの効果は端末側でチューニングされていて、一番強い
 * {@code EFFECT_HEAVY_CLICK} でも頭打ちになる。そこで 4 以上は定義済みを離れ、
 * 振幅を最大にしたうえで震える時間を延ばす。
 */
final class Haptics {

    /** 0 はなし。1..MAX が弱→最強。 */
    static final int MAX = 6;

    /** VibrationEffect の振幅の上限。 */
    private static final int FULL = 255;

    /** 4 以上の「時間で押し切る」段のミリ秒。 */
    private static final int[] LONG_MS = {35, 55, 80};

    private static final AudioAttributes TOUCH_AUDIO_ATTRS =
            new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

    static Vibrator vibrator(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm =
                    (VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) return vm.getDefaultVibrator();
        }
        return (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
    }

    static void play(Vibrator v, int level) {
        if (level <= 0 || v == null || !v.hasVibrator()) return;
        if (level > MAX) level = MAX;
        try {
            VibrationEffect e = effect(level);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                playModern(v, e, level);
            } else {
                v.vibrate(e, TOUCH_AUDIO_ATTRS);
            }
        } catch (Throwable ignore) {
        }
    }

    private static VibrationEffect effect(int level) {
        if (level >= 4) {
            // 振幅の指定は hasAmplitudeControl() が false の端末では無視されるが、
            // そのときは端末の全力で震えるので、いずれにせよ弱くはならない。
            return VibrationEffect.createOneShot(LONG_MS[level - 4], FULL);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int id = level == 1 ? VibrationEffect.EFFECT_TICK
                    : level == 2 ? VibrationEffect.EFFECT_CLICK
                    : VibrationEffect.EFFECT_HEAVY_CLICK;
            return VibrationEffect.createPredefined(id);
        }
        int ms = level == 1 ? 10 : level == 2 ? 18 : 28;
        int amp = level == 1 ? 60 : level == 2 ? 140 : FULL;
        return VibrationEffect.createOneShot(ms, amp);
    }

    /**
     * Android 13 以降。1〜3 は「触れた合図」として出すので、端末の
     * 「タッチ時の触覚の強さ」の設定に従って弱められる。
     * 4 以上はそこで頭を押さえられては困るので、
     * アクセシビリティの合図として出す（ougist はアクセシビリティサービスそのもの）。
     */
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private static void playModern(Vibrator v, VibrationEffect e, int level) {
        v.vibrate(e, new VibrationAttributes.Builder()
                .setUsage(level >= 4
                        ? VibrationAttributes.USAGE_ACCESSIBILITY
                        : VibrationAttributes.USAGE_TOUCH)
                .build());
    }

    private Haptics() {
    }
}
