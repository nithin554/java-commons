#include <jni.h>
#include <stdio.h>
#include "commons_java_jni_StandardOutput.h"

JNIEXPORT jint JNICALL Java_commons_java_jni_Math_add(JNIEnv *env, jobject obj, jint a, jint b) {
    if (!a) return b;

    if (!b) return a;

    return a + b;
}