/*
Packetizer that reads H264 from /dev/videoX and sends it to a consumer.
 */
/*
    A WHIP client for raspberry pi
    Copyright (C) 2021  Tim Panton

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

/* used with permission from pi.pe gmbh */
package pe.pi.whipi.util;

import com.phono.srtplight.BitUtils;
import com.phono.srtplight.Log;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.Arrays;

/**
 *
 * @author tim
 */
public abstract  class V4l2H264 {

    private String devName;
    private Thread readerThread;
    final static int MAXPAYLOAD = 1208;
    private long firstStamp;
    private int fakessrc = 123456;
    private long vseqno;

    public V4l2H264(String dev) {
        devName = dev;
    }

    
    private static int ff_avc_find_startcode(
            byte[] byteStream,
            int beginIndex,
            int endIndex) {
        for (; beginIndex < (endIndex - 3); beginIndex++) {
            if ((byteStream[beginIndex] == 0)
                    && (byteStream[beginIndex + 1] == 0)
                    && (byteStream[beginIndex + 2] == 1)) {
                return beginIndex;
            }
        }
        return endIndex;
    }

    private void packetizeNal(long now, byte[] buff, int nal, int n) throws IOException {
        int len = n - nal;
        long stamp = 90 * (now - firstStamp);
        byte[] pay = new byte[len];
        System.arraycopy(buff, nal, pay, 0, len);

        if (len < MAXPAYLOAD) {
            sendSingleNal(stamp, pay);
        } else {
            sendFragments(stamp, pay);
        }
    }
    int unmarked[] = {6, 7, 8, 9};

    private void sendSingleNal(long stamp, byte[] pay) throws IOException {
        byte nuh = pay[0];
        int nuhType = 0x1f & nuh;
        int nri = (nuh & 0x60) >> 5;
        Log.verb("Single Nal Unit is type " + nuhType + " nri " + nri);
        boolean mark = !Arrays.stream(unmarked).anyMatch((a) -> {
            return a == nuhType;
        });
        int len = pay.length;
        while (pay[len-1] == 0){
            Log.verb("skipping zero at "+(len-1));
            len--;
        }
        byte[] npay = new byte[len];
        System.arraycopy(pay, 0, npay, 0, npay.length); // encryption craps on the data.
        vseqno++;
        sendRTP(vseqno,npay, mark, stamp);
    }

    /*
    The FU indicator octet has the following format:

       +---------------+
       |0|1|2|3|4|5|6|7|
       +-+-+-+-+-+-+-+-+
       |F|NRI|  Type   |
       +---------------+

   Values equal to 28 and 29 in the type field of the FU indicator octet
   identify an FU-A and an FU-B, respectively.  The use of the F bit is
   described in Section 5.3.  The value of the NRI field MUST be set
   according to the value of the NRI field in the fragmented NAL unit.

   The FU header has the following format:

      +---------------+
      |0|1|2|3|4|5|6|7|
      +-+-+-+-+-+-+-+-+
      |S|E|R|  Type   |
      +---------------+

   S:     1 bit
          When set to one, the Start bit indicates the start of a
          fragmented NAL unit.  When the following FU payload is not the
          start of a fragmented NAL unit payload, the Start bit is set
          to zero.





Wang, et al.                 Standards Track                   [Page 31]

RFC 6184           RTP Payload Format for H.264 Video           May 2011


   E:     1 bit
          When set to one, the End bit indicates the end of a fragmented
          NAL unit, i.e., the last byte of the payload is also the last
          byte of the fragmented NAL unit.  When the following FU
          payload is not the last fragment of a fragmented NAL unit, the
          End bit is set to zero.

   R:     1 bit
          The Reserved bit MUST be equal to 0 and MUST be ignored by the
          receiver.

   Type:  5 bits
          The NAL unit payload type as defined in Table 7-1 of [1].

     */
    static int ENDBIT = 9;
    static int STARTBIT = 8;
    static int NRIBITS = 1;
    static int FUI_TYPE = 3;
    static int FUH_TYPE = 11;

    private void sendFragments(long stamp, byte[] pay) throws IOException {
        byte nuh = pay[0];
        int nri = (nuh & 0x60) >> 5;
        int nuhType = 0x1f & nuh;
        Log.verb("Will split NAL " + nuhType + " int FUA's Nal " + " nri " + nri);
        int offs = 1;
        int paylen = pay.length;
        while (offs < paylen) {
            boolean mark = false;
            int remains = pay.length - offs;
            int len = Math.min(remains, MAXPAYLOAD);
            byte[] fup = new byte[len + 2];

            if (remains == len) {
                BitUtils.setBit(fup, ENDBIT);
                mark = true;
            }
            if (offs == 1) {
                BitUtils.setBit(fup, STARTBIT);
            }
            BitUtils.copyBits(nri, 2, fup, NRIBITS);
            BitUtils.copyBits(28, 5, fup, FUI_TYPE);
            BitUtils.copyBits(nuhType, 5, fup, FUH_TYPE);

            System.arraycopy(pay, offs, fup, 2, len);
            //Log.debug("-> "+Packet.getHex(fup));
            vseqno++;
            sendRTP(vseqno,fup, mark, stamp);
            offs += len;
        }
    }



    public void startListeningV() throws SocketException {
        // on the pi do this:
//v4l2-ctl --set-fmt-video width=1024,height=768,pixelformat=4 -d /dev/video0 --set-ctrl video_bitrate=2048000,h264_profile=1,repeat_sequence_header=1,vertical_flip=1
// v4l2-ctl  -d /dev/video0 --set-ctrl video_bitrate=512000 # also works mid flight....
    readerThread = new Thread(() -> {
            InputStream input = null;
            try {
                byte buff[] = new byte[64 * 4096];
                input = new FileInputStream(devName);
                long then = System.currentTimeMillis();
                while (readerThread != null) {
                    int n = input.read(buff);
                    long now = System.currentTimeMillis();
                    Log.verb("read " + n + " delay =" + (now - then));
                    int nal = 0;
                    nal = ff_avc_find_startcode(buff, nal, n);
                    //long cnt = 0;
                    while (nal < n) {
                        nal += 3;
                        int nextnal = ff_avc_find_startcode(buff, nal, n);
                        int nallen = nextnal - nal;
                        Log.verb("nal body at " + nal + " len " + nallen);

                        byte[] nalbuf = new byte[nallen];
                        System.arraycopy(buff, nal, nalbuf, 0, nallen);
                        packetizeNal(now /*+ cnt*/, nalbuf, 0, nallen);
                        //cnt++;
                        nal = nextnal;
                    }
                    then = now;
                }
            } catch (Exception ex) {
                Log.error(this.getClass().getName() + " cant read " + devName + " because " + ex.getMessage());
            } finally {
                try {
                    input.close();
                    Log.debug("closed");
                } catch (IOException ex) {
                    Log.error(this.getClass().getName() + " cant close " + devName + " because " + ex.getMessage());
                }
            }
        }, devName + "-reader");
        readerThread.start();
        firstStamp = System.currentTimeMillis();
    }

    public void close() {
        Thread jthread = readerThread;
        readerThread = null;
    }

    

    protected abstract void sendRTP(long seqno, byte[] payload, boolean mark, long stamp) ;


}
