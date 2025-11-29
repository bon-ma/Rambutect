#include <jni.h>
#include <android/log.h>
#include <cpu-features.h>

#define LOG_TAG "ImageProcessing"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT void JNICALL
Java_com_ultralytics_yolo_ImageProcessing_argb2yolo(
        JNIEnv *env,
        jobject thiz,
        jintArray srcArray,
        jobject destBuffer,
        jint width,
        jint height
) {

    jint *src = env->GetIntArrayElements(srcArray, 0);

    float *dest = (float *) env->GetDirectBufferAddress(destBuffer);

    if (dest == NULL) {
        return;
    }

    for (int i = 0; i < width * height; ++i) {
        uint32_t pixel = src[i];  // ARGB format
        float r = ((pixel >> 16) & 0xFF) / 255.0f;
        float g = ((pixel >> 8) & 0xFF) / 255.0f;
        float b = (pixel & 0xFF) / 255.0f;

        int idx = i * 3;
        dest[idx] = r;
        dest[idx + 1] = g;
        dest[idx + 2] = b;
    }

    env->ReleaseIntArrayElements(srcArray, src, 0);
}