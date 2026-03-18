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

import at.asitplus.signum.indispensable.pki.X509Certificate
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificationChainValidation
import eu.europa.ec.eudi.etsi1196x2.consultation.NonEmptyList
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChain

/**
 * Direct trust certificate chain validation using Signum library.
 *
 * This implementation validates by matching the leaf certificate against trust anchors,
 * without full PKIX path validation. This is useful for scenarios where certificates
 * are explicitly trusted rather than validated through a CA hierarchy.
 *
 * **Validation strategy:**
 * - Extract leaf certificate from chain
 * - Match against trust anchors by comparing encoded bytes
 * - If match found, chain is trusted
 *
 * **Usage:**
 * ```kotlin
 * val validator = ValidateChainCertificateUsingDirectTrustSignum()
 * val chain = listOf(leafCert, intermediateCert, rootCert)
 * val trustAnchors = NonEmptyList(listOf(trustedCert))
 *
 * when (val result = validator(chain, trustAnchors)) {
 *     is CertificationChainValidation.Trusted -> println("Chain is trusted")
 *     is CertificationChainValidation.NotTrusted -> println("Not trusted: ${result.cause}")
 * }
 * ```
 *
 * @see ValidateCertificateChain
 */
public class ValidateChainCertificateUsingDirectTrustSignum :
    ValidateCertificateChain<List<X509Certificate>, X509Certificate> {

    override suspend fun invoke(
        chain: List<X509Certificate>,
        trustAnchors: NonEmptyList<X509Certificate>,
    ): CertificationChainValidation<X509Certificate> {
        if (chain.isEmpty()) {
            return CertificationChainValidation.NotTrusted(
                IllegalArgumentException("Certificate chain is empty"),
            )
        }

        // Extract leaf certificate (first in chain)
        val leafCertificate = chain.first()
        val leafBytes = leafCertificate.encodeToDer()

        // Find matching trust anchor by comparing DER-encoded bytes
        val matchingTrustAnchor = trustAnchors.list.firstOrNull { trustAnchor ->
            val trustAnchorBytes = trustAnchor.encodeToDer()
            leafBytes.contentEquals(trustAnchorBytes)
        }

        return if (matchingTrustAnchor != null) {
            CertificationChainValidation.Trusted(matchingTrustAnchor)
        } else {
            // Try to get subject/serial for better error message
            val subjectInfo = try {
                "subject: ${leafCertificate.tbsCertificate.subjectName}, " +
                    "serial: ${leafCertificate.tbsCertificate.serialNumber}"
            } catch (e: Exception) {
                "certificate bytes"
            }

            CertificationChainValidation.NotTrusted(
                IllegalStateException(
                    "No trust anchor matches leaf certificate ($subjectInfo)",
                ),
            )
        }
    }
}
