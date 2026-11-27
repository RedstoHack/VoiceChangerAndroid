package voice.changer.app;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Верхняя кнопка «Голос: …» — открывает окно выбора голоса. */
final class VoiceRow {
    final LinearLayout view;
    private final TextView title;
    private final Context c;

    VoiceRow(Context c) {
        this.c = c;
        view = Ui.navRow(c, "", null, v -> VoicePicker.show(c, this::refresh));
        title = Ui.navTitle(view);
        refresh();
    }

    void refresh() {
        title.setText(c.getString(R.string.voice_now, Labels.presetName(c, Store.get().preset)));
    }

    void setEnabled(boolean e) {
        view.setEnabled(e);
        view.setAlpha(e ? 1f : 0.5f);
    }
}
