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

import com.ipseorama.slice.ORTC.RTCRtpPacket;
import com.phono.srtplight.Log;
import com.phono.srtplight.RTCP;
import com.phono.srtplight.SRTCPProtocolImpl;
import com.phono.srtplight.SRTPProtocolImpl;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import pe.pi.whipi.util.AlsaOpus;
import pe.pi.whipi.util.CandidateTransport;
import pe.pi.whipi.util.V4l2H264;

/**
 *
 * @author thp
 */
class RTP {

    private ICESRTP outvsrtp;
    private Properties[] cprops;
    private CandidateTransport cdt;
    static int id = 10;
    private final Long vcsrc;
    int vtype = 0;
    int atype = 0;
    private V4l2H264 vidSender;
    private AlsaOpus audioSender;
    private ICESRTP outasrtp;
    private final Long acsrc;
    private SRTCPProtocolImpl outsrtcp;
    private boolean started;
    private Hashtable<String, Long> aStats;
    private Hashtable<String, Long> vStats;

    void inbound(RTCRtpPacket pkt) {

        try {
            DatagramPacket data = pkt.data;
            RTCP[] rtcps = outsrtcp.inbound(data);
            for (RTCP rtcp : rtcps) {
                //Log.info("RTCP " + rtcp);
                rtcpRecieved(rtcp);
            }
        } catch (Exception ex) {
            Log.warn("problem parsing RTCP?");
        }

    }

    void setCandidateTransport(CandidateTransport c) {
        cdt = c;
    }

    private void updateStats(long ssrc, String rcvd, long val) {

        if (ssrc == this.acsrc) {
            updateStats(aStats, rcvd, val);
        } else if (ssrc == this.vcsrc) {
            updateStats(vStats, rcvd, val);
        } else {
            Log.warn("unexpected ssrc " + ssrc + "should be " + this.acsrc + " or " + this.vcsrc);
        }
    }

    private void updateStats(Hashtable<String, Long> tats, String rcvd, long val) {
        tats.put(rcvd, val);
    }

    public Hashtable<String, Long> newStats(long ssrc) {
        Hashtable<String, Long> ret = new Hashtable();
        ret.put("ssrc", ssrc);
        ret.put("then", System.currentTimeMillis() - 1);
        ret.put("frames", 0L);
        ret.put("packets", 0L);
        ret.put("bytes", 0L);
        return ret;
    }

    public Hashtable<String, Long> getStats(Hashtable<String, Long> old) {
        Hashtable<String, Long> ret = new Hashtable();
        Hashtable<String, Long> curr = null;
        long now = System.currentTimeMillis();
        long then = old.get("then");
        long dT = now - then;
        long oSsrc = old.get("ssrc");
        if (oSsrc == this.acsrc) {
            curr = aStats;
            curr.put("packets", this.outasrtp.getPacketsSent());
            curr.put("bytes", this.outasrtp.getBytesSent());
            curr.put("vol", this.audioSender.getLevel());
        }
        if (oSsrc == this.vcsrc) {
            curr = vStats;
            curr.put("packets", this.outvsrtp.getPacketsSent());
            curr.put("bytes", this.outvsrtp.getBytesSent());
            curr.put("frames", this.outvsrtp.getFramesSent());
            delta("frames", ret, curr, old, dT);
        }
        if (curr != null) {
            delta("packets", ret, curr, old, dT);
            delta("bytes", ret, curr, old, dT);
            copy("bwe", ret, curr);
            copy("frac", ret, curr);
            copy("ssrc", ret, curr);
            copy("vol",ret,curr);
        }
        ret.put("then", now);
        return ret;
    }

    private void delta(String key, Hashtable<String, Long> ret, Hashtable<String, Long> curr, Hashtable<String, Long> old, long dT) {
        long v = curr.get(key);
        ret.put(key, v);
        long ov = old.getOrDefault(key, 0L);
        long dv = v - ov;
        long dvdt = (1000 * dv) / dT;
        ret.put(key + "/s", dvdt);
    }

    private void copy(String key, Hashtable<String, Long> ret, Hashtable<String, Long> curr) {
        Long v = curr.get(key);
        if (v != null) {
            ret.put(key, v);
        }
    }

    class ICESRTP extends SRTPProtocolImpl {

        private long pkts;
        private long bytes;
        private boolean finished;
        private long frames;

        public ICESRTP(int id, int vtype, Properties lcryptoProps, Properties rcryptoProps) {
            super(id, null, null, vtype, lcryptoProps, rcryptoProps);
        }

        public void parseICEPacket(DatagramPacket dp) throws IOException {
            parsePacket(dp);
        }

        @Override
        public void startrecv() {
            Log.verb("not starting a rcv thread on the ICED srtp handler.");
        }

        public void sendPacket(byte[] data, long stamp, char seqno, int ptype, boolean marker) throws SocketException, IOException {
            super.sendPacket(data, stamp, seqno, ptype, marker);
            if (marker) {
                frames++;
            }
        }

        @Override
        public void sendToNetwork(byte[] pay) throws IOException {
            try {
                cdt.send(pay, 0, pay.length);
                bytes += pay.length;
                pkts++;
            } catch (IOException x) {
                finished = true;
                throw x;
            }
        }

        @Override
        public boolean finished() {
            return finished;
        }

        long getBytesSent() {
            return bytes;
        }

        long getPacketsSent() {
            return pkts;
        }

        long getFramesSent() {
            return frames;
        }
    };

    public static Properties[] flipProps(Properties[] cprops) {
        // 1 is the DTLS server's crypto and 0 is the dtls client's
        boolean client = false;
        // so if we are the server, swap them.
        Properties[] svp = {cprops[1], cprops[0]};
        return client ? cprops : svp;
    }

    void start() {
        boolean didstart = true;
        Properties[] flip = flipProps(cprops);
        if (vcsrc != null) {
            outvsrtp = new ICESRTP(id, vtype, flip[0], flip[1]);
            outvsrtp.setSSRC(vcsrc);
            outvsrtp.setRealloc(true);
            vidSender = new V4l2H264("/dev/video0") {
                @Override
                protected void sendRTP(long seqno, byte[] payload, boolean mark, long stamp) {
                    try {
                        Log.verb("forwarding encrypted pkt " + vcsrc + " stamp" + stamp + " seq " + (int) seqno + " size " + payload.length + " mark " + mark);
                        outvsrtp.sendPacket(payload, stamp, (char) seqno, vtype, mark);
                    } catch (IOException ex) {
                        Log.warn("exception " + ex.getMessage());
                    }
                }
            };
            try {
                vidSender.startListeningV();
                Log.info("Sending media...");
                vStats.put("then", System.currentTimeMillis());
            } catch (Exception ex) {
                didstart = false;
                Log.warn("exception " + ex.getMessage());
            }
        }
        if (acsrc != null) {
            outasrtp = new ICESRTP(id + 1, atype, flip[0], flip[1]);
            outasrtp.setSSRC(acsrc);
            outasrtp.setRealloc(true);
            outsrtcp = new SRTCPProtocolImpl(flip[0], flip[1]) {
                @Override
                protected void sendToNetwork(byte[] pay) throws IOException {
                    outasrtp.sendToNetwork(pay); // rtcpmux means that this is the same network tuple.
                }
            };
            try {
                audioSender = new AlsaOpus() {
                    @Override
                    protected void sendRTP(long seqno, byte[] payload, boolean mark, long stamp) {
                        try {
                            Log.verb("forwarding encrypted pkt " + acsrc + " stamp" + stamp + " seq " + (int) seqno + " size " + payload.length + " mark " + mark);
                            outasrtp.sendPacket(payload, stamp, (char) seqno, atype, mark);
                        } catch (IOException ex) {
                            Log.warn("exception " + ex.getMessage());
                        }
                    }
                };
                audioSender.startMedia();
                aStats.put("then", System.currentTimeMillis());
            } catch (Exception ex) {
                didstart = false;
                Log.warn("exception " + ex.getMessage());
            }
        }
        started = didstart;
    }

    void setCrypto(Properties[] props) {
        cprops = props;
    }

    void putBwe(byte[] fci) {
        long bwe = 0;
        int remb = 0x52454d42;
        if (fci.length >= 12) {
            ByteBuffer bb = ByteBuffer.wrap(fci);
            int sig = bb.getInt();
            int val = bb.getInt();
            if (sig == remb) {
                Log.verb("got remb data");
                int mant = val & 0x3ffff;
                int exp = (val & 0xfc0000) >> 18;
                int ssrcn = (val & 0xf0000000) >> 24;
                bwe = mant << exp;
                Log.debug("bwe =" + bwe + " mant =" + mant + " exp=" + exp + " srcn=" + ssrcn);
                updateStats(this.vcsrc, "bwe", bwe);
            } else {
                Log.warn("not remb");
            }
        }
    }

    protected void rtcpRecieved(RTCP rtcp) {
        if (rtcp instanceof RTCP.PSFB) {
            RTCP.PSFB psfb = (RTCP.PSFB) rtcp;
            if (psfb.getFmt() == 1) {
                Log.debug("PSFB fmt 1 ssrc was " + psfb.getMssrc());
            }
            if (psfb.getFmt() == 15) {
                Log.debug("PSFB fmt 15 ssrc was " + psfb.getMssrc());
                putBwe(psfb.getFci());
            }
        }
        if (rtcp instanceof RTCP.RTPFB) {
            RTCP.RTPFB fb = (RTCP.RTPFB) rtcp;
            if (fb.getFmt() == 1) {
                String missed = "";
                List<Long> missing = fb.getSeqList();
                missed = missing.stream().map(m -> " " + m).reduce(missed, String::concat);
                Log.debug("RTPFB fmt 1 (nack) fci was " + missed);
            }
        }
        if (rtcp instanceof RTCP.PSFB) {
            Log.debug("PSFB" + ((RTCP.PSFB) rtcp).toString());
        }
        if (rtcp instanceof RTCP.ReceiverReport) {
            RTCP.ReceiverReport rr = (RTCP.ReceiverReport) rtcp;
            Log.debug("ReceiverReport " + rr.toString());
            RTCP.ReportBlock[] reports = rr.getReports();
            for (RTCP.ReportBlock r : reports) {
                Log.debug("RR " + r.toString());
                updateStats(r.getSsrc(), "rcvd", r.getHighestSeqRcvd());
                updateStats(r.getSsrc(), "frac", r.getFrac());
            }
        }

        if (rtcp instanceof RTCP.BYE) {
            Log.info("RTCP BYE");
            Log.debug(rtcp.toString());
            //stats.put("bye",1L);
        }

    }

    public boolean isRunning() {
        boolean running = started;
        if ((running) && (acsrc != null) && this.outasrtp.finished()) {
            running = false;
        }
        if ((running) && (vcsrc != null) && this.outvsrtp.finished()) {
            running = false;
        }
        return running;
    }

    public RTP(Long vcs, int vty, Long acs, int aty) {
        vtype = vty;
        vcsrc = vcs;
        atype = aty;
        acsrc = acs;
        if (acsrc != null) {
            aStats = new Hashtable();
            aStats.put("ssrc", acsrc);
        }
        if (vcsrc != null) {
            vStats = new Hashtable();
            vStats.put("ssrc", vcsrc);
        }
        id += 2;
    }
}
