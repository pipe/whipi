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

import com.phono.srtplight.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.util.Hashtable;
import java.util.Vector;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.ClientCertificateType;
import org.bouncycastle.tls.DTLSServerProtocol;
import org.bouncycastle.tls.DTLSTransport;
import org.bouncycastle.tls.DatagramTransport;
import org.bouncycastle.tls.DefaultTlsServer;
import org.bouncycastle.tls.ExtensionType;
import org.bouncycastle.tls.HashAlgorithm;
import org.bouncycastle.tls.ProtocolName;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsContext;
import org.bouncycastle.tls.TlsCredentialedDecryptor;
import org.bouncycastle.tls.TlsCredentialedSigner;
import org.bouncycastle.tls.TlsSRTPUtils;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.UseSRTPData;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import pe.pi.whipi.DTLS;

/**
 *
 * @author tim
 */
public abstract class DTLSServer extends
        DefaultTlsServer implements Runnable{

    private DTLSServerProtocol _serverProtocol;
    private boolean _isShutdown;
    private Thread _acceptor;

    private final String _ffp;
    private boolean _verified = false;
    private Object nap;
    private final DatagramTransport _dt;
    private boolean wantRTP = true; // default magic
    boolean _dtlsStatusOk = true;
    private static int __count = 0;
    private final TlsCertificate _cert;
    private final AsymmetricKeyParameter _key;
    private final BcTlsCrypto _crypto;

    public DTLSServer(    AsymmetricKeyParameter k,
    TlsCertificate c,     BcTlsCrypto crypto,
            DatagramTransport dt, String farFingerprint) throws Exception {
        super(crypto);

        _ffp = farFingerprint;
        _dt = dt;
        _cert = c;
        _key = k;
        _crypto = crypto;
        nap = new Object();
        if (_dt != null) {
            _serverProtocol = new DTLSServerProtocol();
            _acceptor = new Thread(this);
            _acceptor.setName("DTLSServer-"+__count++);
            _acceptor.start();
        } else {
            Log.debug("no socket or destination");
        }

    }

    protected void setWantRTP(boolean want) {
        this.wantRTP = want;
    }

    static String getHex(byte[] in) {
        return getHex(in, in.length);
    }

    static String getHex(byte[] in, int len) {
        char cmap[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        StringBuffer ret = new StringBuffer();
        int top = Math.min(in.length, len);
        for (int i = 0; i < top; i++) {
            ret.append("(byte)0x");
            ret.append(cmap[0x0f & (in[i] >>> 4)]);
            ret.append(cmap[in[i] & 0x0f]);
            ret.append(", ");
            if ((i > 0) && ((i % 8) == 0)) {
                ret.append("\n");
            }
        }
        return ret.toString();
    }

    public abstract void onVerified();
    
    public void run() {
        _dtlsStatusOk = true;
        DTLSTransport dtlsServer = null;
        try {
            dtlsServer = _serverProtocol.accept(this, _dt);
            Log.debug("DTLS accept. verified = " + _verified);
            if (_verified) {
                onVerified();
            } else {
                Log.error("Not the fingerprint we were looking for (waves hand)");
            }
        } catch (Exception e) {
            _dtlsStatusOk = false;
            if (Log.getLevel() >= Log.DEBUG) {
                e.printStackTrace();
            }
            Log.debug("Problem with DTLS server setup  " + e.getMessage());
        }
        synchronized (nap) {
            while (_dtlsStatusOk == true) {
                try {
                    Log.verb(" Napping ");
                    nap.wait(10000);
                } catch (InterruptedException ex) {
                    Log.error("Error Napping " + ex.getMessage());
                }
            }
        }
        if (dtlsServer != null) {
            try {
                dtlsServer.close();
                dtlsServer = null;
            } catch (IOException ex) {
                if (Log.getLevel() >= Log.VERB) {
                    ex.printStackTrace();
                }
                Log.debug("Problem with DTLS server setup  " + ex.getMessage());
            }
        }

        Log.debug("DTLS server run leaving. Status is "+_dtlsStatusOk);
    }

    @Override
    public CertificateRequest getCertificateRequest() throws IOException {
        short[] certificateTypes = new short[]{ClientCertificateType.rsa_sign,
            ClientCertificateType.dss_sign, ClientCertificateType.ecdsa_sign};

        Vector serverSigAlgs = null;
        if (TlsUtils.isSignatureAlgorithmsExtensionAllowed(this.getServerVersion())) {
            serverSigAlgs = TlsUtils.getDefaultSupportedSignatureAlgorithms(context);
        }

        Vector certificateAuthorities = null;
        return new CertificateRequest(certificateTypes, serverSigAlgs, certificateAuthorities);
    }

    @Override
    public Vector<ProtocolName> getProtocolNames() {
        Vector<ProtocolName> ret = new Vector<ProtocolName>(1);
        ret.add(ProtocolName.WEBRTC);
        return ret;
    }

    @Override
    public ProtocolVersion[] getSupportedVersions() {
        ProtocolVersion[] ret = {ProtocolVersion.DTLSv12};
        return ret;
    }

    protected TlsCredentialedSigner getECDSASignerCredentials() {
        Log.verb("getECDSASignerCredentials");

        return getXSignerCredentials(ClientCertificateType.ecdsa_sign);
    }

    protected TlsCredentialedSigner getRSASignerCredentials() {
        Log.verb("getRSASignerCredentials");
        return getXSignerCredentials(ClientCertificateType.rsa_sign);
    }

    protected TlsCredentialedSigner getXSignerCredentials(short sign) {
        AsymmetricKeyParameter privateKey = _key;
        Log.verb("getSignerCredentials");

        TlsCrypto crypto = context.getCrypto();
        TlsCertificate[] tcs = new TlsCertificate[1];
        tcs[0] = _cert;
        Certificate certif = new Certificate(tcs);
        TlsCryptoParameters cryptoParams = new TlsCryptoParameters(context);
        Vector salho = TlsUtils.getDefaultSupportedSignatureAlgorithms(context);
        SignatureAndHashAlgorithm snap = (SignatureAndHashAlgorithm) (salho.stream().filter((s) -> {
            SignatureAndHashAlgorithm sha = (SignatureAndHashAlgorithm) s;
            Log.verb("sha = " + sha.getSignature() + " " + sha.getHash());
            return (sha.getSignature() == sign && sha.getHash() == HashAlgorithm.sha256);
        }).findFirst().orElse(salho.firstElement()));
        return new BcDefaultTlsCredentialedSigner(cryptoParams, (BcTlsCrypto) crypto, privateKey, certif, snap);

    }

    @Override
    protected TlsCredentialedDecryptor getRSAEncryptionCredentials() {
        AsymmetricKeyParameter privateKey = _key;
        TlsCertificate[] tcs = new TlsCertificate[1];
        tcs[0] = _cert;
        Certificate certif = new Certificate(tcs);
        BcDefaultTlsCredentialedDecryptor ret = new BcDefaultTlsCredentialedDecryptor(_crypto, certif, privateKey);
        Log.verb("returning creds " + ret.toString());
        return ret;
    }

    @Override
    public Hashtable getServerExtensions() throws IOException {
        // see https://tools.ietf.org/html/rfc5764 
        byte[] SRTP_AES128_CM_HMAC_SHA1_80 = {0x00, 0x01};
        Hashtable ret = super.getServerExtensions();

        byte[] prof = new byte[5];

        ByteBuffer profileB = ByteBuffer.wrap(prof);
        profileB.putChar((char) 2); // length;
        profileB.put(SRTP_AES128_CM_HMAC_SHA1_80);
        profileB.put((byte) 0);// mkti
        if (wantRTP) {
            if ((this.clientExtensions != null) && this.clientExtensions.containsKey(ExtensionType.use_srtp)) {
                if (ret == null) {
                    ret = new Hashtable();
                }
                ret.put(ExtensionType.use_srtp, prof);
            }
        }
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyClientCertificate(Certificate clientCertificate)
            throws IOException {
        Log.verb("getting client cert ");
        TlsCertificate[] cs = clientCertificate.getCertificateList();
        if ((cs == null) || (cs.length < 1)) {
            throw new IOException("no certs offered");
        }
        Log.debug("certs sent ="+cs.length);
        Log.debug("cert is of type " + cs[0].getClass().getName());
        try {
            if (DTLS.validate(cs[0])) {
                String ffp = DTLS.getPrint(cs[0], true);
                if (!ffp.equalsIgnoreCase(_ffp)) {
                    Log.warn("These aren't the same\n+\t" + ffp + "\n-\t" + _ffp);
                    throw new IOException("fingerprints don't match ");
                }
                _verified = true;
            } else {
               throw new IOException("offered cert is empty ");
            }
        } catch (CertificateException ex) {
            Log.warn("rejecting cert because "+ex.getMessage());
            throw new IOException("offered cert is invalid :" + ex.getMessage());
        }

    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that the DTLS extended client hello contains the
     * <tt>use_srtp</tt> extension.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void processClientExtensions(Hashtable clientExtensions)
            throws IOException {
        UseSRTPData d
                = TlsSRTPUtils.getUseSRTPExtension(clientExtensions);

        if (d == null) {
            Log.debug("Browser didn't send a use_srtp in the client hello");
        }
       
        super.processClientExtensions(clientExtensions);
    }


    public TlsContext getContext() {
        return context;
    }


    public void stop() {
        this._dtlsStatusOk = false;
        Log.debug("DTLS server stop()");
    }
}
