/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pe.pi.whipi.util;

import com.phono.srtplight.Log;
import java.util.Optional;
import java.util.function.Consumer;

/**
 *
 * @author thp
 */
public class AnswerParser {

    private final String answer;

    public AnswerParser(String lines) {
        answer = lines;
    }

    private Optional<String> getLine(String sol) {
        return answer.lines().filter((s) -> {
            return s.startsWith(sol);
        }).findFirst();
    }

    private String getArg(String sol) {
        String[] ret = new String[1];
        Optional<String> li = getLine(sol);
        li.ifPresent(new Consumer<String>() {
            @Override
            public void accept(String l) {
                ret[0] = l.substring(sol.length());
            }
        });
        return ret[0];
    }

    public String getUfrag() {
        return getArg("a=ice-ufrag:");
    }

    public String getUpass() {
        return getArg("a=ice-pwd:");
    }

    public String getFingerprint() {
        return getArg("a=fingerprint:sha-256 ");
    }

    public String[] getCandidates() {
        return answer.lines().filter(l -> l.startsWith("a=candidate")).toArray(String[]::new);
    }

    public boolean mediaMatch(boolean v, boolean a) {
        boolean ret = false;
        long lc = a ? 1 : 0;
        lc += v ? 1 : 0;
        var alc = answer.lines().filter((s) -> {
            return s.startsWith("m=");
        }).count();
        if (lc == alc) {
            Optional<String> hv = getLine("m=video");
            Optional<String> ha = getLine("m=audio");
            ret = ((hv.isPresent() == v) && (ha.isPresent() == a));
        } else {
            Log.warn("Wrong number of m= lines expected" + lc + " got " + alc);
        }
        return ret;
    }

    public static void main(String[] args) {
        Log.setLevel(Log.DEBUG);
        String ta
                = "v=0\r\no=- 3816661123106074949 3 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=group:BUNDLE video audio\r\na=msid-semantic: WMS\r\nm=video 9 RTP/SAVPF 96\r\nc=IN IP4 0.0.0.0\r\na=rtcp:9 IN IP4 0.0.0.0\r\na=candidate:4197120840 1 udp 2113937151 133765d4-ab5d-4d18-8cae-a9f2823b8739.local 56987 typ host generation 0 network-cost 999\r\na=candidate:3221732995 1 udp 2113939711 60f1227c-3e07-42d8-90d6-75c9158c066e.local 53848 typ host generation 0 network-cost 999\r\na=ice-ufrag:z8jr\r\na=ice-pwd:egVeSPal0zUJWkBg8ZVfWtNi\r\na=ice-options:trickle\r\na=fingerprint:sha-256 A5:54:36:91:F7:35:20:13:29:28:11:E3:21:68:E8:EC:E7:B5:DC:B6:26:68:5A:49:41:FB:8B:AA:F8:66:F7:B3\r\na=setup:active\r\na=mid:video\r\na=recvonly\r\na=rtcp-mux\r\na=rtpmap:96 H264/90000\r\na=rtcp-fb:96 goog-remb\r\na=rtcp-fb:96 nack\r\na=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f\r\nm=audio 9 UDP/TLS/RTP/SAVP 111\r\nc=IN IP4 0.0.0.0\r\na=rtcp:9 IN IP4 0.0.0.0\r\na=ice-ufrag:z8jr\r\na=ice-pwd:egVeSPal0zUJWkBg8ZVfWtNi\r\na=ice-options:trickle\r\na=fingerprint:sha-256 A5:54:36:91:F7:35:20:13:29:28:11:E3:21:68:E8:EC:E7:B5:DC:B6:26:68:5A:49:41:FB:8B:AA:F8:66:F7:B3\r\na=setup:active\r\na=mid:audio\r\na=recvonly\r\na=rtcp-mux\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 minptime=10;useinbandfec=1\r\n";
        var ap = new AnswerParser(ta);
        var m = ap.mediaMatch(true, true);
        Log.debug("mediaMatch =" + m);
        var ufrag = ap.getUfrag();
        Log.debug("ufrag  =" + ufrag);
        var upass = ap.getUpass();
        Log.debug("upass =" + upass);
        var finger = ap.getFingerprint();
        Log.debug("finger =" + finger);
        var rcandies = ap.getCandidates();
        for (var r : rcandies) {
            Log.debug("candidate =" + r);
        }

    }

}
