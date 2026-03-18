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
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle

/**
 * JVM-specific test certificate generation using the same CertOps
 * from the consultation module.
 *
 * This ensures we're using exactly the same test data as the consultation module.
 */
object TestCertificatesJvm {
    private const val SIGN_ALG = "SHA256withECDSA"

    data class CertChain(
        val endEntity: X509Certificate,
        val intermediate: X509Certificate,
        val root: X509Certificate,
    )

    /**
     * Generate test certificate chain using the same approach as consultation module
     */
    fun generateTestChain(): CertChain = with(CertOps) {
        //
        // Trust Anchor (Root CA)
        //
        val rootName: X500Name =
            X500NameBuilder(BCStyle.INSTANCE).apply {
                addRDN(BCStyle.C, "Utopia")
                addRDN(BCStyle.O, "Awesome Organization")
                addRDN(BCStyle.CN, "Demo Root Certificate")
            }.build()
        val (rootKeyPair, rootCertHolder) = genTrustAnchor(SIGN_ALG, rootName)
        val rootCert = rootCertHolder.toX509Certificate()

        //
        // Intermediate CA
        //
        val intermediateSubject =
            X500NameBuilder(BCStyle.INSTANCE).apply {
                addRDN(BCStyle.C, "Utopia")
                addRDN(BCStyle.O, "Awesome Organization")
                addRDN(BCStyle.CN, "Demo Intermediate Certificate")
            }.build()
        val (intermediateKeyPair, intermediateCertHolder) =
            genIntermediateCertificate(
                rootCertHolder,
                rootKeyPair.private,
                SIGN_ALG,
                0,
                intermediateSubject,
            )
        val intermediateCert = intermediateCertHolder.toX509Certificate()

        //
        // End Entity
        //
        val endEntitySubject =
            X500NameBuilder(BCStyle.INSTANCE).apply {
                addRDN(BCStyle.C, "Utopia")
                addRDN(BCStyle.O, "Awesome Organization")
                addRDN(BCStyle.CN, "Demo End-Entity Certificate")
            }.build()
        val (_, endEntityCertHolder) =
            genEndEntity(intermediateCertHolder, intermediateKeyPair.private, SIGN_ALG, endEntitySubject)
        val endEntityCert = endEntityCertHolder.toX509Certificate()

        // Convert JVM X509Certificate to Signum X509Certificate
        val rootSignum = X509Certificate.decodeFromDer(rootCert.encoded)
        val intermediateSignum = X509Certificate.decodeFromDer(intermediateCert.encoded)
        val endEntitySignum = X509Certificate.decodeFromDer(endEntityCert.encoded)

        return CertChain(
            endEntity = endEntitySignum,
            intermediate = intermediateSignum,
            root = rootSignum,
        )
    }

    // Cached instance to avoid regenerating on every test
    private val cached: CertChain by lazy { generateTestChain() }

    val rootCa: X509Certificate get() = cached.root
    val intermediateCa: X509Certificate get() = cached.intermediate
    val endEntity: X509Certificate get() = cached.endEntity

    val validChain: List<X509Certificate> by lazy {
        listOf(endEntity, intermediateCa, rootCa)
    }

    val partialChain: List<X509Certificate> by lazy {
        listOf(endEntity, intermediateCa)
    }
}
