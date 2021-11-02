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
package pe.pi.whipi;

import com.ipseorama.slice.ORTC.RTCDtlsPacket;
import com.ipseorama.slice.ORTC.RTCEventData;
import pe.pi.whipi.util.AnswerParser;
import com.ipseorama.slice.ORTC.RTCIceCandidate;
import com.ipseorama.slice.ORTC.RTCIceCandidatePair;
import com.phono.srtplight.Log;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import pe.pi.whipi.util.CandidateTransport;
import pe.pi.whipi.util.OfferMaker;

/**
 *
 * @author thp
 */
class Whipi {

    private static Long SN;

    String uri;
    String token;
    ICE slice;
    DTLS dtls;
    RTP rtp;
    String ffp;
    SecureRandom random = new SecureRandom();
    private Integer vssrc;
    private Integer assrc;

    HttpClient client;
    private Optional<String> resource;

    Whipi(String u, String t) {
        uri = u;
        token = t;
        vssrc = Math.abs(random.nextInt());
        //assrc = Math.abs(random.nextInt());
        client = HttpClient.newHttpClient();
        try {
            getLinks();
        } catch (Exception x) {
            Log.warn("OPTIONS on " + uri + " failed because of " + x.getMessage());
        }
        rtp = new RTP(vssrc, 96) {

        };
        dtls = new DTLS(random) {
            @Override
            protected String makeCn() {
                String ret = null;
                if (SN != null) {
                    Log.warn("Making a new unique certificate for " + SN + " this may take a minute...");
                    ret = "whipi-" + SN + "-GPL";
                    Log.debug("cn = " + ret);
                }
                return ret; // the CN is the serial number of the Pi
            }

            @Override
            public void onReady() {
                Properties[] props = this.extractCryptoProps();
                rtp.setCrypto(props);
                rtp.start();
            }
        };
        slice = new ICE(random) {
            @Override
            void onGathered() {
                try {
                    String offer = makeOffer();
                    String answer = sendOffer(offer);
                    AnswerParser ap = new AnswerParser(answer);
                    boolean v = vssrc != null;
                    boolean a = assrc != null;
                    if (ap.mediaMatch(v, a)) {
                        String rufrag = ap.getUfrag();
                        String rupass = ap.getUpass();
                        String[] rcandy = ap.getCandidates();
                        ffp = ap.getFingerprint();
                        slice.connect(rufrag, rupass, rcandy);
                    } else {
                        throw new Exception("Media match error");
                    }
                } catch (Exception x) {
                    Log.error("Failed to make or send an offer/answer " + x.getMessage());
                    System.exit(-1);
                }
            }

            @Override
            void onConnected(RTCIceCandidatePair scp) {
                scp.onRTP = (rtppkt) -> {
                    if (rtppkt instanceof RTCDtlsPacket) {
                        byte data[] = ((RTCDtlsPacket) rtppkt).data;
                        rtp.inbound(rtppkt);
                    }
                };
                final CandidateTransport cdt = new CandidateTransport(getTransport());
                rtp.setCandidateTransport(cdt);
                scp.onDtls = (RTCEventData pkt) -> {
                    if (pkt instanceof RTCDtlsPacket) {
                        byte data[] = ((RTCDtlsPacket) pkt).data;
                        cdt.enqueue(data);
                    }
                };
                dtls.start(cdt, ffp);
                Log.debug("ICE connected! to " + scp.getFarIp());
            }
        };
    }

    private String makeOffer() throws Exception {
        ArrayList<RTCIceCandidate> cs = slice.getCandidates();
        String ufrag = slice.getLfrag();
        String upass = slice.getLpass();

        dtls.mkCertNKey();
        String fingerprint = dtls.getPrint(true);

        return OfferMaker.makeOffer(cs, ufrag, upass, vssrc, assrc, fingerprint);
    }

    private void printOffer(String offer) {
        char[] bsr = {'\\', 'r'};
        char[] bsn = {'\\', 'n'};
        String soffer = offer.replace("\r", new String(bsr)).replace("\n", new String(bsn));
        Log.debug("desc = {type:\"offer\", sdp:\"" + soffer + "\"};");
    }

    private void getLinks() throws Exception {
        HttpRequest.Builder bu = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(10))
                .method("OPTIONS", BodyPublishers.noBody());
        if (token != null) {
            bu = bu.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = bu.build();
        HttpResponse<String> response
                = client.send(request, BodyHandlers.ofString());
        int status = response.statusCode();

        Log.debug("headers :");
        response.headers().map().
                forEach((String k, List<String> vs) -> {
                    for (String v : vs) {
                        Log.debug("\t" + k + ":" + v);
                    }
                });
        Log.debug("Options status :" + status);
    }

    private String sendOffer(String offer) throws Exception {
        printOffer(offer);
        HttpRequest.Builder bu = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/sdp")
                .POST(BodyPublishers.ofString(offer));
        if (token != null){
               bu=bu.header("Authorization", "Bearer " + token);
        }
        HttpRequest request= bu.build();
        HttpResponse<String> response
                = client.send(request, BodyHandlers.ofString());
        int status = response.statusCode();
        Log.debug("status :" + status);
        String answer = response.body();
        Log.debug("answer :\n" + answer);
        if (status != 201) {
            throw new java.lang.IllegalArgumentException("" + status);
        }
        resource = response.headers().firstValue("Location");
        if (resource.isPresent()) {
            Log.debug("Resource is " + resource);
        }
        return answer;
    }

    void start() {
        slice.gather();
    }

    void quit() {
        System.exit(0);
    }

    boolean isRunning() {
        return true;
    }

    public final static void checkPi() {
        Path cpuinfo = Paths.get("/proc/cpuinfo");
        if (Files.isReadable(cpuinfo)) {
            try {
                String[] a = {"Serial", "Model"};
                String device = "Raspberry Pi";
                List<String> targs = Arrays.asList(a);
                HashMap<String, String> res = new HashMap();
                Files.lines(cpuinfo).map((l) -> l.split(":"))
                        .filter((kv) -> kv.length == 2)
                        .map((kv) -> {
                            kv[0] = kv[0].trim();
                            kv[1] = kv[1].trim();
                            return kv;
                        })
                        .filter((kv) -> targs.contains(kv[0]))
                        .forEach((kv) -> res.put(kv[0], kv[1]));
                String m = res.get(a[1]);
                String s = res.get(a[0]);

                SN = Long.parseLong(s, 16);
                if ((m == null) || (!m.startsWith(device))) {
                    System.exit((int) (Integer.MAX_VALUE & SN));
                }
                java.security.Security.insertProviderAt(new BouncyCastleProvider(), 0);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } else {
            System.exit(0);
        }
    }

}
