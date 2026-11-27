#include <stddef.h>
#include <stdint.h>
#include <jni.h>
#include "vc_mp3.h"

/* Класс voice.changer.app.Mp3Encoder */

JNIEXPORT jlong JNICALL Java_voice_changer_app_Mp3Encoder_nOpen(JNIEnv *env, jclass cls, jint rate, jint ch, jint kbps) {
    return (jlong) (intptr_t) vc_mp3_open(rate, ch, kbps);
}

JNIEXPORT jint JNICALL Java_voice_changer_app_Mp3Encoder_nEncode(JNIEnv *env, jclass cls, jlong h, jshortArray pcm, jint frames, jbyteArray out) {
    jshort *p = (*env)->GetShortArrayElements(env, pcm, NULL);
    jbyte *o = (*env)->GetByteArrayElements(env, out, NULL);
    jint cap = (*env)->GetArrayLength(env, out);
    int r = vc_mp3_encode((VcMp3 *) (intptr_t) h, (const short *) p, frames, (unsigned char *) o, cap);
    (*env)->ReleaseByteArrayElements(env, out, o, 0);
    (*env)->ReleaseShortArrayElements(env, pcm, p, JNI_ABORT);
    return r;
}

JNIEXPORT jint JNICALL Java_voice_changer_app_Mp3Encoder_nFlush(JNIEnv *env, jclass cls, jlong h, jbyteArray out) {
    jbyte *o = (*env)->GetByteArrayElements(env, out, NULL);
    jint cap = (*env)->GetArrayLength(env, out);
    int r = vc_mp3_flush((VcMp3 *) (intptr_t) h, (unsigned char *) o, cap);
    (*env)->ReleaseByteArrayElements(env, out, o, 0);
    return r;
}

JNIEXPORT void JNICALL Java_voice_changer_app_Mp3Encoder_nClose(JNIEnv *env, jclass cls, jlong h) {
    vc_mp3_close((VcMp3 *) (intptr_t) h);
}
