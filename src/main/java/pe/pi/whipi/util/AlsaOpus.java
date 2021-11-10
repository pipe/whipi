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

import com.phono.api.Codec;
import com.phono.api.CodecList;
import com.phono.applet.audio.phone.PhonoAudioShim;
import com.phono.audio.AudioFace;
import com.phono.audio.AudioReceiver;
import com.phono.audio.StampedAudio;
import com.phono.audio.phone.PhonoAudioPropNames;
import com.phono.srtplight.Log;

/**
 *
 * @author thp
 */
abstract public class AlsaOpus {

    private final PhonoAudioShim audio;
    private final CodecList codecList;
    static String CODEC = "OPUS";
    static String SDPCODEC = "opus/48000/2";
    static int CTYPE = 111;
    private boolean first = true;
    private long then;
    long seqno = 0;

    public AlsaOpus() throws Exception {
        Log.debug("AlsaSrtp init");
        audio = new PhonoAudioShim();
        audio.setAudioProperty(PhonoAudioPropNames.DOEC, "false");
        codecList = new CodecList(audio);
    }

    public void close() {
        audio.stopRec();
        audio.stopPlay();
        audio.destroy();
    }

    public void startMedia() throws Exception {

        Codec codecs[] = this.codecList.getCodecs();
        Codec codec = codecs[0];
        for (Codec c : codecs) {
            Log.debug("Codec name " + c.name);
            if (CODEC.equals(c.name)) {
                codec = c;
                break;
            }

        }
        audio.init(codec.iaxcn, 100);

        AudioReceiver ar = (AudioFace af, int avail) -> {
            if (first) {
                first = false;
                Log.info("Sending media.");
                then = System.currentTimeMillis();
            }
            try {
                StampedAudio sa = af.readStampedAudio();
                int fac = CodecList.getFac(af.getCodec());
                while (sa != null) {
                    long dur = sa.getStamp();
                    long ts = dur * fac;
                    sendRTP(seqno, sa.getData(), false, ts);
                    seqno++;
                    sa = af.readStampedAudio();
                }
            } catch (Exception ex) {
                Log.error(ex.toString());
            }
        };
        audio.addAudioReceiver(ar);
        audio.startRec();
    }
    public Long getLevel(){
        double[] v = audio.getEnergy();
        return (long) (100 * v[0])/Short.MAX_VALUE;
    }

    protected abstract void sendRTP(long seqno, byte[] payload, boolean mark, long stamp);

    public static void main(String argv[]) {
        Log.setLevel(Log.ALL);
        StringBuffer bret = new StringBuffer("{\n");
        PhonoAudioShim.getMixersJSON(bret);
        bret.append("}\n");
        String deviceList = bret.toString();
        Log.debug("audio list is :" + deviceList);

        PhonoAudioShim a = new PhonoAudioShim();

        CodecList cl = new CodecList(a);

    }

}
