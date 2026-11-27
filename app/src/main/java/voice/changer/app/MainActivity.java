package voice.changer.app;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity {
    private FrameLayout overlay;
    private FrameLayout content;
    private LinearLayout top;
    private Backdrop backdrop;
    private final TextView[] tabs = new TextView[4];     // по id вкладки: 0 микрофон, 1 файл, 2 текст, 3 голос
    private final List<Runnable> closers = new ArrayList<>();
    private Screen[] screens;
    private int cur = -1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store.init(this);
        Theme.load(this);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);

        FrameLayout rootStack = new FrameLayout(this);
        backdrop = new Backdrop(this);
        rootStack.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = Ui.v(this);
        top = Ui.h(this);
        top.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 4));
        if (Theme.headerFile(this).exists()) {
            Bitmap icon = Theme.decode(Theme.headerFile(this), 128);
            if (icon != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(icon);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
                ip.rightMargin = Ui.dp(this, 10);
                top.addView(iv, ip);
            }
        }
        TextView title = Ui.text(this, getString(R.string.app_name), 22, Ui.TEXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(title, Ui.lp(0, -2, 1));
        TextView gear = Ui.text(this, getString(R.string.settings), 24, Ui.TEXT2);
        gear.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        // шестерёнка только открывает настройки; закрываются они своей кнопкой «Назад»
        gear.setOnClickListener(v -> { if (overlay.getChildCount() == 0) push(SettingsPage.build(this), null); });
        top.addView(gear);
        root.addView(top);

        FrameLayout stack = new FrameLayout(this);
        content = new FrameLayout(this);
        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        stack.addView(content, new FrameLayout.LayoutParams(-1, -1));
        stack.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        root.addView(stack, Ui.lp(-1, 0, 1));

        LinearLayout nav = Ui.h(this);
        nav.setBackgroundColor(Ui.SURFACE | 0xFF000000);
        int[] order = LayoutPrefs.tabOrder();
        for (int id : order) {
            final int tabId = id;
            TextView t = Ui.text(this, getString(Labels.tab(id)), 14, Ui.TEXT2);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
            t.setOnClickListener(v -> selectTab(tabId));
            tabs[id] = t;
            nav.addView(t, Ui.lp(0, -2, 1));
        }
        root.addView(nav);
        rootStack.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(rootStack);

        screens = new Screen[]{new LiveScreen(this), new FileScreen(this), new TextScreen(this), new VoiceScreen(this)};
        selectTab(b == null ? order[0] : b.getInt("tab", order[0]));
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", Math.max(cur, 0));
    }

    void selectTab(int i) {
        while (overlay.getChildCount() > 0) pop();
        if (i == cur) return;
        if (cur >= 0) screens[cur].onHide();
        cur = i;
        content.removeAllViews();
        content.addView(screens[i].view());
        screens[i].onShow();
        for (int k = 0; k < tabs.length; k++) {
            tabs[k].setTextColor(k == i ? Ui.LINK : Ui.TEXT2);
            tabs[k].setTypeface(null, k == i ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    /** Открывает страницу поверх экрана (верхняя панель с шестерёнкой при этом скрывается). */
    void push(View page, Runnable onClose) {
        overlay.addView(page, new FrameLayout.LayoutParams(-1, -1));
        closers.add(onClose);
        overlay.setVisibility(View.VISIBLE);
        top.setVisibility(View.GONE);
    }

    void pop() {
        int n = overlay.getChildCount();
        if (n == 0) return;
        overlay.removeViewAt(n - 1);
        Runnable r = closers.remove(closers.size() - 1);
        if (overlay.getChildCount() == 0) {
            overlay.setVisibility(View.GONE);
            top.setVisibility(View.VISIBLE);
        }
        if (r != null) r.run();
        applyThemeIfChanged();
    }

    /** Применяет новые цвета, расположение и фон: пересоздаёт окно, когда закрыты все страницы настроек. */
    void applyThemeIfChanged() {
        if (Theme.dirty && overlay.getChildCount() == 0) {
            Theme.dirty = false;
            recreate();
        }
    }

    @Override public void onBackPressed() {
        if (overlay.getChildCount() > 0) pop(); else super.onBackPressed();
    }

    @Override protected void onResume() {
        super.onResume();
        backdrop.resume();
        if (cur >= 0) screens[cur].onShow();
    }

    @Override protected void onPause() {
        if (cur >= 0) screens[cur].onHide();
        backdrop.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        Tts.release();
        super.onDestroy();
    }
}
