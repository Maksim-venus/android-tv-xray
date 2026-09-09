#include <jni.h>
#include <android/log.h>

#define LOG_TAG "passwall-xray-stub"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// JNI TODO: replace this stub with AndroidLibXrayLite / libxray gomobile bindings.
// Expected drop-in: core-xray/libs/libv2ray.aar (see README).

extern "C" JNIEXPORT jstring JNICALL
Java_com_passwall_corexray_NativeXrayBridge_nativeVersion(JNIEnv *env, jobject) {
    return env->NewStringUTF("stub-0.0.0");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_passwall_corexray_NativeXrayBridge_nativeStart(JNIEnv *env, jobject, jstring configPath) {
    const char *path = env->GetStringUTFChars(configPath, nullptr);
    LOGI("nativeStart stub — not starting xray. config=%s", path ? path : "(null)");
    if (path) env->ReleaseStringUTFChars(configPath, path);
    // Non-zero: caller should fall back to StubXrayEngine.
    return -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_passwall_corexray_NativeXrayBridge_nativeStop(JNIEnv *, jobject) {
    LOGI("nativeStop stub");
    return 0;
}
