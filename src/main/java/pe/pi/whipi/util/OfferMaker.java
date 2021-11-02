/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pe.pi.whipi.util;

import com.ipseorama.slice.ORTC.RTCIceCandidate;
import java.util.ArrayList;

/**
 *
 * @author thp
 */
public class OfferMaker {

    public static String makeOffer(ArrayList<RTCIceCandidate> cs, String ufrag, String upass, Integer videoSsrc, Integer audioSsrc, String fingerprint) {
        String ret
                = "v=0\n"
                + "o=- 4648475892259889561 3 IN IP4 127.0.0.1\n"
                + "s=-\n"
                + "t=0 0\n";
        if ((videoSsrc != null) && (audioSsrc != null)) {
            ret += "a=group:BUNDLE video audio\n";
        }
        for (var c : cs) {
            String mc = c.toString();
            int idx = mc.indexOf(" ");
            String sdpCandy = "a="+mc.substring(idx).trim();
            ret += sdpCandy;
            ret += "\n";
        }
        ret += "a=ice-ufrag:" + ufrag + "\n";
        ret += "a=ice-pwd:" + upass + "\n";
        ret += "a=fingerprint:sha-256 " + fingerprint + "\n";
        if (videoSsrc != null) {
            ret += "m=video 9 UDP/TLS/RTP/SAVPF 96\n"
                    + "c=IN IP4 0.0.0.0\n"
                    + "a=rtcp:9 IN IP4 0.0.0.0\n"
                    + "a=setup:passive\n"
                    + "a=mid:video\n"
                    + "a=sendonly\n"
                    + "a=rtcp-mux\n"
                    + "a=rtpmap:96 H264/90000\n"
                    + "a=rtcp-fb:96 nack\n"
                    + "a=rtcp-fb:96 goog-remb\n"
                    + "a=fmtp:96 packetization-mode=1;profile-level-id=42e01f\n"
                    + "a=ssrc:"+videoSsrc+" cname:whipiVideo\n";
        }
        if (audioSsrc!= null) {
            ret
                    += "m=audio 9 UDP/TLS/RTP/SAVPF 111\n"
                    + "c=IN IP4 0.0.0.0\n"
                    + "a=rtcp:9 IN IP4 0.0.0.0\n"
                    + "a=setup:passive\n"
                    + "a=mid:audio\n"
                    + "a=sendonly\n"
                    + "a=rtcp-mux\n"
                    + "a=rtpmap:111 opus/48000/2\n"
                    + "a=ssrc:"+audioSsrc+" cname:whipiAudio\n";
        }
        ret = ret.replace("\n", "\r\n");
        return ret;
    }
}
