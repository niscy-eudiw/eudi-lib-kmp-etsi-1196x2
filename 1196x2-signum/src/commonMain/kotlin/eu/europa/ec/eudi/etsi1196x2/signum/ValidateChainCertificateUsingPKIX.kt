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

import at.asitplus.signum.indispensable.X509SignatureAlgorithm
import at.asitplus.signum.indispensable.isSupported
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.requireSupported
import at.asitplus.signum.supreme.sign.verifierFor
import at.asitplus.signum.supreme.sign.verify
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificationChainValidation
import eu.europa.ec.eudi.etsi1196x2.consultation.NonEmptyList
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIX
import kotlin.time.Clock

/**
 * PKIX-based certificate chain validation using Signum library.
 *
 * This implementation uses Signum's cross-platform X509Certificate and signature verification
 * to perform certificate chain validation against trust anchors.
 *
 * **Validation steps:**
 * 1. Verify certificate chain continuity (each cert signs the next)
 * 2. Verify root is signed by a trust anchor
 * 3. Check validity periods for all certificates
 * 4. Verify signatures using platform-native crypto
 *
 */
public class ValidateChainCertificateUsingPKIXSignum :
    ValidateCertificateChainUsingPKIX<List<X509Certificate>, X509Certificate> {

    override suspend fun invoke(
        chain: List<X509Certificate>,
        trustAnchors: NonEmptyList<X509Certificate>,
    ): CertificationChainValidation<X509Certificate> {
        if (chain.isEmpty()) {
            return CertificationChainValidation.NotTrusted(
                IllegalArgumentException("Certificate chain is empty"),
            )
        }

        return try {
            // Step 1: Verify chain continuity
            verifyChainContinuity(chain)

            // Step 2: Find a trust anchor that validates the path
            val trustAnchor = findValidatingTrustAnchor(chain, trustAnchors.list)
                ?: return CertificationChainValidation.NotTrusted(
                    IllegalStateException("No trust anchor validated the chain"),
                )

            // Step 3: Verify validity periods
            verifyValidityPeriods(chain)

            CertificationChainValidation.Trusted(trustAnchor)
        } catch (e: Exception) {
            CertificationChainValidation.NotTrusted(e)
        }
    }

    /**
     * Verifies that each certificate in the chain signs the next certificate.
     *
     * @throws IllegalStateException if signature verification fails
     */
    private suspend fun verifyChainContinuity(chain: List<X509Certificate>) {
        for (i in 0 until chain.size - 1) {
            val cert = chain[i]
            val issuerCert = chain[i + 1]

            // Verify that issuerCert signs cert
            // Based on Signum docs example:
            // val verifier = untrustedCert.signatureAlgorithm.verifierFor(rootCert.publicKey).getOrThrow()
            // val plaintext = untrustedCert.tbsCertificate.encodeToDer()
            // val signature = untrustedCert.signature
            // val isValid = verifier.verify(plaintext, signature).isSuccess

            val issuerPublicKey = issuerCert.decodedPublicKey.getOrElse {
                throw IllegalStateException(
                    "Cannot decode public key at index ${i + 1}: ${it.message}",
                    it,
                )
            }

            // Ensure signature algorithm is supported (throws if not)
            cert.signatureAlgorithm.requireSupported()
            // Cast to X509SignatureAlgorithm (which is SpecializedSignatureAlgorithm)
            val sigAlg = cert.signatureAlgorithm as X509SignatureAlgorithm

            val verifier = sigAlg.verifierFor(issuerPublicKey).getOrElse {
                throw IllegalStateException(
                    "Cannot create verifier at index $i: ${it.message}",
                    it,
                )
            }

            val tbsBytes = cert.tbsCertificate.encodeToDer()
            val certSignature = cert.decodedSignature.getOrElse {
                throw IllegalStateException(
                    "Cannot decode signature at index $i: ${it.message}",
                    it,
                )
            }
            val isValid = verifier.verify(tbsBytes, certSignature).isSuccess

            if (!isValid) {
                throw IllegalStateException(
                    "Certificate at index $i is not signed by certificate at index ${i + 1}",
                )
            }
        }
    }

    /**
     * Finds a trust anchor that can validate the root certificate.
     *
     * @return the trust anchor if found, null otherwise
     */
    private suspend fun findValidatingTrustAnchor(
        chain: List<X509Certificate>,
        trustAnchors: List<X509Certificate>,
    ): X509Certificate? {
        val root = chain.last()

        for (trustAnchor in trustAnchors) {
            // Check if root is the trust anchor (by comparing encoded bytes)
            if (root.encodeToDer().contentEquals(trustAnchor.encodeToDer())) {
                return trustAnchor
            }

            // Check if trust anchor signs the root
            val trustAnchorPublicKey = trustAnchor.decodedPublicKey.getOrNull()
            if (trustAnchorPublicKey == null) {
                continue // Cannot decode trust anchor public key, try next anchor
            }

            // Check if signature algorithm is supported
            try {
                root.signatureAlgorithm.requireSupported()
            } catch (e: Exception) {
                continue // Unsupported signature algorithm, try next anchor
            }
            val rootSigAlg = root.signatureAlgorithm as X509SignatureAlgorithm

            val verifierResult = rootSigAlg.verifierFor(trustAnchorPublicKey)
            if (verifierResult.isFailure) {
                continue // Cannot create verifier, try next anchor
            }

            val verifier = verifierResult.getOrThrow()
            val tbsBytes = root.tbsCertificate.encodeToDer()
            val rootSignature = root.decodedSignature.getOrNull()
            if (rootSignature == null) {
                continue // Cannot decode root signature, try next anchor
            }

            val isValid = verifier.verify(tbsBytes, rootSignature).isSuccess

            if (isValid) {
                return trustAnchor
            }
        }

        return null
    }

    /**
     * Verifies that all certificates in the chain are within their validity period.
     *
     * Checks that the current time is between validFrom and validUntil for each certificate.
     * Asn1Time has an `instant` property that gives direct access to kotlinx.datetime.Instant.
     *
     * @throws IllegalStateException if any certificate is expired or not yet valid
     */
    private fun verifyValidityPeriods(chain: List<X509Certificate>) {
        val now = Clock.System.now()

        for ((index, cert) in chain.withIndex()) {
            val validFrom = cert.tbsCertificate.validFrom.instant
            val validUntil = cert.tbsCertificate.validUntil.instant

            // Check if certificate is not yet valid
            if (now < validFrom) {
                val subject = try {
                    cert.tbsCertificate.subjectName.joinToString(", ")
                } catch (e: Exception) {
                    "certificate at index $index"
                }
                throw IllegalStateException(
                    "Certificate not yet valid: $subject. " +
                        "Current time: $now, Valid from: $validFrom",
                )
            }

            // Check if certificate has expired
            if (now > validUntil) {
                val subject = try {
                    cert.tbsCertificate.subjectName.joinToString(", ")
                } catch (e: Exception) {
                    "certificate at index $index"
                }
                throw IllegalStateException(
                    "Certificate has expired: $subject. " +
                        "Current time: $now, Valid until: $validUntil",
                )
            }
        }
    }
}
