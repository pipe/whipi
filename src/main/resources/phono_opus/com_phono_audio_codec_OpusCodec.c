#include <jni.h>
#include <opus/opus.h>
#include <string.h>
#include <alloca.h>
#include "com_phono_audio_codec_OpusCodec.h"

#define MAXPKT_SZ 1200

void * getDirectAddress(JNIEnv *env, jobject this,char *name){
        jclass clz;
        jfieldID fld;
        jobject bb;
        void * dbp;
        dbp = NULL;

        clz = (*env)->GetObjectClass(env, this);
        if (clz != NULL){
                fld = (*env)->GetFieldID(env, clz,name,"Ljava/nio/ByteBuffer;");
                if (fld != NULL){
                        bb= (*env)->GetObjectField(env, this,fld);
                        if (bb != NULL){
                                dbp = (*env)->GetDirectBufferAddress(env,  bb);
                        } else {
                                printf("Can't get direct buffer address\n");
                        }
                } else {
                        printf("Can't find ByteBuffer Fld\n");
                }
        } else {
                printf("Cant find class\n");
        }
        return dbp;
}
OpusEncoder *getEnc(JNIEnv *env, jobject this){
      void * addr = getDirectAddress(env, this,"_enc");
      return (OpusEncoder *) addr;
}
OpusDecoder *getDec(JNIEnv *env, jobject this){
      void * addr = getDirectAddress(env, this,"_dec");
      return (OpusDecoder *) addr;
}

/*
 * Class:     com_phono_audio_codec_OpusCodec
 * Method:    getDecoderSize
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_phono_audio_codec_OpusCodec_getDecoderSize
  (JNIEnv *env, jobject this, jint chans){
	jint ret = opus_decoder_get_size(chans);
	return ret;
}

/*
 * Class:     com_phono_audio_codec_OpusCodec
 * Method:    getEncoderSize
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_phono_audio_codec_OpusCodec_getEncoderSize
  (JNIEnv *env , jobject this, jint chans){
        jint ret = opus_encoder_get_size(chans);
        return ret;
}

/*
 * Class:     com_phono_audio_codec_OpusCodec
 * Method:    initEncoder
 * Signature: (III)V
 */
JNIEXPORT void JNICALL Java_com_phono_audio_codec_OpusCodec_initEncoder
  (JNIEnv *env, jobject this, jint rate, jint channels, jint application){
	OpusEncoder *enc;
	enc = getEnc(env,this);
        opus_encoder_init (enc,(int)rate,(int)channels,(int)application);
}

/*
 * Class:     com_phono_audio_codec_OpusCodec
 * Method:    initDecoder
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_com_phono_audio_codec_OpusCodec_initDecoder
  (JNIEnv *env, jobject this, jint rate, jint channels){
	OpusDecoder *dec;
        dec = getDec(env,this);
        opus_decoder_init(dec,(int)rate,(int)channels);
}

/*
 * Class:     com_phono_audio_codec_OpusCodec
 * Method:    opusDecode
 * Signature: ([BI)[S
 */
JNIEXPORT jshortArray JNICALL Java_com_phono_audio_codec_OpusCodec_opusDecode
  (JNIEnv *env, jobject this , jbyteArray jwire , jint doFec){
          jshortArray jaudio;
          jshort *top;
          jshort *op;
          jbyte *offs;
          jsize wlen =0;
          int res;
          int alen = 0;
          int maxaudio = (48000 *4 * 60 )/1000;
                        // (max sample rate * 16 bit * stereo * maxptime)/ms
          OpusDecoder *dec;
          dec = getDec(env,this);

          // memory faffing
          offs = (*env)->GetByteArrayElements(env, jwire, 0);
          wlen = (*env)->GetArrayLength(env, jwire);
	  //printf("decoder = %8x ip = %8x len = %d\n", dec,offs,wlen);
          
          top = alloca(maxaudio);
	  alen = opus_decode(dec,offs,wlen,top,maxaudio,doFec);
          //printf("decoder output len = %d\n", alen);

          if (alen > 0) {
             jaudio = (*env)->NewShortArray(env,alen);
             op =  (*env)->GetShortArrayElements(env, jaudio, 0);
             memcpy(op,top,alen*2);
             (*env)->ReleaseShortArrayElements(env, jaudio, op, 0);
          }

          // memory unfaffing
          (*env)->ReleaseByteArrayElements(env, jwire, offs, 0);
          return jaudio;
}


/*
 * Class:     com_phono_audio_codec_OpusCodec
 * Method:    opusEncode
 * Signature: ([S)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_phono_audio_codec_OpusCodec_opusEncode
  (JNIEnv *env , jobject this ,  jshortArray jaudio){

          jint nbBits = 0;
          jshort *ip;
          jbyte *offs;
          char * wire;
          opus_int32 nbBytes;
          opus_int32 ipl;
          OpusEncoder *enc;
          enc = getEnc(env,this);

          // memory faffing
          ip =  (*env)->GetShortArrayElements(env, jaudio, 0);
          ipl = (*env)->GetArrayLength(env, jaudio);
          wire = alloca(MAXPKT_SZ);
	  //printf("encoder = %8x ip = %8x len = %d\n", enc,ip,ipl);
	  nbBytes = opus_encode	(enc,ip,ipl,wire,MAXPKT_SZ);
          
          jbyteArray jwire = (*env)->NewByteArray(env, nbBytes);
          offs = (*env)->GetByteArrayElements(env, jwire, 0);
	  //printf("memcpy to %8x from  %8x len = %d\n", offs,wire,nbBytes);
          memcpy(offs,wire,nbBytes);

          // memory unfaffing

          (*env)->ReleaseShortArrayElements(env, jaudio, ip, 0);
          (*env)->ReleaseByteArrayElements(env, jwire, offs, 0);
          return jwire;

}

/*
 * Class:     com_phono_audio_codec_OpusCodec
 * Method:    opusSetCtl
 * Signature: (III)V
 */
JNIEXPORT void JNICALL Java_com_phono_audio_codec_OpusCodec_opusSetCtl
  (JNIEnv *env, jobject this,  jint ctl , jint val , jint eord){
        OpusEncoder *enc;
        OpusDecoder *dec;
        if (eord == 0){
                enc = getEnc(env,this);
                opus_encoder_ctl(enc,ctl,val);
        }else{
                dec = getDec(env,this);
                opus_decoder_ctl(dec,ctl,val);
        }
}

/*
 * Class:     com_phono_audio_codec_OpusCodec
 * Method:    opusGetCtl
 * Signature: ([BII)I
 */
JNIEXPORT jint JNICALL Java_com_phono_audio_codec_OpusCodec_opusGetCtl
  (JNIEnv *env, jobject this ,  jint ctl , jint eord){
        OpusEncoder *enc;
        OpusDecoder *dec;
        jint ret = 0;
	if (eord == 0){
                enc = getEnc(env,this);
		opus_encoder_ctl(enc,ctl,&ret);
        }else{
                dec = getDec(env,this);
                opus_decoder_ctl(dec,ctl,&ret);
	}
        return ret;
}

/*
 * Class:     com_phono_audio_codec_OpusCodec
 * Method:    freeCodec
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_phono_audio_codec_OpusCodec_freeCodec
  (JNIEnv *env, jobject this ){
 	struct codec *co;

}
/*
  (JNIEnv * env, jobject this,jbyteArray jcodec) {
	struct codec *co;    
        char *rcs;
        jstring ret;
        co = getCodec(env,jcodec);
	switch(co->co_error){
		case OPUS_OK:
		rcs =" 	No error. "; break;
		case OPUS_BAD_ARG:
		rcs =" 	One or more invalid/out of range arguments. "; break;
		case OPUS_BUFFER_TOO_SMALL:
		rcs =" 	The mode struct passed is invalid. "; break;
		case OPUS_INTERNAL_ERROR:
		rcs =" 	An internal error was detected. "; break;
		case OPUS_INVALID_PACKET:
		rcs =" 	The compressed data passed is corrupted. "; break;
		case OPUS_UNIMPLEMENTED:
		rcs =" 	Invalid/unsupported request number. "; break;
		case OPUS_INVALID_STATE:
		rcs =" 	An encoder or decoder structure is invalid or already freed. "; break;
		case OPUS_ALLOC_FAIL:
		rcs =" 	Memory allocation has failed. 	"; break;
		default: rcs = "Unknown error state"; break;
	}
        ret = (*env)->NewStringUTF(env , rcs);	
	return ret;
}
*/
