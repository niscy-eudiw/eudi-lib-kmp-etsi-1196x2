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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for PKIX certificate chain validation using Signum.
 *
 * These tests verify the ValidateChainCertificateUsingPKIXSignum implementation
 * against various certificate chain configurations.
 */
class ValidateChainCertificateUsingPKIXSignumTest {

    private val validator = ValidateChainCertificateUsingPKIXSignum()
    private val rootCa = TestCertificatesJvm.rootCa
    private val intermediateCa = TestCertificatesJvm.intermediateCa
    private val endEntity = TestCertificatesJvm.endEntity

    @Test
    fun `chain contains end-entity and intermediate certs should succeed`() = runTest {
        // Chain contains end-entity and intermediate certs
        // Trust contains the root CA cert
        val chain = listOf(endEntity, intermediateCa)
        val trust = NonEmptyList(listOf(rootCa))

        val result = validator(chain, trust)

        val trusted = assertIs<CertificationChainValidation.Trusted<X509Certificate>>(result)
        // Trust anchor should be the root CA
        assertEquals(rootCa.encodeToDer().toList(), trusted.trustAnchor.encodeToDer().toList())
    }

    @Test
    fun `chain contains end-entity intermediate and root certs should succeed`() = runTest {
        // Chain contains all certificates: end-entity, intermediate, and root
        // Trust contains the root CA cert
        val chain = listOf(endEntity, intermediateCa, rootCa)
        val trust = NonEmptyList(listOf(rootCa))

        val result = validator(chain, trust)

        val trusted = assertIs<CertificationChainValidation.Trusted<X509Certificate>>(result)
        // Trust anchor should be the root CA
        assertEquals(rootCa.encodeToDer().toList(), trusted.trustAnchor.encodeToDer().toList())
    }

    @Test
    fun `chain contains end-entity cert trust contains intermediate and root certs should succeed`() = runTest {
        // Chain contains only end-entity
        // Trust contains the intermediate CA and root CA certs
        val chain = listOf(endEntity)
        val trust = NonEmptyList(listOf(intermediateCa, rootCa))

        val result = validator(chain, trust)

        // Should succeed - the intermediate cert is in trust and signs the end-entity
        assertIs<CertificationChainValidation.Trusted<X509Certificate>>(result)
    }

    @Test
    fun `validate a partial chain without intermediate should fail`() = runTest {
        // Chain contains only end-entity (missing intermediate)
        // Trust contains only root CA
        val chain = listOf(endEntity)
        val trust = NonEmptyList(listOf(rootCa))

        val result = validator(chain, trust)

        // Should fail because intermediate is missing
        assertIs<CertificationChainValidation.NotTrusted>(result)
    }

    @Test
    fun `when directly trusting the intermediate should succeed`() = runTest {
        // Chain contains end-entity
        // Trust contains intermediate CA (not root)
        val chain = listOf(endEntity)
        val trust = NonEmptyList(listOf(intermediateCa))

        val result = validator(chain, trust)

        val trusted = assertIs<CertificationChainValidation.Trusted<X509Certificate>>(result)
        // Trust anchor should be the intermediate CA
        assertEquals(intermediateCa.encodeToDer().toList(), trusted.trustAnchor.encodeToDer().toList())
    }

    @Test
    fun `when chain is in reverse order should fail`() = runTest {
        // Chain in wrong order: intermediate, end-entity (should be end-entity, intermediate)
        val chain = listOf(intermediateCa, endEntity)
        val trust = NonEmptyList(listOf(rootCa))

        val result = validator(chain, trust)

        // Should fail because chain order is wrong
        assertIs<CertificationChainValidation.NotTrusted>(result)
    }

    @Test
    fun `when chain is empty should return not trusted`() = runTest {
        val chain = emptyList<X509Certificate>()
        val trust = NonEmptyList(listOf(rootCa))

        val result = validator(chain, trust)

        assertIs<CertificationChainValidation.NotTrusted>(result)
    }

    @Test
    fun `when trust anchor does not sign the chain should fail`() = runTest {
        // Chain is valid, but trust anchor is the end-entity (not in the signing chain)
        val chain = listOf(endEntity, intermediateCa)
        val trust = NonEmptyList(listOf(endEntity))

        val result = validator(chain, trust)

        // Should fail because end-entity doesn't sign the intermediate
        assertIs<CertificationChainValidation.NotTrusted>(result)
    }

    @Test
    fun `self-signed certificate should validate against itself`() = runTest {
        // Root CA is self-signed
        val chain = listOf(rootCa)
        val trust = NonEmptyList(listOf(rootCa))

        val result = validator(chain, trust)

        val trusted = assertIs<CertificationChainValidation.Trusted<X509Certificate>>(result)
        assertEquals(rootCa.encodeToDer().toList(), trusted.trustAnchor.encodeToDer().toList())
    }

    @Test
    fun `chain with multiple intermediates in trust should succeed`() = runTest {
        // Chain: end-entity -> intermediate
        // Trust: root, intermediate
        val chain = listOf(endEntity, intermediateCa)
        val trust = NonEmptyList(listOf(rootCa, intermediateCa))

        val result = validator(chain, trust)

        // Should succeed - intermediate validates with root in trust
        assertIs<CertificationChainValidation.Trusted<X509Certificate>>(result)
    }
}
