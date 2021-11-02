/*
 * Copyright 2017 pi.pe gmbh .
 *
 */
package pe.pi.whipi.util;

import com.ipseorama.slice.ORTC.RTCIceTransport;
import com.phono.srtplight.Log;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.DatagramTransport;
import org.bouncycastle.tls.TlsFatalAlert;




/**
 * a class that queues DTLS packets
 */
public class CandidateTransport implements DatagramTransport {
    protected final static int MIN_IP_OVERHEAD = 20;
    protected final static int MAX_IP_OVERHEAD = MIN_IP_OVERHEAD + 64;
    protected final static int UDP_OVERHEAD = 8;
    private boolean _isShutdown = false;
    BlockingQueue<byte[]> _packetQueue = new ArrayBlockingQueue<>(100);
    private final RTCIceTransport transport;
    protected final int receiveLimit, sendLimit;

    public CandidateTransport(RTCIceTransport transport) {
        this.transport = transport;
        int mtu = transport.getMTU();
        this.receiveLimit = mtu - MIN_IP_OVERHEAD - UDP_OVERHEAD;
        this.sendLimit = mtu - MAX_IP_OVERHEAD - UDP_OVERHEAD;
        Log.debug("set limits based on mtu of "+mtu);
    }

    public void doShutdown() {
        _isShutdown = true;
    }

    public int getReceiveLimit()
    {
        return receiveLimit;
    }

    public int getSendLimit()
    {
        // TODO[DTLS] Implement Path-MTU discovery?
        return sendLimit;
    }

    public void enqueue(byte[] pkt) {
        boolean res = _packetQueue.offer(pkt);
        Log.debug("added packet to DTLS Queue "+res);
    }

    public int receive(byte[] buf, int off, int len, int waitMillis) throws IOException {
        int ret = 0;
        if (!_isShutdown || (_packetQueue.peek() != null)) {
            try {
                Log.debug("recv " + waitMillis);
                byte[] pkt = _packetQueue.poll(waitMillis, TimeUnit.MILLISECONDS);
                if (pkt != null) {
                    ret = Math.min(len, pkt.length);
                    Log.debug("Read DTLS packet "+ret+" bytes queue is "+_packetQueue.size());
                    System.arraycopy(pkt, 0, buf, off, ret);
                }
            } catch (InterruptedException ex) {
                Log.debug("recv interrupted ");
                ex.printStackTrace(); // remove this wart.
                throw new SocketTimeoutException(ex.getMessage());
            }
        } else {
            Log.debug("Transport  shutdown - throw exception.");
            throw new java.io.EOFException("Transport was shutdown.");
        }
        return ret;
    }

    @Override
    public void send(byte[] buf, int off, int len) throws IOException {
        if (len > getSendLimit()){
            /*
             * RFC 4347 4.1.1. "If the application attempts to send a record larger than the MTU,
             * the DTLS implementation SHOULD generate an error, thus avoiding sending a packet
             * which will be fragmented."
             */
            Log.debug("packet too big to send "+len+" > "+sendLimit);
            throw new TlsFatalAlert(AlertDescription.internal_error);
        }
        if (_isShutdown) {
            Log.debug("Transport  shutdown - throw exception.");
            throw new java.io.EOFException("transport shut.");
        }
        transport.sendDtlsPkt(buf, off, len);
        /*
        DatagramPacket p = new DatagramPacket(buf, off, len, _dest);
        _ds.send(p);
        Log.debug("sent " + p.getLength() + " to " + _dest.toString());
         */
    }

    @Override
    public void close() throws IOException {
        if (_isShutdown) {
            Log.debug("Transport  already shutdown - throw exception.");
            throw new java.io.EOFException("transport shut.");
        }
        Log.debug("Transport  shutdown.");
        if (Log.getLevel() >= Log.VERB) {
            Exception why = new Exception("ShutdownCause");
            why.printStackTrace();
        }
        _isShutdown = true;
        // todo - propergate transport shutdown....
    }

    public RTCIceTransport getTransport() {
        return transport;
    }
    
}
