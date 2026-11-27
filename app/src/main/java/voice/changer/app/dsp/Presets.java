package voice.changer.app.dsp;

import java.util.ArrayList;
import java.util.List;

/** Готовые голоса (названия на русском и английском). */
public final class Presets {
    private Presets() {}

    public static final class Preset {
        public final String id, ru, en;
        public final float pitch, speed;
        public final boolean reverse;
        public final float[] fx = new float[FxType.COUNT];
        Preset(String id, String ru, String en, float pitch, float speed, boolean reverse) {
            this.id = id; this.ru = ru; this.en = en; this.pitch = pitch; this.speed = speed; this.reverse = reverse;
        }
        public String name(boolean russian) { return russian ? ru : en; }
    }

    public static final List<Preset> ALL = new ArrayList<>();

    private static void add(String id, String ru, String en, float pitch, float speed, Object... fx) {
        put(id, ru, en, pitch, speed, false, fx);
    }

    private static void put(String id, String ru, String en, float pitch, float speed, boolean rev, Object[] fx) {
        Preset p = new Preset(id, ru, en, pitch, speed, rev);
        for (int i = 0; i < fx.length; i += 2)
            p.fx[((FxType) fx[i]).ordinal()] = ((Number) fx[i + 1]).floatValue();
        ALL.add(p);
    }

    private static final FxType RB = FxType.ROBOT, AL = FxType.ALIEN, WH = FxType.WHISPER, EC = FxType.ECHO,
            RV = FxType.REVERB, CH = FxType.CHORUS, FL = FxType.FLANGER, VI = FxType.VIBRATO, TR = FxType.TREMOLO,
            DI = FxType.DISTORTION, BC = FxType.BITCRUSH, TE = FxType.TELEPHONE, ME = FxType.MEGAPHONE,
            UW = FxType.UNDERWATER, VO = FxType.VOCODER, FA = FxType.FAN;

    static {
        add("original", "Обычный", "Normal", 0, 1);
        add("helium", "Гелий", "Helium", 7, 1.05f);
        add("hexafluoride", "Гексафторид", "Hexafluoride", -6, 0.95f, RV, 0.15f);
        add("robot", "Робот", "Robot", -1, 1, RB, 0.85f);
        add("bigrobot", "Большой робот", "Big Robot", -5, 0.95f, RB, 0.8f, RV, 0.25f);
        add("cave", "Пещера", "Cave", 0, 1, RV, 1f, EC, 0.55f);
        add("spacemutant", "Космический мутант", "Space Mutant", -4, 1, AL, 0.6f, FL, 0.5f, VI, 0.3f);
        add("deep", "Низкий голос", "Deep Voice", -5, 0.97f);
        put("backwards", "Наоборот", "Backwards", 0, 1, true, new Object[0]);
        add("monster", "Монстр", "Monster", -7, 0.9f, DI, 0.35f, RB, 0.2f, RV, 0.2f);
        add("smallcreature", "Малыш", "Small Creature", 6, 1.1f, VI, 0.15f);
        add("optimus", "Оптимус", "Optimus Robot", -3, 1, VO, 0.7f, ME, 0.3f, RV, 0.15f);
        add("telephone", "Телефон", "Telephone", 0, 1, TE, 1f);
        add("dragon", "Дракон", "Dragon", -9, 0.88f, DI, 0.4f, RV, 0.45f, EC, 0.2f);
        add("nervous", "Нервный", "Nervous", 1.5f, 1.15f, VI, 0.5f, TR, 0.35f);
        add("extraterrestrial", "Инопланетянин", "Extraterrestrial", 4, 1, AL, 0.8f, EC, 0.2f, VI, 0.35f);
        add("cathedral", "Собор", "Cathedral", 0, 1, RV, 1f, EC, 0.35f);
        add("cyborg", "Киборг", "Cyborg", -1, 1, RB, 0.5f, VO, 0.45f, BC, 0.15f);
        add("poltergeist", "Полтергейст", "Poltergeist", -3, 1, WH, 0.5f, RV, 0.8f, EC, 0.4f, FL, 0.4f, TR, 0.2f);
        add("chipmunks", "Поющие бурундуки", "Singing Chipmunks", 9, 1.12f, CH, 0.5f);
        add("android", "Андроид", "Android", 0, 1, VO, 0.85f, RB, 0.15f);
        add("skullmask", "Маска-череп", "Skull Mask", -6, 1, DI, 0.3f, WH, 0.35f, RV, 0.35f);
        add("drunk", "Пьяный", "Drunk", -1, 0.82f, VI, 0.75f, CH, 0.3f);
        add("giant", "Великан", "Giant", -9, 0.85f, RV, 0.35f, EC, 0.15f);
        add("squirrel", "Белка", "Squirrel", 10, 1.15f, VI, 0.2f);
        add("darkcyborg", "Тёмный киборг", "Dark Cyborg", -6, 1, RB, 0.6f, VO, 0.5f, DI, 0.25f, RV, 0.25f);
        add("grandcanyon", "Гранд-Каньон", "Grand Canyon", 0, 1, EC, 1f, RV, 0.6f);
        add("oldradio", "Старое радио", "Old Radio", 0, 1, TE, 0.8f, BC, 0.2f, TR, 0.12f, DI, 0.15f);
        add("fly", "Муха", "Fly", 8, 1.2f, FA, 0.6f, VI, 0.5f, TE, 0.3f);
        add("zombie", "Зомби", "Zombie", -4, 0.78f, VI, 0.45f, DI, 0.2f, TR, 0.2f);
        add("child", "Ребёнок", "Child", 5, 1.03f, VI, 0.1f);
        add("megaphone", "Мегафон", "Megaphone", 0, 1, ME, 1f, EC, 0.1f);
        add("fan", "Вентилятор", "Fan", 0, 1, FA, 1f);
        add("sarcasticrobot", "Саркастичный робот", "Sarcastic Robot", -2, 0.88f, RB, 0.6f, VI, 0.2f);
        add("ghost", "Призрак", "Ghost", -2, 0.95f, WH, 0.45f, RV, 0.9f, EC, 0.45f, VI, 0.35f);
        add("retro", "8-бит", "8-bit", 0, 1, BC, 0.6f);
        add("alien", "Пришелец", "Alien", 3, 1.05f, AL, 0.7f, VI, 0.3f);
        add("smallalien", "Малый пришелец", "Small Alien", 7, 1.1f, AL, 0.6f, VI, 0.4f);
        add("villain", "Злодей", "Villain", -6, 0.92f, DI, 0.2f, RV, 0.3f, ME, 0.2f);
        add("sheep", "Овца", "Sheep", 3, 1.05f, VI, 0.9f, TR, 0.5f);
        add("man", "Мужчина", "Man", -3.5f, 1);
        add("woman", "Женщина", "Woman", 3.5f, 1);
        add("death", "Смерть", "Death", -9, 0.8f, WH, 0.35f, RV, 0.6f, EC, 0.3f, DI, 0.2f);
        add("eliminator", "Робот-уничтожитель", "Eliminator Robot", -2, 1, RB, 1f, ME, 0.4f, EC, 0.2f);
        add("spinning", "Всё кружится", "Everything is Spinning", 0, 1, VI, 0.9f, FL, 0.8f, TR, 0.4f, CH, 0.5f);
        add("overlord", "Повелитель", "Overlord", -8, 0.9f, DI, 0.3f, RV, 0.5f, EC, 0.25f);
        add("galactic", "Галактический солдат", "Galactic Soldier", -3, 1, VO, 0.5f, ME, 0.5f, RB, 0.2f);
        add("underwater", "Под водой", "Underwater", -1, 0.95f, UW, 1f);
        add("duck", "Утка", "Duck", 5, 1.1f, ME, 0.5f, VI, 0.6f, TR, 0.3f);
        add("vocoder", "Вокодер", "Vocoder", 0, 1, VO, 1f);
        add("evilpuppet", "Злая кукла", "Evil Puppet", 2, 1.1f, VO, 0.35f, TE, 0.5f, VI, 0.3f, EC, 0.15f);
        add("lowbattery", "Низкий заряд", "Low Battery", -4, 0.78f, TR, 0.25f, DI, 0.15f, BC, 0.15f);
        add("shrinking", "Уменьшение", "Shrinking", 8, 1.25f, EC, 0.2f, FL, 0.3f);
        add("devil", "Дьявол", "Devil", -8, 0.9f, DI, 0.45f, RV, 0.5f, FL, 0.3f, EC, 0.25f);
        add("whisper", "Шёпот", "Whisper", 0, 1, WH, 1f);
        add("hall", "Зал", "Hall", 0, 1, RV, 0.7f);
        add("oldman", "Старик", "Old Man", -2, 0.92f, VI, 0.4f, TR, 0.15f, TE, 0.25f);
        add("choir", "Хор", "Choir", 0, 1, CH, 1f, RV, 0.5f);
    }

    public static Preset find(String id) {
        for (Preset p : ALL) if (p.id.equals(id)) return p;
        return null;
    }

    /** Применяет пресет к настройкам (громкость и фон не трогает). */
    public static void apply(Preset p, VoiceSettings s) {
        s.preset = p.id; s.pitch = p.pitch; s.speed = p.speed; s.reverse = p.reverse;
        System.arraycopy(p.fx, 0, s.fx, 0, s.fx.length);
    }
}
