/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.etsi1196x2.signum

import at.asitplus.signum.indispensable.asn1.Asn1BitString
import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1OctetString
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.decodeToBoolean
import at.asitplus.signum.indispensable.asn1.encoding.decodeToInt
import at.asitplus.signum.indispensable.asn1.encoding.parse
import at.asitplus.signum.indispensable.asn1.readOid
import at.asitplus.signum.indispensable.pki.X509Certificate
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.AuthorityInformationAccess
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.BasicConstraintsInfo
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateOperations
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.KeyUsageBits
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.QCStatementInfo
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ValidityPeriod

/**
 * Certificate operations implementation using Signum's cross-platform ASN.1 parsing.
 *
 * This implementation extracts certificate properties using Signum's X509Certificate API,
 * providing consistent behavior across JVM, Android, and iOS platforms.
 *
 * **Implemented operations:**
 * - Self-signed detection (comparing subject and issuer)
 * - Validity period extraction (TODO: needs property name discovery)
 * - Extension parsing (TODO: requires ASN.1 parsing exploration)
 *
 * **Usage:**
 * ```kotlin
 * val operations = SignumCertificateOperations()
 * val isSelfSigned = operations.isSelfSigned(certificate)
 * ```
 *
 * @see CertificateOperations
 */
public class SignumCertificateOperations : CertificateOperations<X509Certificate> {

    /**
     * Extracts basic constraints extension (cA flag and pathLenConstraint).
     *
     * Parses the BasicConstraints extension (OID: 2.5.29.19) from the certificate.
     * ASN.1 structure:
     * ```
     * BasicConstraints ::= SEQUENCE {
     *      cA                      BOOLEAN DEFAULT FALSE,
     *      pathLenConstraint       INTEGER (0..MAX) OPTIONAL
     * }
     * ```
     *
     * @param certificate the certificate to extract from
     * @return basic constraints information, or default values if extension not present
     */
    override fun getBasicConstraints(certificate: X509Certificate): BasicConstraintsInfo {
        // OID for BasicConstraints extension
        val basicConstraintsOid = ObjectIdentifier("2.5.29.19")

        // Find the extension
        val extension = certificate.tbsCertificate.extensions?.firstOrNull { ext ->
            ext.oid == basicConstraintsOid
        } ?: return BasicConstraintsInfo(isCa = false, pathLenConstraint = null)

        return try {
            // The extension value is an OCTET STRING containing the DER-encoded BasicConstraints
            val octetString = extension.value as? Asn1OctetString
                ?: return BasicConstraintsInfo(isCa = false, pathLenConstraint = null)

            // Parse the OCTET STRING content to get the SEQUENCE
            val sequence = Asn1Element.parse(octetString.content).asSequence()

            // Parse the SEQUENCE structure
            var isCa = false
            var pathLenConstraint: Int? = null

            if (sequence.children.isNotEmpty()) {
                val first = sequence.children[0]

                // First element might be BOOLEAN (cA) or INTEGER (pathLenConstraint if cA is default FALSE)
                when (first.tag) {
                    Asn1Element.Tag.BOOL -> {
                        isCa = first.asPrimitive().decodeToBoolean()

                        // If there's a second element, it's the pathLenConstraint
                        if (sequence.children.size > 1) {
                            pathLenConstraint = sequence.children[1].asPrimitive().decodeToInt()
                        }
                    }
                    Asn1Element.Tag.INT -> {
                        // If first element is INTEGER, cA is default FALSE
                        isCa = false
                        pathLenConstraint = first.asPrimitive().decodeToInt()
                    }
                }
            }

            BasicConstraintsInfo(
                isCa = isCa,
                pathLenConstraint = pathLenConstraint,
            )
        } catch (e: Exception) {
            // If parsing fails, return default values
            BasicConstraintsInfo(isCa = false, pathLenConstraint = null)
        }
    }

    /**
     * Extracts QCStatements extension.
     *
     * Parses the QCStatements extension (OID: 1.3.6.1.5.5.7.1.3) from the certificate.
     * ASN.1 structure:
     * ```
     * QCStatements ::= SEQUENCE OF QCStatement
     * QCStatement ::= SEQUENCE {
     *     statementId   OBJECT IDENTIFIER,
     *     statementInfo ANY DEFINED BY statementId OPTIONAL
     * }
     * ```
     *
     * Common QC statement OIDs (ETSI EN 319 412-5):
     * - id-etsi-qcs-QcCompliance: 0.4.0.1862.1.1
     * - id-etsi-qcs-QcType: 0.4.0.1862.1.6
     * - id-etsi-qcs-QcPDS: 0.4.0.1862.1.5
     *
     * @param certificate the certificate to extract from
     * @return list of QC statements
     */
    override fun getQcStatements(certificate: X509Certificate): List<QCStatementInfo> {
        // OID for QCStatements extension
        val qcStatementsOid = ObjectIdentifier("1.3.6.1.5.5.7.1.3")

        // Find the extension
        val extension = certificate.tbsCertificate.extensions?.firstOrNull { ext ->
            ext.oid == qcStatementsOid
        } ?: return emptyList()

        return try {
            // The extension value is an OCTET STRING containing the DER-encoded QCStatements
            val octetString = extension.value as? Asn1OctetString
                ?: return emptyList()

            // Parse the OCTET STRING content to get the SEQUENCE OF QCStatement
            val sequenceOfStatements = Asn1Element.parse(octetString.content).asSequence()

            // Parse each QCStatement
            sequenceOfStatements.children.mapNotNull { statementElement ->
                try {
                    val statement = statementElement.asSequence()
                    if (statement.children.isEmpty()) return@mapNotNull null

                    // First element is the statementId (OID)
                    val statementId = statement.children[0].asPrimitive().readOid()

                    // Known QC compliance OID
                    val qcComplianceOid = ObjectIdentifier("0.4.0.1862.1.1")

                    // Check if this is a QC compliance statement
                    val qcCompliance = (statementId == qcComplianceOid)

                    // Return the statement info
                    QCStatementInfo(
                        qcType = statementId.toString(),
                        qcCompliance = qcCompliance,
                    )
                } catch (e: Exception) {
                    // Skip malformed statements
                    null
                }
            }
        } catch (e: Exception) {
            // If parsing fails, return empty list
            emptyList()
        }
    }

    /**
     * Extracts key usage extension bits.
     *
     * Parses the KeyUsage extension (OID: 2.5.29.15) from the certificate.
     * KeyUsage is encoded as a BIT STRING where each bit represents a specific usage:
     * - Bit 0: digitalSignature
     * - Bit 1: nonRepudiation (contentCommitment)
     * - Bit 2: keyEncipherment
     * - Bit 3: dataEncipherment
     * - Bit 4: keyAgreement
     * - Bit 5: keyCertSign
     * - Bit 6: cRLSign
     * - Bit 7: encipherOnly
     * - Bit 8: decipherOnly
     *
     * @param certificate the certificate to extract from
     * @return key usage bits, or null if extension not present
     */
    override fun getKeyUsage(certificate: X509Certificate): KeyUsageBits? {
        // OID for KeyUsage extension
        val keyUsageOid = ObjectIdentifier("2.5.29.15")

        // Find the extension
        val extension = certificate.tbsCertificate.extensions?.firstOrNull { ext ->
            ext.oid == keyUsageOid
        } ?: return null

        return try {
            // The extension value is an OCTET STRING containing the DER-encoded BIT STRING
            val octetString = extension.value as? Asn1OctetString
                ?: return null

            // Parse the OCTET STRING content to get the BIT STRING
            val bitString = Asn1BitString.decodeFromTlv(
                Asn1Element.parse(octetString.content).asPrimitive(),
            )

            // Extract bit values
            // BIT STRING rawBytes contain the actual bits
            val bytes = bitString.rawBytes

            // Helper function to check if a bit is set
            fun isBitSet(bitIndex: Int): Boolean {
                if (bytes.isEmpty()) return false
                val byteIndex = bitIndex / 8
                if (byteIndex >= bytes.size) return false
                val bitInByte = 7 - (bitIndex % 8) // MSB first
                return ((bytes[byteIndex].toInt() and (1 shl bitInByte)) != 0)
            }

            KeyUsageBits(
                digitalSignature = isBitSet(0),
                nonRepudiation = isBitSet(1),
                keyEncipherment = isBitSet(2),
                dataEncipherment = isBitSet(3),
                keyAgreement = isBitSet(4),
                keyCertSign = isBitSet(5),
                crlSign = isBitSet(6),
                encipherOnly = isBitSet(7),
                decipherOnly = isBitSet(8),
            )
        } catch (e: Exception) {
            // If parsing fails, return null
            null
        }
    }

    /**
     * Extracts validity period (notBefore and notAfter dates).
     *
     * Signum's TbsCertificate has:
     * - `validFrom: Asn1Time` - equivalent to notBefore
     * - `validUntil: Asn1Time` - equivalent to notAfter
     *
     * Asn1Time has an `instant` property for direct access to kotlinx.datetime.Instant.
     *
     * @param certificate the certificate to extract from
     * @return validity period with notBefore and notAfter timestamps
     */
    override fun getValidityPeriod(certificate: X509Certificate): ValidityPeriod {
        val notBefore = certificate.tbsCertificate.validFrom.instant
        val notAfter = certificate.tbsCertificate.validUntil.instant

        return ValidityPeriod(
            notBefore = notBefore,
            notAfter = notAfter,
        )
    }

    /**
     * Extracts certificate policy OIDs.
     *
     * Parses the CertificatePolicies extension (OID: 2.5.29.32) from the certificate.
     * ASN.1 structure:
     * ```
     * CertificatePolicies ::= SEQUENCE SIZE (1..MAX) OF PolicyInformation
     *
     * PolicyInformation ::= SEQUENCE {
     *      policyIdentifier   CertPolicyId,
     *      policyQualifiers   SEQUENCE SIZE (1..MAX) OF PolicyQualifierInfo OPTIONAL
     * }
     *
     * CertPolicyId ::= OBJECT IDENTIFIER
     * ```
     *
     * @param certificate the certificate to extract from
     * @return list of policy OIDs as strings
     */
    override fun getCertificatePolicies(certificate: X509Certificate): List<String> {
        // OID for CertificatePolicies extension
        val certPoliciesOid = ObjectIdentifier("2.5.29.32")

        // Find the extension
        val extension = certificate.tbsCertificate.extensions?.firstOrNull { ext ->
            ext.oid == certPoliciesOid
        } ?: return emptyList()

        return try {
            // The extension value is an OCTET STRING containing the DER-encoded CertificatePolicies
            val octetString = extension.value as? Asn1OctetString
                ?: return emptyList()

            // Parse the OCTET STRING content to get the SEQUENCE OF PolicyInformation
            val sequenceOfPolicies = Asn1Element.parse(octetString.content).asSequence()

            // Parse each PolicyInformation to extract the policyIdentifier OID
            sequenceOfPolicies.children.mapNotNull { policyElement ->
                try {
                    val policyInfo = policyElement.asSequence()
                    if (policyInfo.children.isEmpty()) return@mapNotNull null

                    // First element is the policyIdentifier (OID)
                    val policyOid = policyInfo.children[0].asPrimitive().readOid()

                    // Return the OID as string
                    policyOid.toString()
                } catch (e: Exception) {
                    // Skip malformed policy entries
                    null
                }
            }
        } catch (e: Exception) {
            // If parsing fails, return empty list
            emptyList()
        }
    }

    /**
     * Checks if the certificate is self-signed.
     *
     * A certificate is self-signed if the subject and issuer are the same.
     * This implementation compares the subject and issuer names.
     *
     * @param certificate the certificate to check
     * @return true if self-signed, false otherwise
     */
    override fun isSelfSigned(certificate: X509Certificate): Boolean {
        return try {
            // Compare subject and issuer from TbsCertificate
            val subject = certificate.tbsCertificate.subjectName
            val issuer = certificate.tbsCertificate.issuerName

            // Compare lists directly (RelativeDistinguishedName has proper equals)
            subject == issuer
        } catch (e: Exception) {
            // If we can't access the names, assume not self-signed
            false
        }
    }

    /**
     * Extracts Authority Information Access (AIA) extension.
     *
     * Parses the AIA extension (OID: 1.3.6.1.5.5.7.1.1) from the certificate.
     * ASN.1 structure:
     * ```
     * AuthorityInfoAccessSyntax ::= SEQUENCE SIZE (1..MAX) OF AccessDescription
     *
     * AccessDescription ::= SEQUENCE {
     *     accessMethod          OBJECT IDENTIFIER,
     *     accessLocation        GeneralName
     * }
     * ```
     *
     * Common accessMethod OIDs:
     * - id-ad-caIssuers: 1.3.6.1.5.5.7.48.2
     * - id-ad-ocsp: 1.3.6.1.5.5.7.48.1
     *
     * The accessLocation is typically a uniformResourceIdentifier [6] (URI).
     *
     * @param certificate the certificate to extract from
     * @return AIA extension information with caIssuers and OCSP URIs
     */
    override fun getAiaExtension(certificate: X509Certificate): AuthorityInformationAccess? {
        // OID for AIA extension
        val aiaOid = ObjectIdentifier("1.3.6.1.5.5.7.1.1")

        // Find the extension
        val extension = certificate.tbsCertificate.extensions?.firstOrNull { ext ->
            ext.oid == aiaOid
        } ?: return null

        // Known accessMethod OIDs
        val caIssuersOid = ObjectIdentifier("1.3.6.1.5.5.7.48.2")
        val ocspOid = ObjectIdentifier("1.3.6.1.5.5.7.48.1")

        var caIssuersUri: String? = null
        var ocspUri: String? = null

        try {
            // The extension value is an OCTET STRING containing the DER-encoded AIA
            val octetString = extension.value as? Asn1OctetString
                ?: return null

            // Parse the OCTET STRING content to get the SEQUENCE OF AccessDescription
            val sequenceOfDescriptions = Asn1Element.parse(octetString.content).asSequence()

            // Parse each AccessDescription
            for (descElement in sequenceOfDescriptions.children) {
                try {
                    val desc = descElement.asSequence()
                    if (desc.children.size < 2) continue

                    // First element is accessMethod (OID)
                    val accessMethod = desc.children[0].asPrimitive().readOid()

                    // Second element is accessLocation (GeneralName)
                    // For URI, it's tagged with [6] (uniformResourceIdentifier)
                    val accessLocation = desc.children[1]

                    // Check if it's a URI (context-specific tag 6)
                    if (accessLocation.tag.tagValue == 6uL) {
                        // Decode as string (the URI content)
                        val uri = (accessLocation as? at.asitplus.signum.indispensable.asn1.Asn1Primitive)
                            ?.content
                            ?.decodeToString()
                            ?: continue

                        // Store based on accessMethod
                        when (accessMethod) {
                            caIssuersOid -> caIssuersUri = uri
                            ocspOid -> ocspUri = uri
                        }
                    }
                } catch (e: Exception) {
                    // Skip malformed access descriptions
                    continue
                }
            }

            // Return only if we found at least one URI
            return if (caIssuersUri != null || ocspUri != null) {
                AuthorityInformationAccess(
                    caIssuersUri = caIssuersUri,
                    ocspUri = ocspUri,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            // If parsing fails, return null
            return null
        }
    }
}
