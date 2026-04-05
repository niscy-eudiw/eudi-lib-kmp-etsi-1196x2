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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for SignumCertificateOperations.
 *
 * These tests verify certificate field extraction and extension parsing
 * using the test certificates.
 */
class SignumCertificateOperationsTest {

    private val operations = SignumCertificateOperations()
    private val rootCa = TestCertificatesJvm.rootCa
    private val intermediateCa = TestCertificatesJvm.intermediateCa
    private val endEntity = TestCertificatesJvm.endEntity

    // ===== isSelfSigned() Tests =====

    @Test
    fun `root CA certificate should be self-signed`() {
        val isSelfSigned = operations.isSelfSigned(rootCa)
        assertTrue(isSelfSigned, "Root CA should be self-signed")
    }

    @Test
    fun `intermediate CA certificate should not be self-signed`() {
        val isSelfSigned = operations.isSelfSigned(intermediateCa)
        assertFalse(isSelfSigned, "Intermediate CA should not be self-signed")
    }

    @Test
    fun `end entity certificate should not be self-signed`() {
        val isSelfSigned = operations.isSelfSigned(endEntity)
        assertFalse(isSelfSigned, "End entity should not be self-signed")
    }

    // ===== getValidityPeriod() Tests =====

    @Test
    fun `should extract validity period from certificate`() {
        val validity = operations.getValidityPeriod(rootCa)
        assertNotNull(validity, "Validity period should not be null")
        assertNotNull(validity.notBefore, "notBefore should not be null")
        assertNotNull(validity.notAfter, "notAfter should not be null")
        assertTrue(
            validity.notBefore < validity.notAfter,
            "notBefore should be before notAfter",
        )
    }

    @Test
    fun `validity period should be consistent across all test certificates`() {
        val rootValidity = operations.getValidityPeriod(rootCa)
        val intermediateValidity = operations.getValidityPeriod(intermediateCa)
        val endEntityValidity = operations.getValidityPeriod(endEntity)

        // All test certs should have valid periods
        assertTrue(rootValidity.notBefore < rootValidity.notAfter)
        assertTrue(intermediateValidity.notBefore < intermediateValidity.notAfter)
        assertTrue(endEntityValidity.notBefore < endEntityValidity.notAfter)
    }

    // ===== getBasicConstraints() Tests =====

    @Test
    fun `root CA should have CA basic constraint set to true`() {
        val basicConstraints = operations.getBasicConstraints(rootCa)
        assertTrue(basicConstraints.isCa, "Root CA should have isCa=true")
    }

    @Test
    fun `intermediate CA should have CA basic constraint set to true`() {
        val basicConstraints = operations.getBasicConstraints(intermediateCa)
        assertTrue(basicConstraints.isCa, "Intermediate CA should have isCa=true")
        // Our test intermediate has pathLen=0
        assertNotNull(basicConstraints.pathLenConstraint, "Intermediate should have path length constraint")
        assertEquals(0, basicConstraints.pathLenConstraint, "Path length should be 0")
    }

    @Test
    fun `end entity should have CA basic constraint set to false`() {
        val basicConstraints = operations.getBasicConstraints(endEntity)
        assertFalse(basicConstraints.isCa, "End entity should have isCa=false")
    }

    // ===== getKeyUsage() Tests =====

    @Test
    fun `root CA should have keyCertSign and crlSign key usage`() {
        val keyUsage = operations.getKeyUsage(rootCa)
        assertNotNull(keyUsage, "Key usage should not be null for root CA")
        assertTrue(keyUsage.keyCertSign, "Root CA should have keyCertSign")
        assertTrue(keyUsage.crlSign, "Root CA should have crlSign")
    }

    @Test
    fun `intermediate CA should have digitalSignature keyCertSign and crlSign`() {
        val keyUsage = operations.getKeyUsage(intermediateCa)
        assertNotNull(keyUsage, "Key usage should not be null for intermediate CA")
        assertTrue(keyUsage.digitalSignature, "Intermediate should have digitalSignature")
        assertTrue(keyUsage.keyCertSign, "Intermediate should have keyCertSign")
        assertTrue(keyUsage.crlSign, "Intermediate should have crlSign")
    }

    @Test
    fun `end entity should have digitalSignature key usage`() {
        val keyUsage = operations.getKeyUsage(endEntity)
        assertNotNull(keyUsage, "Key usage should not be null for end entity")
        assertTrue(keyUsage.digitalSignature, "End entity should have digitalSignature")
        assertFalse(keyUsage.keyCertSign, "End entity should not have keyCertSign")
    }

    @Test
    fun `key usage bits should be properly decoded`() {
        val keyUsage = operations.getKeyUsage(rootCa)
        assertNotNull(keyUsage)

        // Root CA should only have keyCertSign and crlSign
        assertFalse(keyUsage.digitalSignature, "Root should not have digitalSignature")
        assertFalse(keyUsage.nonRepudiation, "Root should not have nonRepudiation")
        assertFalse(keyUsage.keyEncipherment, "Root should not have keyEncipherment")
        assertFalse(keyUsage.dataEncipherment, "Root should not have dataEncipherment")
        assertFalse(keyUsage.keyAgreement, "Root should not have keyAgreement")
        assertTrue(keyUsage.keyCertSign, "Root should have keyCertSign")
        assertTrue(keyUsage.crlSign, "Root should have crlSign")
        assertFalse(keyUsage.encipherOnly, "Root should not have encipherOnly")
        assertFalse(keyUsage.decipherOnly, "Root should not have decipherOnly")
    }

    // ===== getQcStatements() Tests =====

    @Test
    fun `getQcStatements should return empty list for certificates without QC extension`() {
        // Our test certificates don't have QC statements
        val qcStatements = operations.getQcStatements(rootCa)
        assertTrue(qcStatements.isEmpty(), "Should return empty list when QC extension is not present")
    }

    @Test
    fun `getQcStatements should not throw on any certificate`() {
        // Should handle missing extension gracefully
        operations.getQcStatements(rootCa)
        operations.getQcStatements(intermediateCa)
        operations.getQcStatements(endEntity)
    }

    // ===== getCertificatePolicies() Tests =====

    @Test
    fun `getCertificatePolicies should return empty list for certificates without policies`() {
        // Our test certificates don't have certificate policies
        val policies = operations.getCertificatePolicies(rootCa)
        assertTrue(policies.isEmpty(), "Should return empty list when policies extension is not present")
    }

    @Test
    fun `getCertificatePolicies should not throw on any certificate`() {
        // Should handle missing extension gracefully
        operations.getCertificatePolicies(rootCa)
        operations.getCertificatePolicies(intermediateCa)
        operations.getCertificatePolicies(endEntity)
    }

    // ===== getAiaExtension() Tests =====

    @Test
    fun `getAiaExtension should return null for certificates without AIA extension`() {
        // Our test certificates don't have AIA extension
        val aia = operations.getAiaExtension(rootCa)
        assertNull(aia, "Should return null when AIA extension is not present")
    }

    @Test
    fun `getAiaExtension should not throw on any certificate`() {
        // Should handle missing extension gracefully
        operations.getAiaExtension(rootCa)
        operations.getAiaExtension(intermediateCa)
        operations.getAiaExtension(endEntity)
    }

    // ===== Integration Tests =====

    @Test
    fun `all certificate operations should work on all test certificates`() {
        val certificates = listOf(rootCa, intermediateCa, endEntity)

        certificates.forEach { cert ->
            // All operations should succeed without throwing
            operations.isSelfSigned(cert)
            operations.getValidityPeriod(cert)
            operations.getBasicConstraints(cert)
            operations.getKeyUsage(cert)
            operations.getQcStatements(cert)
            operations.getCertificatePolicies(cert)
            operations.getAiaExtension(cert)
        }
    }

    @Test
    fun `certificate hierarchy should be reflected in basic constraints`() {
        val rootBasicConstraints = operations.getBasicConstraints(rootCa)
        val intermediateBasicConstraints = operations.getBasicConstraints(intermediateCa)
        val endEntityBasicConstraints = operations.getBasicConstraints(endEntity)

        // Root and intermediate are CAs
        assertTrue(rootBasicConstraints.isCa)
        assertTrue(intermediateBasicConstraints.isCa)

        // End entity is not a CA
        assertFalse(endEntityBasicConstraints.isCa)

        // Intermediate has path length constraint of 0 (can't have sub-CAs)
        assertEquals(0, intermediateBasicConstraints.pathLenConstraint)
    }
}
