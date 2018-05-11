/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.ssl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Locale;
import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.util.HexDumpEncoder;

enum SSLExtension implements SSLStringize {
    // Extensions defined in RFC 3546
    CH_SERVER_NAME          (0x0000,  "server_name",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_13,
                                ServerNameExtension.chNetworkProducer,
                                ServerNameExtension.chOnLoadConcumer,
                                null,
                                null,
                                ServerNameExtension.chStringize),
    SH_SERVER_NAME          (0x0000, "server_name",
                                SSLHandshake.SERVER_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_12,
                                ServerNameExtension.shNetworkProducer,
                                ServerNameExtension.shOnLoadConcumer,
                                null,
                                null,
                                ServerNameExtension.shStringize),
    EE_SERVER_NAME          (0x0000, "server_name",
                                SSLHandshake.ENCRYPTED_EXTENSIONS,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                ServerNameExtension.eeNetworkProducer,
                                ServerNameExtension.eeOnLoadConcumer,
                                null,
                                null,
                                ServerNameExtension.shStringize),
    CH_MAX_FRAGMENT_LENGTH (0x0001, "max_fragment_length",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_13,
                                MaxFragExtension.chNetworkProducer,
                                MaxFragExtension.chOnLoadConcumer,
                                null,
                                null,
                                MaxFragExtension.maxFragLenStringize),
    SH_MAX_FRAGMENT_LENGTH (0x0001, "max_fragment_length",
                                SSLHandshake.SERVER_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_12,
                                MaxFragExtension.shNetworkProducer,
                                MaxFragExtension.shOnLoadConcumer,
                                null,
                                MaxFragExtension.shOnTradeConsumer,
                                MaxFragExtension.maxFragLenStringize),
    EE_MAX_FRAGMENT_LENGTH (0x0001, "max_fragment_length",
                                SSLHandshake.ENCRYPTED_EXTENSIONS,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                MaxFragExtension.eeNetworkProducer,
                                MaxFragExtension.eeOnLoadConcumer,
                                null,
                                MaxFragExtension.eeOnTradeConsumer,
                                MaxFragExtension.maxFragLenStringize),
    CLIENT_CERTIFICATE_URL  (0x0002, "client_certificate_url"),
    TRUSTED_CA_KEYS         (0x0003, "trusted_ca_keys"),
    TRUNCATED_HMAC          (0x0004, "truncated_hmac"),

    CH_STATUS_REQUEST       (0x0005, "status_request",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_13,
                                CertStatusExtension.chNetworkProducer,
                                CertStatusExtension.chOnLoadConsumer,
                                null,
                                null,
                                CertStatusExtension.certStatusReqStringize),
    SH_STATUS_REQUEST       (0x0005, "status_request",
                                SSLHandshake.SERVER_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_12,
                                CertStatusExtension.shNetworkProducer,
                                CertStatusExtension.shOnLoadConsumer,
                                null,
                                null,
                                CertStatusExtension.certStatusReqStringize),

    CR_STATUS_REQUEST       (0x0005, "status_request"),
    CT_STATUS_REQUEST       (0x0005, "status_request",
                                SSLHandshake.CERTIFICATE,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                CertStatusExtension.ctNetworkProducer,
                                CertStatusExtension.ctOnLoadConsumer,
                                null,
                                null,
                                CertStatusExtension.certStatusRespStringize),
    // extensions defined in RFC 4681
    USER_MAPPING            (0x0006, "user_mapping"),

    // extensions defined in RFC 5878
    CLIENT_AUTHZ            (0x0007, "client_authz"),
    SERVER_AUTHZ            (0x0008, "server_authz"),

    // extensions defined in RFC 5081
    CERT_TYPE               (0x0009, "cert_type"),

    // extensions defined in RFC 4492 (ECC)
    CH_SUPPORTED_GROUPS     (0x000A, "supported_groups",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_13,
                                SupportedGroupsExtension.chNetworkProducer,
                                SupportedGroupsExtension.chOnLoadConcumer,
                                null,
                                null,
                                SupportedGroupsExtension.sgsStringize),
    EE_SUPPORTED_GROUPS     (0x000A, "supported_groups",
                                SSLHandshake.ENCRYPTED_EXTENSIONS,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                SupportedGroupsExtension.eeNetworkProducer,
                                SupportedGroupsExtension.eeOnLoadConcumer,
                                null,
                                null,
                                SupportedGroupsExtension.sgsStringize),

    CH_EC_POINT_FORMATS     (0x000B, "ec_point_formats",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_12,
                                ECPointFormatsExtension.chNetworkProducer,
                                ECPointFormatsExtension.chOnLoadConcumer,
                                null,
                                null,
                                ECPointFormatsExtension.epfStringize),
    SH_EC_POINT_FORMATS     (0x000B, "ec_point_formats",
                                SSLHandshake.SERVER_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_12,
                                null,   // not use of the producer
                                ECPointFormatsExtension.shOnLoadConcumer,
                                null,
                                null,
                                ECPointFormatsExtension.epfStringize),

    // extensions defined in RFC 5054
    SRP                     (0x000C, "srp"),

    // extensions defined in RFC 5246
    CH_SIGNATURE_ALGORITHMS (0x000D, "signature_algorithms",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_12_13,
                                SignatureAlgorithmsExtension.chNetworkProducer,
                                SignatureAlgorithmsExtension.chOnLoadConcumer,
                                SignatureAlgorithmsExtension.chOnLoadAbsence,
                                SignatureAlgorithmsExtension.chOnTradeConsumer,
                                SignatureAlgorithmsExtension.ssStringize),
    CR_SIGNATURE_ALGORITHMS (0x000D, "signature_algorithms",
                                SSLHandshake.CERTIFICATE_REQUEST,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                SignatureAlgorithmsExtension.crNetworkProducer,
                                SignatureAlgorithmsExtension.crOnLoadConcumer,
                                SignatureAlgorithmsExtension.crOnLoadAbsence,
                                SignatureAlgorithmsExtension.crOnTradeConsumer,
                                SignatureAlgorithmsExtension.ssStringize),

    CH_SIGNATURE_ALGORITHMS_CERT (0x0032, "signature_algorithms_cert",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_12_13,
                                CertSignAlgsExtension.chNetworkProducer,
                                CertSignAlgsExtension.chOnLoadConcumer,
                                null,
                                CertSignAlgsExtension.chOnTradeConsumer,
                                CertSignAlgsExtension.ssStringize),
    CR_SIGNATURE_ALGORITHMS_CERT (0x0032, "signature_algorithms_cert",
                                SSLHandshake.CERTIFICATE_REQUEST,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                CertSignAlgsExtension.crNetworkProducer,
                                CertSignAlgsExtension.crOnLoadConcumer,
                                null,
                                CertSignAlgsExtension.crOnTradeConsumer,
                                CertSignAlgsExtension.ssStringize),

    // extensions defined in RFC 5764
    USE_SRTP                (0x000E, "use_srtp"),

    // extensions defined in RFC 6520
    HEARTBEAT               (0x000E, "heartbeat"),

    // extension defined in RFC 7301 (ALPN)
    CH_ALPN                 (0x0010, "application_layer_protocol_negotiation",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_13,
                                AlpnExtension.chNetworkProducer,
                                AlpnExtension.chOnLoadConcumer,
                                AlpnExtension.chOnLoadAbsence,
                                null,
                                AlpnExtension.alpnStringize),
    SH_ALPN                 (0x0010, "application_layer_protocol_negotiation",
                                SSLHandshake.SERVER_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_12,
                                AlpnExtension.shNetworkProducer,
                                AlpnExtension.shOnLoadConcumer,
                                AlpnExtension.shOnLoadAbsence,
                                null,
                                AlpnExtension.alpnStringize),
    EE_ALPN                 (0x0010, "application_layer_protocol_negotiation",
                                SSLHandshake.ENCRYPTED_EXTENSIONS,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                AlpnExtension.shNetworkProducer,
                                AlpnExtension.shOnLoadConcumer,
                                AlpnExtension.shOnLoadAbsence,
                                null,
                                AlpnExtension.alpnStringize),

    // extensions defined in RFC 6961
    CH_STATUS_REQUEST_V2    (0x0011, "status_request_v2",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_12,
                                CertStatusExtension.chV2NetworkProducer,
                                CertStatusExtension.chV2OnLoadConsumer,
                                null,
                                null,
                                CertStatusExtension.certStatusReqV2Stringize),
    SH_STATUS_REQUEST_V2    (0x0011, "status_request_v2",
                                SSLHandshake.SERVER_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_12,
                                CertStatusExtension.shV2NetworkProducer,
                                CertStatusExtension.shV2OnLoadConsumer,
                                null,
                                null,
                                CertStatusExtension.certStatusReqV2Stringize),

    // extensions defined in RFC 6962
    SIGNED_CERT_TIMESTAMP   (0x0012, "signed_certificate_timestamp"),

    // extensions defined in RFC 7250
    CLIENT_CERT_TYPE        (0x0013, "padding"),
    SERVER_CERT_TYPE        (0x0014, "server_certificate_type"),

    // extensions defined in RFC 7685
    PADDING                 (0x0015, "client_certificate_type"),

    // extensions defined in RFC 7366
    ENCRYPT_THEN_MAC        (0x0016, "encrypt_then_mac"),

    // extensions defined in RFC 7627
    CH_EXTENDED_MASTER_SECRET  (0x0017, "extended_master_secret",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_10_12,
                                ExtendedMasterSecretExtension.chNetworkProducer,
                                ExtendedMasterSecretExtension.chOnLoadConcumer,
                                ExtendedMasterSecretExtension.chOnLoadAbsence,
                                null,
                                ExtendedMasterSecretExtension.emsStringize),
    SH_EXTENDED_MASTER_SECRET  (0x0017, "extended_master_secret",
                                SSLHandshake.SERVER_HELLO,
                                ProtocolVersion.PROTOCOLS_10_12,
                                ExtendedMasterSecretExtension.shNetworkProducer,
                                ExtendedMasterSecretExtension.shOnLoadConcumer,
                                ExtendedMasterSecretExtension.shOnLoadAbsence,
                                null,
                                ExtendedMasterSecretExtension.emsStringize),

    // extensions defined in RFC draft-ietf-tokbind-negotiation
    TOKEN_BINDING           (0x0018, "token_binding "),

    // extensions defined in RFC 7924
    CACHED_INFO             (0x0019, "cached_info"),

    // extensions defined in RFC 4507/5077
    SESSION_TICKET          (0x0023, "session_ticket"),

    // extensions defined in TLS 1.3
    CH_EARLY_DATA           (0x002A, "early_data"),
    EE_EARLY_DATA           (0x002A, "early_data"),
    NST_EARLY_DATA          (0x002A, "early_data"),

    CH_SUPPORTED_VERSIONS   (0x002B, "supported_versions",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                SupportedVersionsExtension.chNetworkProducer,
                                SupportedVersionsExtension.chOnLoadConcumer,
                                null,
                                null,
                                SupportedVersionsExtension.chStringize),
    SH_SUPPORTED_VERSIONS   (0x002B, "supported_versions",
                                SSLHandshake.SERVER_HELLO,
                                        // and HelloRetryRequest
                                ProtocolVersion.PROTOCOLS_OF_13,
                                SupportedVersionsExtension.shNetworkProducer,
                                SupportedVersionsExtension.shOnLoadConcumer,
                                null,
                                null,
                                SupportedVersionsExtension.shStringize),
    HRR_SUPPORTED_VERSIONS  (0x002B, "supported_versions",
                                SSLHandshake.HELLO_RETRY_REQUEST,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                SupportedVersionsExtension.hrrNetworkProducer,
                                SupportedVersionsExtension.hrrOnLoadConcumer,
                                null,
                                null,
                                SupportedVersionsExtension.hrrStringize),
    MH_SUPPORTED_VERSIONS   (0x002B, "supported_versions",
                                SSLHandshake.MESSAGE_HASH,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                SupportedVersionsExtension.hrrReproducer,
                                null, null, null,
                                SupportedVersionsExtension.hrrStringize),

    CH_COOKIE               (0x002C, "cookie",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                CookieExtension.chNetworkProducer,
                                CookieExtension.chOnLoadConcumer,
                                null,
                                CookieExtension.chOnTradeConsumer,
                                CookieExtension.cookieStringize),
    HRR_COOKIE              (0x002C, "cookie",
                                SSLHandshake.HELLO_RETRY_REQUEST,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                CookieExtension.hrrNetworkProducer,
                                CookieExtension.hrrOnLoadConcumer,
                                null, null,
                                CookieExtension.cookieStringize),
    MH_COOKIE               (0x002C, "cookie",
                                SSLHandshake.MESSAGE_HASH,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                CookieExtension.hrrNetworkReproducer,
                                null, null, null,
                                CookieExtension.cookieStringize),

    PSK_KEY_EXCHANGE_MODES  (0x002D, "psk_key_exchange_modes",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                PskKeyExchangeModesExtension.chNetworkProducer,
                                PskKeyExchangeModesExtension.chOnLoadConsumer,
                                null, null, null),
    CERTIFICATE_AUTHORITIES (0x002F, "certificate_authorities"),
    OID_FILTERS             (0x0030, "oid_filters"),
    POST_HANDSHAKE_AUTH     (0x0030, "post_handshake_auth"),

    CH_KEY_SHARE            (0x0033, "key_share",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                KeyShareExtension.chNetworkProducer,
                                KeyShareExtension.chOnLoadConcumer,
                                null, null,
                                KeyShareExtension.chStringize),
    SH_KEY_SHARE            (0x0033, "key_share",
                                SSLHandshake.SERVER_HELLO,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                KeyShareExtension.shNetworkProducer,
                                KeyShareExtension.shOnLoadConcumer,
                                KeyShareExtension.shOnLoadAbsence,
                                null,
                                KeyShareExtension.shStringize),
    HRR_KEY_SHARE           (0x0033, "key_share",
                                SSLHandshake.HELLO_RETRY_REQUEST,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                KeyShareExtension.hrrNetworkProducer,
                                KeyShareExtension.hrrOnLoadConcumer,
                                null, null,
                                KeyShareExtension.hrrStringize),
    MH_KEY_SHARE            (0x0033, "key_share",
                                SSLHandshake.MESSAGE_HASH,
                                ProtocolVersion.PROTOCOLS_OF_13,
                                KeyShareExtension.hrrNetworkReproducer,
                                null, null, null,
                                KeyShareExtension.hrrStringize),

    // Extensions defined in RFC 5746
    CH_RENEGOTIATION_INFO   (0xff01, "renegotiation_info",
                                SSLHandshake.CLIENT_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_12,
                                RenegoInfoExtension.chNetworkProducer,
                                RenegoInfoExtension.chOnLoadConcumer,
                                RenegoInfoExtension.chOnLoadAbsence,
                                null,
                                RenegoInfoExtension.rniStringize),
    SH_RENEGOTIATION_INFO   (0xff01, "renegotiation_info",
                                SSLHandshake.SERVER_HELLO,
                                ProtocolVersion.PROTOCOLS_TO_12,
                                RenegoInfoExtension.shNetworkProducer,
                                RenegoInfoExtension.shOnLoadConcumer,
                                RenegoInfoExtension.shOnLoadAbsence,
                                null,
                                RenegoInfoExtension.rniStringize),

    // TLS 1.3 PSK extension must be last
    CH_PRE_SHARED_KEY       (0x0029, "pre_shared_key",
                            SSLHandshake.CLIENT_HELLO,
                            ProtocolVersion.PROTOCOLS_OF_13,
                            PreSharedKeyExtension.chNetworkProducer,
                            PreSharedKeyExtension.chOnLoadConsumer,
                            PreSharedKeyExtension.chOnLoadAbsence,
                            PreSharedKeyExtension.chOnTradeConsumer,
                            null),
    SH_PRE_SHARED_KEY       (0x0029, "pre_shared_key",
                            SSLHandshake.SERVER_HELLO,
                            ProtocolVersion.PROTOCOLS_OF_13,
                            PreSharedKeyExtension.shNetworkProducer,
                            PreSharedKeyExtension.shOnLoadConsumer,
                            PreSharedKeyExtension.shOnLoadAbsence,
                            null, null);

    final int id;
    final SSLHandshake handshakeType;
    final String name;
    final ProtocolVersion[] supportedProtocols;
    final HandshakeProducer networkProducer;
    final ExtensionConsumer onLoadConcumer;
    final HandshakeAbsence  onLoadAbsence;
    final HandshakeConsumer onTradeConsumer;
    final SSLStringize stringize;

    // known but unsupported extension
    private SSLExtension(int id, String name) {
        this.id = id;
        this.handshakeType = SSLHandshake.NOT_APPLICABLE;
        this.name = name;
        this.supportedProtocols = new ProtocolVersion[0];
        this.networkProducer = null;
        this.onLoadConcumer = null;
        this.onLoadAbsence = null;
        this.onTradeConsumer = null;
        this.stringize = null;
    }

    // supported extension
    private SSLExtension(int id, String name, SSLHandshake handshakeType,
            ProtocolVersion[] supportedProtocols,
            HandshakeProducer producer,
            ExtensionConsumer onLoadConcumer, HandshakeAbsence onLoadAbsence,
            HandshakeConsumer onTradeConsumer, SSLStringize stringize) {

        this.id = id;
        this.handshakeType = handshakeType;
        this.name = name;
        this.supportedProtocols = supportedProtocols;
        this.networkProducer = producer;
        this.onLoadConcumer = onLoadConcumer;
        this.onLoadAbsence = onLoadAbsence;
        this.onTradeConsumer = onTradeConsumer;
        this.stringize = stringize;
    }

    static SSLExtension valueOf(SSLHandshake handshakeType, int extensionType) {
        for (SSLExtension ext : SSLExtension.values()) {
            if (ext.id == extensionType &&
                    ext.handshakeType == handshakeType) {
                return ext;
            }
        }

        return null;
    }

    static boolean isConsumable(int extensionType) {
        for (SSLExtension ext : SSLExtension.values()) {
            if (ext.id == extensionType &&
                    ext.onLoadConcumer != null) {
                return true;
            }
        }

        return false;
    }

    public byte[] produce(ConnectionContext context,
            HandshakeMessage message) throws IOException {
        if (networkProducer != null) {
            return networkProducer.produce(context, message);
        } else {
            throw new UnsupportedOperationException(
                    "Not yet supported extension producing.");
        }
    }

    public void consumeOnLoad(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {
        if (onLoadConcumer != null) {
            onLoadConcumer.consume(context, message, buffer);
        } else {
            throw new UnsupportedOperationException(
                    "Not yet supported extension loading.");
        }
    }

    public void consumeOnTrade(ConnectionContext context,
            HandshakeMessage message) throws IOException {
        if (onTradeConsumer != null) {
            onTradeConsumer.consume(context, message);
        } else {
            throw new UnsupportedOperationException(
                    "Not yet supported extension processing.");
        }
    }

    void absent(ConnectionContext context,
            HandshakeMessage message) throws IOException {
        if (onLoadAbsence != null) {
            onLoadAbsence.absent(context, message);
        } else {
            throw new UnsupportedOperationException(
                    "Not yet supported extension absence processing.");
        }
    }

    public boolean isAvailable(ProtocolVersion protocolVersion) {
        /*
        for (ProtocolVersion pv : supportedProtocols) {
            if (pv == protocolVersion) {
                return true;
            }
        }
        */
        for (int i = 0; i < supportedProtocols.length; i++) {
            if (supportedProtocols[i] == protocolVersion) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public String toString(ByteBuffer byteBuffer) {
        MessageFormat messageFormat = new MessageFormat(
            "\"{0} ({1})\": '{'\n" +
            "{2}\n" +
            "'}'",
            Locale.ENGLISH);

        String extData;
        if (stringize == null) {
            HexDumpEncoder hexEncoder = new HexDumpEncoder();
            String encoded = hexEncoder.encode(byteBuffer.duplicate());
            extData = encoded;
        } else {
            extData = stringize.toString(byteBuffer);
        }

        Object[] messageFields = {
            this.name,
            this.id,
            Utilities.indent(extData)
        };

        return messageFormat.format(messageFields);
    }

    //////////////////////////////////////////////////////
    // Nested extension, consumer and producer interfaces.

    static interface ExtensionConsumer {
        void consume(ConnectionContext context,
                HandshakeMessage message, ByteBuffer buffer) throws IOException;
    }

    /**
     * A (transparent) specification of extension data.
     *
     * This interface contains no methods or constants. Its only purpose is to
     * group all extension data.  All extension data should implement this
     * interface if the data is expected to handle in the following handshake
     * processes.
     */
    static interface SSLExtensionSpec {
        // blank
    }

    // Default enabled client extensions.
    static final class ClientExtensions {
        static final Collection<SSLExtension> defaults;

        static {
            Collection<SSLExtension> extensions = new LinkedList<>();
            for (SSLExtension extension : SSLExtension.values()) {
                if (extension.handshakeType != SSLHandshake.NOT_APPLICABLE) {
                    extensions.add(extension);
                }
            }

            // Switch off SNI extention?
            boolean enableExtension =
                Utilities.getBooleanProperty("jsse.enableSNIExtension", true);
            if (!enableExtension) {
                extensions.remove(CH_SERVER_NAME);
            }

            // To switch off the max_fragment_length extension.
            enableExtension =
                Utilities.getBooleanProperty("jsse.enableMFLExtension", false);
            if (!enableExtension) {
                extensions.remove(CH_MAX_FRAGMENT_LENGTH);
            }

//            enableExtension = Utilities.getBooleanProperty(
//                    "jdk.tls.client.enableStatusRequestExtension", true);
//            if (!enableExtension) {
//                extensions.remove(CH_STATUS_REQUEST);
//                extensions.remove(CR_STATUS_REQUEST);
//                extensions.remove(CT_STATUS_REQUEST);
//
//                extensions.remove(CH_STATUS_REQUEST_V2);
//            }

            if (!SSLConfiguration.useExtendedMasterSecret) {
                extensions.remove(CH_EXTENDED_MASTER_SECRET);
            }

            defaults = Collections.unmodifiableCollection(extensions);
        }
    }

    // Default enabled server extensions.
    static final class ServerExtensions {
        static final Collection<SSLExtension> defaults;

        static {
            Collection<SSLExtension> extensions = new LinkedList<>();
            for (SSLExtension extension : SSLExtension.values()) {
                if (extension.handshakeType != SSLHandshake.NOT_APPLICABLE) {
                    extensions.add(extension);
                }
            }

/*
            // Switch off SNI extention?
            boolean enableExtension =
                Utilities.getBooleanProperty("jsse.enableSNIExtension", true);
            if (!enableExtension) {
                extensions.remove(CH_SERVER_NAME);
                extensions.remove(SH_SERVER_NAME);
                extensions.remove(EE_SERVER_NAME);
            }

            // To switch off the max_fragment_length extension.
            enableExtension =
                Utilities.getBooleanProperty("jsse.enableMFLExtension", false);
            if (!enableExtension) {
                extensions.remove(CH_MAX_FRAGMENT_LENGTH);
                extensions.remove(SH_MAX_FRAGMENT_LENGTH);
                extensions.remove(EE_MAX_FRAGMENT_LENGTH);
            }
*/

//            enableExtension = Utilities.getBooleanProperty(
//                    "jdk.tls.server.enableStatusRequestExtension", true);
//            if (!enableExtension) {
//                extensions.remove(CH_STATUS_REQUEST);
//                extensions.remove(SH_STATUS_REQUEST);
//                extensions.remove(CR_STATUS_REQUEST);
//                extensions.remove(CT_STATUS_REQUEST);
//
//                extensions.remove(SH_STATUS_REQUEST_V2);
//            }

/*
            if (!SSLConfiguration.useExtendedMasterSecret) {
                extensions.remove(CH_EXTENDED_MASTER_SECRET);
                extensions.remove(SH_EXTENDED_MASTER_SECRET);
            }
*/
            defaults = Collections.unmodifiableCollection(extensions);
        }
    }
}
