/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class genomicsdb_GenomicsDBQueryOutputStream */

#ifndef _Included_genomicsdb_GenomicsDBQueryOutputStream
#define _Included_genomicsdb_GenomicsDBQueryOutputStream
#ifdef __cplusplus
extern "C" {
#endif
#undef genomicsdb_GenomicsDBQueryOutputStream_MAX_SKIP_BUFFER_SIZE
#define genomicsdb_GenomicsDBQueryOutputStream_MAX_SKIP_BUFFER_SIZE 2048L
/*
 * Class:     genomicsdb_GenomicsDBQueryOutputStream
 * Method:    jniGenomicsDBInit
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIJJ)J
 */
JNIEXPORT jlong JNICALL Java_genomicsdb_GenomicsDBQueryOutputStream_jniGenomicsDBInit
  (JNIEnv *, jobject, jstring, jstring, jstring, jint, jint, jint, jlong, jlong);

/*
 * Class:     genomicsdb_GenomicsDBQueryOutputStream
 * Method:    jniGenomicsDBClose
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_genomicsdb_GenomicsDBQueryOutputStream_jniGenomicsDBClose
  (JNIEnv *, jobject, jlong);

/*
 * Class:     genomicsdb_GenomicsDBQueryOutputStream
 * Method:    jniGenomicsDBGetNumBytesAvailable
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_genomicsdb_GenomicsDBQueryOutputStream_jniGenomicsDBGetNumBytesAvailable
  (JNIEnv *, jobject, jlong);

/*
 * Class:     genomicsdb_GenomicsDBQueryOutputStream
 * Method:    jniGenomicsDBReadNextByte
 * Signature: (J)B
 */
JNIEXPORT jbyte JNICALL Java_genomicsdb_GenomicsDBQueryOutputStream_jniGenomicsDBReadNextByte
  (JNIEnv *, jobject, jlong);

/*
 * Class:     genomicsdb_GenomicsDBQueryOutputStream
 * Method:    jniGenomicsDBRead
 * Signature: (J[BII)I
 */
JNIEXPORT jint JNICALL Java_genomicsdb_GenomicsDBQueryOutputStream_jniGenomicsDBRead
  (JNIEnv *, jobject, jlong, jbyteArray, jint, jint);

/*
 * Class:     genomicsdb_GenomicsDBQueryOutputStream
 * Method:    jniGenomicsDBSkip
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL Java_genomicsdb_GenomicsDBQueryOutputStream_jniGenomicsDBSkip
  (JNIEnv *, jobject, jlong, jlong);

#ifdef __cplusplus
}
#endif
#endif
