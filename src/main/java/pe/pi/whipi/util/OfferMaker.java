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

package pe.pi.whipi.util;

import com.ipseorama.slice.ORTC.RTCIceCandidate;
import java.util.ArrayList;

/**
 *
 * @author thp
 */
public class OfferMaker {

    public static String makeOffer(ArrayList<RTCIceCandidate> cs, String ufrag, String upass, Long videoSsrc, Long audioSsrc, String fingerprint, String cname) {
        String ret
                = "v=0\n"
                + "o=- 4648475892259889561 3 IN IP4 127.0.0.1\n"
                + "s=-\n"
                + "t=0 0\n";
        if ((videoSsrc != null) && (audioSsrc != null)) {
            ret += "a=group:BUNDLE 1 2\n";
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
                    + "a=mid:1\n"
                    + "a=sendonly\n"
                    + "a=rtcp-mux\n"
                    + "a=rtpmap:96 H264/90000\n"
                    + "a=rtcp-fb:96 nack\n"
                    + "a=rtcp-fb:96 goog-remb\n"
                    + "a=fmtp:96 packetization-mode=1;profile-level-id=42e01f\n"
                    + "a=ssrc:"+videoSsrc+" cname:"+cname+"\n";
        }
        if (audioSsrc!= null) {
            ret
                    += "m=audio 9 UDP/TLS/RTP/SAVPF 111\n"
                    + "c=IN IP4 0.0.0.0\n"
                    + "a=rtcp:9 IN IP4 0.0.0.0\n"
                    + "a=setup:passive\n"
                    + "a=mid:2\n"
                    + "a=sendonly\n"
                    + "a=rtcp-mux\n"
                    + "a=rtpmap:111 opus/48000/2\n"
                    + "a=ssrc:"+audioSsrc+" cname:"+cname+"\n";
        }
        ret = ret.replace("\n", "\r\n");
        return ret;
    }
}
