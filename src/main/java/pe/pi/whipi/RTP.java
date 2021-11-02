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

import com.ipseorama.slice.ORTC.RTCEventData;
import com.phono.srtplight.Log;
import com.phono.srtplight.SRTPProtocolImpl;
import java.io.IOException;
import java.net.DatagramPacket;
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
    long vcsrc;
    int vtype = 0;
    int atype = 0;
    private V4l2H264 vidSender;
    private AlsaOpus audioSender;
    private ICESRTP outasrtp;
    private long acsrc;

    void inbound(RTCEventData pkt) {
        // ignore RTCP data for now.
    }

    void setCandidateTransport(CandidateTransport c) {
        cdt = c;
    }

    class ICESRTP extends SRTPProtocolImpl {

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

        @Override
        public void sendToNetwork(byte[] pay) throws IOException {
            cdt.send(pay, 0, pay.length);
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
        Properties[] flip = flipProps(cprops);
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
        outasrtp = new ICESRTP(id + 1, atype, flip[0], flip[1]);
        outasrtp.setSSRC(acsrc);
        outasrtp.setRealloc(true);
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
            vidSender.startListeningV();
            Log.info("Now sending Video");
            audioSender.startMedia();
        } catch (Exception ex) {
            Log.warn("exception " + ex.getMessage());
        }
    }

    void setCrypto(Properties[] props) {
        cprops = props;
    }

    public RTP(long vcs, int vty, long acs, int aty) {
        vtype = vty;
        vcsrc = vcs;
        atype = aty;
        acsrc = acs;
    }
}
