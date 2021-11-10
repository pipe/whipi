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
import com.ipseorama.slice.ORTC.RTCRtpPacket;
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
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import pe.pi.whipi.util.CandidateTransport;
import pe.pi.whipi.util.OfferMaker;

/**
 *
 * @author thp
 */
public class Whipi {

    private static Long SN;

    String uri;
    String token;
    ICE slice;
    DTLS dtls;
    RTP rtp;
    String ffp;
    SecureRandom random = new SecureRandom();
    private Long vssrc;
    private Long assrc;
    Timer tock;

    HttpClient client;
    private Optional<String> resource;

    public Whipi(String u, String t) {
        uri = u;
        token = t;
        vssrc = (long) Math.abs(random.nextInt()); // remove this if you want to run audio only
        assrc = (long) Math.abs(random.nextInt()); // remove this if you want to run video only
        client = HttpClient.newHttpClient();
        try {
            getLinks();
        } catch (Exception x) {
            Log.warn("OPTIONS on " + uri + " failed because of " + x.getMessage());
        }
        rtp = new RTP(vssrc, 96, assrc, 111) {

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
                Log.info("DTLS complete.");
                Properties[] props = this.extractCryptoProps();
                rtp.setCrypto(props);
                rtp.start();

            }
        };
        slice = new ICE(random) {
            @Override
            void onGathered() {
                Log.info("Got local ip address(es)");
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
                Log.info("ICE has connected to server at" + scp.getFarIp());
                scp.onRTP = (rtppkt) -> {
                    if (rtppkt instanceof RTCRtpPacket) {
                        rtp.inbound((RTCRtpPacket) rtppkt);
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
            }
        };
    }

    private String makeOffer() throws Exception {
        ArrayList<RTCIceCandidate> cs = slice.getCandidates();
        String ufrag = slice.getLfrag();
        String upass = slice.getLpass();

        dtls.mkCertNKey();
        String fingerprint = dtls.getPrint(true);

        return OfferMaker.makeOffer(cs, ufrag, upass, vssrc, assrc, fingerprint, Long.toHexString(SN));
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
        Log.info("Http Options status :" + status);
    }

    private String sendOffer(String offer) throws Exception {
        printOffer(offer);
        Log.info("Sending SDP offer");
        HttpRequest.Builder bu = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/sdp")
                .POST(BodyPublishers.ofString(offer));
        if (token != null) {
            bu = bu.header("Authorization", "Bearer " + token);
        }
        HttpRequest request = bu.build();
        HttpResponse<String> response
                = client.send(request, BodyHandlers.ofString());
        int status = response.statusCode();
        Log.info("Http offer status :" + status);
        String answer = response.body();
        Log.debug("answer :\n" + answer);
        if (Log.getLevel() >= Log.DEBUG) {
            response.headers().map().forEach((String k, List<String> vs) -> {
                Log.debug(k + "\t:");
                for (String v : vs) {
                    Log.debug("\t:" + v);
                }
            });
        }
        if (status != 201) {
            throw new java.lang.IllegalArgumentException("" + status);
        }
        Log.info("Got SDP answer");
        resource = response.headers().firstValue("Location");
        if (resource.isPresent()) {
            Log.info("Resource is " + resource);
        }
        return answer;
    }

    public void start() {
        slice.gather();
    }

    public void quit() {
        resource.ifPresent((luri) -> {
            URI ruri;
            URI turi;
            if (luri.startsWith("https://")) {
                ruri = URI.create(luri);
            } else {
                turi = URI.create(uri);
                ruri = turi.resolve(luri);
            }
            HttpRequest.Builder bu = HttpRequest.newBuilder()
                    .uri(ruri)
                    .timeout(Duration.ofSeconds(10))
                    .DELETE();
            if (token != null) {
                bu = bu.header("Authorization", "Bearer " + token);
            }
            HttpRequest request = bu.build();
            HttpResponse<String> response;
            try {
                response = client.send(request, BodyHandlers.ofString());
                int status = response.statusCode();
                Log.info("Http delete status :" + status);
            } catch (Exception ex) {
                Logger.getLogger(Whipi.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
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

    Hashtable<String, Long> ovstats;
    Hashtable<String, Long> oastats;

    public Hashtable<String, Long> getVideoStats() {
        if (ovstats == null) {
            ovstats = rtp.newStats(this.vssrc);
        } else {
            ovstats = rtp.getStats(ovstats);
        }
        return ovstats;
    }

    public Hashtable<String, Long> getAudioStats() {
        if (oastats == null) {
            oastats = rtp.newStats(this.assrc);
        } else {
            oastats = rtp.getStats(oastats);
        }
        return oastats;
    }
}
