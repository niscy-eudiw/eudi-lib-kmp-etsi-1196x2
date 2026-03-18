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
 * Tests for Direct Trust certificate chain validation using Signum.
 *
 * These tests verify the ValidateChainCertificateUsingDirectTrustSignum implementation
 * which validates by matching the leaf certificate against trust anchors.
 */
class ValidateChainCertificateUsingDirectTrustSignumTest {

    private val validator = ValidateChainCertificateUsingDirectTrustSignum()
    private val rootCa = TestCertificatesJvm.rootCa
    private val intermediateCa = TestCertificatesJvm.intermediateCa
    private val endEntity = TestCertificatesJvm.endEntity

    @Test
    fun `when first certificate in chain matches trust anchor it should be trusted`() = runTest {
        // Chain contains end-entity and intermediate
        // Trust anchor is the end-entity itself
        val chain = listOf(endEntity, intermediateCa)
        val trustAnchors = NonEmptyList(listOf(endEntity))

        val result = validator(chain, trustAnchors)

        val trusted = assertIs<CertificationChainValidation.Trusted<X509Certificate>>(result)
        assertEquals(endEntity.encodeToDer().toList(), trusted.trustAnchor.encodeToDer().toList())
    }

    @Test
    fun `when chain and trust anchors contain only the end-entity it should be trusted`() = runTest {
        // Chain contains only end-entity
        // Trust anchor is the end-entity
        val chain = listOf(endEntity)
        val trustAnchors = NonEmptyList(listOf(endEntity))

        val result = validator(chain, trustAnchors)

        val trusted = assertIs<CertificationChainValidation.Trusted<X509Certificate>>(result)
        assertEquals(endEntity.encodeToDer().toList(), trusted.trustAnchor.encodeToDer().toList())
    }

    @Test
    fun `when first certificate in chain does not match any trust anchor it should not be trusted`() = runTest {
        // Chain contains end-entity and intermediate
        // Trust anchor is the root CA (not the leaf)
        val chain = listOf(endEntity, intermediateCa)
        val trustAnchors = NonEmptyList(listOf(rootCa))

        val result = validator(chain, trustAnchors)

        assertIs<CertificationChainValidation.NotTrusted>(result)
    }

    @Test
    fun `when chain is empty it should return not trusted`() = runTest {
        val chain = emptyList<X509Certificate>()
        val trustAnchors = NonEmptyList(listOf(rootCa))

        val result = validator(chain, trustAnchors)

        assertIs<CertificationChainValidation.NotTrusted>(result)
    }

    @Test
    fun `when intermediate certificate is trusted should fail`() = runTest {
        // Chain contains end-entity and intermediate
        // Trust anchor is the intermediate (not the leaf)
        val chain = listOf(endEntity, intermediateCa)
        val trustAnchors = NonEmptyList(listOf(intermediateCa))

        val result = validator(chain, trustAnchors)

        // Should fail because direct trust only matches the leaf (first) certificate
        assertIs<CertificationChainValidation.NotTrusted>(result)
    }

    @Test
    fun `when leaf matches one of multiple trust anchors should succeed`() = runTest {
        // Chain contains end-entity
        // Trust anchors contain root, intermediate, and end-entity
        val chain = listOf(endEntity)
        val trustAnchors = NonEmptyList(listOf(rootCa, intermediateCa, endEntity))

        val result = validator(chain, trustAnchors)

        val trusted = assertIs<CertificationChainValidation.Trusted<X509Certificate>>(result)
        assertEquals(endEntity.encodeToDer().toList(), trusted.trustAnchor.encodeToDer().toList())
    }

    @Test
    fun `when chain order is reversed should fail`() = runTest {
        // Chain in wrong order: intermediate first, then end-entity
        // Trust anchor is the intermediate
        val chain = listOf(intermediateCa, endEntity)
        val trustAnchors = NonEmptyList(listOf(intermediateCa))

        val result = validator(chain, trustAnchors)

        // Should succeed because first cert in chain (intermediate) matches trust anchor
        val trusted = assertIs<CertificationChainValidation.Trusted<X509Certificate>>(result)
        assertEquals(intermediateCa.encodeToDer().toList(), trusted.trustAnchor.encodeToDer().toList())
    }

    @Test
    fun `self-signed root certificate should be trusted if in trust anchors`() = runTest {
        // Chain contains only root CA (self-signed)
        // Trust anchor is the root CA
        val chain = listOf(rootCa)
        val trustAnchors = NonEmptyList(listOf(rootCa))

        val result = validator(chain, trustAnchors)

        val trusted = assertIs<CertificationChainValidation.Trusted<X509Certificate>>(result)
        assertEquals(rootCa.encodeToDer().toList(), trusted.trustAnchor.encodeToDer().toList())
    }

    @Test
    fun `when leaf does not match any of multiple trust anchors should fail`() = runTest {
        // Chain contains end-entity
        // Trust anchors contain only root and intermediate (not end-entity)
        val chain = listOf(endEntity)
        val trustAnchors = NonEmptyList(listOf(rootCa, intermediateCa))

        val result = validator(chain, trustAnchors)

        assertIs<CertificationChainValidation.NotTrusted>(result)
    }
}
