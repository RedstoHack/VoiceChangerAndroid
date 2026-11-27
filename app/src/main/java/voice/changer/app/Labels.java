package voice.changer.app;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.os.Build;

import voice.changer.app.dsp.FxType;
import voice.changer.app.dsp.Presets;

/** Подписи из ресурсов для пресетов, эффектов, фонов и аудиоустройств. */
final class Labels {
    private Labels() {}

    /** Название голоса: готовый (на языке приложения), свой ("user:Имя") или «Свой». */
    static String presetName(Context c, String id) {
        if (id == null) return c.getString(R.string.custom);
        if (id.startsWith("user:")) return id.substring(5);
        Presets.Preset p = Presets.find(id);
        return p == null ? c.getString(R.string.custom) : p.name(Lang.russian(c));
    }

    static int fx(FxType t) {
        switch (t) {
            case ROBOT: return R.string.fx_robot;
            case ALIEN: return R.string.fx_alien;
            case WHISPER: return R.string.fx_whisper;
            case ECHO: return R.string.fx_echo;
            case REVERB: return R.string.fx_reverb;
            case CHORUS: return R.string.fx_chorus;
            case FLANGER: return R.string.fx_flanger;
            case VIBRATO: return R.string.fx_vibrato;
            case TREMOLO: return R.string.fx_tremolo;
            case DISTORTION: return R.string.fx_distortion;
            case BITCRUSH: return R.string.fx_bitcrush;
            case TELEPHONE: return R.string.fx_telephone;
            case MEGAPHONE: return R.string.fx_megaphone;
            case VOCODER: return R.string.fx_vocoder;
            case FAN: return R.string.fx_fan;
            case REVERSE: return R.string.fx_reverse;
            default: return R.string.fx_underwater;
        }
    }

    static int bg(String kind) {
        switch (kind) {
            case "rain": return R.string.bg_rain;
            case "wind": return R.string.bg_wind;
            case "waves": return R.string.bg_waves;
            case "white": return R.string.bg_white;
            case "brown": return R.string.bg_brown;
            case "file": return R.string.bg_file;
            default: return R.string.bg_none;
        }
    }

    /** Название устройства или null, если его не стоит показывать. */
    static String device(Context c, AudioDeviceInfo d) {
        int t = d.getType();
        int res;
        switch (t) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: res = R.string.dev_builtin_mic; break;
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: res = R.string.dev_speaker; break;
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: res = R.string.dev_earpiece; break;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: res = R.string.dev_wired; break;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: res = R.string.dev_headphones; break;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET: res = R.string.dev_usb; break;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: res = R.string.dev_bt_sco; break;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: res = R.string.dev_bt_a2dp; break;
            default:
                if (Build.VERSION.SDK_INT >= 31 && (t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLE_SPEAKER))
                    res = R.string.dev_ble;
                else return null;
        }
        String name = c.getString(res);
        CharSequence prod = d.getProductName();
        boolean builtin = t == AudioDeviceInfo.TYPE_BUILTIN_MIC || t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || t == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
        return (!builtin && prod != null && prod.length() > 0) ? name + " · " + prod : name;
    }

    /** Название блока интерфейса (для редактора расположения). */
    static int block(String id) {
        switch (id) {
            case "voice": return R.string.blk_voice;
            case "mode": return R.string.blk_mode;
            case "mic": return R.string.blk_mic;
            case "quick": return R.string.blk_quick;
            case "devices": return R.string.blk_devices;
            case "result": return R.string.blk_result;
            case "pick": return R.string.blk_pick;
            case "action": return R.string.blk_action;
            case "input": return R.string.blk_input;
            case "tts": return R.string.blk_tts;
            case "tune": return R.string.blk_tune;
            case "save": return R.string.blk_save;
            case "effects": return R.string.blk_effects;
            default: return R.string.blk_bg;
        }
    }

    static int tab(int id) {
        switch (id) {
            case 0: return R.string.tab_live;
            case 1: return R.string.tab_file;
            case 2: return R.string.tab_text;
            default: return R.string.tab_voice;
        }
    }
}
