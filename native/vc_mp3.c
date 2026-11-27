#include <stdlib.h>
#include "lame.h"
#include "vc_mp3.h"

struct VcMp3 {
    lame_t g;
    int ch;
};

VcMp3 *vc_mp3_open(int rate, int ch, int kbps) {
    VcMp3 *h = (VcMp3 *) calloc(1, sizeof(VcMp3));
    if (!h) return NULL;
    h->ch = ch;
    h->g = lame_init();
    if (!h->g) { free(h); return NULL; }
    lame_set_in_samplerate(h->g, rate);
    lame_set_num_channels(h->g, ch);
    lame_set_brate(h->g, kbps);
    lame_set_mode(h->g, ch == 1 ? MONO : JOINT_STEREO);
    lame_set_quality(h->g, 3);
    if (lame_init_params(h->g) < 0) { lame_close(h->g); free(h); return NULL; }
    return h;
}

int vc_mp3_encode(VcMp3 *h, const short *pcm, int frames, unsigned char *out, int outcap) {
    if (h->ch == 1)
        return lame_encode_buffer(h->g, pcm, pcm, frames, out, outcap);
    return lame_encode_buffer_interleaved(h->g, (short *) pcm, frames, out, outcap);
}

int vc_mp3_flush(VcMp3 *h, unsigned char *out, int outcap) {
    return lame_encode_flush(h->g, out, outcap);
}

void vc_mp3_close(VcMp3 *h) {
    if (!h) return;
    lame_close(h->g);
    free(h);
}
