#include <jni.h>
#include <stdio.h>
#include "commons_java_jni_StandardOutput.h"

JNIEXPORT void JNICALL Java_commons_java_jni_StandardOutput_printStr(JNIEnv *env, jobject obj, jstring str) {
    if (!str) {
        printf("Nothing to print");
        return;
    }

    const char* cstr = (*env)->GetStringUTFChars(env, str, NULL);
    if (cstr == NULL) {
        return;
    }

    printf("%s", cstr);

    (*env)->ReleaseStringUTFChars(env, str, cstr);
}