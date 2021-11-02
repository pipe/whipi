/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pe.pi.whipi;

import com.ipseorama.slice.ORTC.EventHandler;
import com.ipseorama.slice.ORTC.RTCEventData;
import com.ipseorama.slice.ORTC.RTCIceCandidate;
import com.ipseorama.slice.ORTC.RTCIceCandidatePair;
import com.ipseorama.slice.ORTC.RTCIceGatherOptions;
import com.ipseorama.slice.ORTC.RTCIceGatherer;
import com.ipseorama.slice.ORTC.RTCIceParameters;
import com.ipseorama.slice.ORTC.RTCIceServer;
import com.ipseorama.slice.ORTC.RTCIceTransport;
import com.ipseorama.slice.ORTC.enums.RTCIceCandidateType;
import com.ipseorama.slice.ORTC.enums.RTCIceComponent;
import com.ipseorama.slice.ORTC.enums.RTCIceCredentialType;
import com.ipseorama.slice.ORTC.enums.RTCIceGatherPolicy;
import com.ipseorama.slice.ORTC.enums.RTCIceGathererState;
import com.ipseorama.slice.ORTC.enums.RTCIceProtocol;
import com.ipseorama.slice.ORTC.enums.RTCIceRole;
import com.ipseorama.slice.ORTC.enums.RTCIceTcpCandidateType;
import com.ipseorama.slice.ORTC.enums.RTCIceTransportState;
import com.ipseorama.slice.SingleThreadNioIceEngine;
import com.phono.srtplight.Log;
import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;

/**
 *
 * @author thp
 */
public abstract class ICE {

    RTCIceTransport ice;

    private final String lufrag;
    private final String lupass;
    private final RTCIceTransport transport;
    private final ArrayList<RTCIceCandidate> candidates;
    private final RTCIceGatherer gatherer;
    private final RTCIceRole icerole = RTCIceRole.CONTROLLING;
    private RTCIceParameters answerParams = null;
    SingleThreadNioIceEngine tie;

    public ICE(SecureRandom sr) {

        lufrag = Long.toString(sr.nextLong(), 36).replace("-", "");
        lupass = (new BigInteger(130, sr).toString(32)).replace("-", "");
        candidates = new ArrayList();
        gatherer = new RTCIceGatherer();

        tie = new SingleThreadNioIceEngine();
        tie.addIceCreds(lufrag, lupass);
        RTCIceParameters localParameters = new RTCIceParameters(lufrag, lupass, false);
        gatherer.setLocalParameters(localParameters);
        gatherer.setIceEngine(tie);
        transport = new RTCIceTransport(gatherer, icerole, RTCIceComponent.RTP) {
            @Override
            public RTCIceParameters getRemoteParameters() {
                return (answerParams != null) ? answerParams : super.getRemoteParameters();
            }
        };
        transport.onstatechange = (RTCEventData d) -> {
            Log.info("transport state is now " + d.toString());

            if (transport.getState() == RTCIceTransportState.COMPLETED) {
                RTCIceCandidatePair sel = transport.getSelectedCandidatePair();
                if (sel != null) {
                    onConnected(sel);
                } else {
                    Log.error("Connected ICE but selected pair is null ?!?! ");
                }
            }
        };

        transport.oncandidatepairchange = (RTCEventData d) -> {
            Log.info("selected pair is now " + d);
        };
        gatherer.onlocalcandidate = (RTCEventData d) -> {
            RTCIceCandidate candy = (RTCIceCandidate) d;
            candidates.add(candy);
            Log.debug("local candidate " + d.toString());
        };
        transport.start(gatherer, answerParams, icerole);
    }

    public void gather() {
        RTCIceGatherOptions options = googleStunOptions();

        gatherer.onstatechange = (RTCEventData d) -> {
            Log.debug("gatherer state is now " + d.toString());
            if (gatherer.getState() == RTCIceGathererState.COMPLETE) {
                onGathered();
            }
        };
        gatherer.gather(options);

    }

    public void connect(String rufrag, String rpass, String[] rcandy) {
        if (gatherer.getState() == RTCIceGathererState.COMPLETE) {
            answerParams = new RTCIceParameters(rufrag, rpass, false);
            tie.addIceCreds(rufrag, rpass);
            Log.debug("starting transport as " + icerole);
            for (var r : rcandy) {
                RTCIceCandidate remoteCandidate = mkCandidate(r);
                transport.addRemoteCandidate(remoteCandidate);
            }

        } else {
            Log.debug("not starting transport gather is  " + gatherer.getState());
        }
    }

    public RTCIceTransport getTransport() {
        return transport;
    }

    public RTCIceGatherOptions googleStunOptions() {
        RTCIceGatherOptions options = new RTCIceGatherOptions();
        options.setGatherPolicy(RTCIceGatherPolicy.ALL);
        ArrayList<RTCIceServer> iceServers = new ArrayList< RTCIceServer>();
        ArrayList<URI> u = new ArrayList<URI>();
        try {
            u.add(new URI("stun:stun4.l.google.com:19302"));
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }

        String uname = null;
        String cred = null;
        RTCIceCredentialType credType = null;
        RTCIceServer e = new RTCIceServer(u, uname, cred, credType);
        iceServers.add(e);
        options.setIceServers(iceServers);
        return options;
    }

    ArrayList<RTCIceCandidate> getCandidates() {
        return this.candidates;
    }

    String getLpass() {
        return lupass;
    }

    String getLfrag() {
        return lufrag;
    }

    abstract void onGathered();

    abstract void onConnected(RTCIceCandidatePair selectedCandidatePair);

    private static RTCIceCandidate mkCandidate(String r) {
        String foundation;
        long priority;
        long component;
        String ip;
        RTCIceProtocol protocol;
        char port;
        RTCIceCandidateType type;
        RTCIceTcpCandidateType tcpType;
        // a=candidate:1 1 UDP 2130706431 159.65.75.8 34381 typ host

        String[] params = r.substring(r.indexOf(":") + 1).split(" ");
        foundation = params[0];
        component = Long.parseLong(params[1]);
        protocol = RTCIceProtocol.fromString(params[2].toLowerCase());
        priority = Long.parseLong(params[3]);
        ip = params[4];
        port = (char) Integer.parseInt(params[5]);
        int index = 6;
        RTCIceCandidate ret = null;
        ret = new RTCIceCandidate(foundation,priority,ip,protocol,port
            ,RTCIceCandidateType.HOST,RTCIceTcpCandidateType.ACTIVE);
        try {
            InetAddress nip = InetAddress.getByName(ip);
            if (nip instanceof Inet6Address) {
                ret.setIpVersion(6);
            } else {
                ret.setIpVersion(4);
            }
        } catch (UnknownHostException x) {
            ret.setIpVersion(4); // shrug - best guess.
        }
        while (index + 1 <= params.length) {
            String val = params[index + 1];

            switch (params[index]) {
                case "typ":
                    type = RTCIceCandidateType.fromString(val);
                    ret.setType(type);
                    break;
                case "generation":
                    break;
                case "username":
                    break;
                case "password":
                    break;
                case "raddr":
                    ret.setRelatedAddress(val);
                    break;
                case "rport":
                    char rport = (char) Integer.parseInt(val);
                    ret.setRelatedPort(port);
                    break;
                default:
                    Log.warn("extra candidate info ignored "+params[index]);
            }
            index += 2;
        }
        Log.debug("returning candidate " + ret.toString());
        return ret;

    }

}
