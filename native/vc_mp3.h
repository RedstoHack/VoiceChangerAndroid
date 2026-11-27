#ifndef VC_MP3_H
#define VC_MP3_H
/* Тонкая обёртка над LAME: без JNI, чтобы её можно было проверить отдельно. */
typedef struct VcMp3 VcMp3;

/* ch: 1 — моно (сэмплы подряд), 2 — стерео (interleaved). Возвращает NULL при ошибке. */
VcMp3 *vc_mp3_open(int rate, int ch, int kbps);
/* Возвращает число записанных байт или отрицательный код ошибки LAME. */
int vc_mp3_encode(VcMp3 *h, const short *pcm, int frames, unsigned char *out, int outcap);
int vc_mp3_flush(VcMp3 *h, unsigned char *out, int outcap);
void vc_mp3_close(VcMp3 *h);
#endif
