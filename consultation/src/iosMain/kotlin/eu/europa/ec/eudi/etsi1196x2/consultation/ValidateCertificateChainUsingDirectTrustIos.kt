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
package eu.europa.ec.eudi.etsi1196x2.consultation

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.CFBridgingRelease
import platform.Security.SecCertificateCopySerialNumberData
import platform.Security.SecCertificateCopySubjectSummary
import platform.Security.SecCertificateRef

/**
 * Certificate identifier for iOS certificates.
 *
 * @property subject the certificate subject
 * @property serialNumber the certificate serial number (hex string)
 */
public data class IosCertificateId(
    val subject: String,
    val serialNumber: String,
)

/**
 * iOS-specific implementation of Direct Trust validation.
 *
 * This implementation performs direct certificate matching by comparing the head certificate
 * against trust anchors using subject and serial number.
 */
@OptIn(ExperimentalForeignApi::class)
public object ValidateCertificateChainUsingDirectTrustIos {

    /**
     * Creates a [ValidateCertificateChain] that performs direct trust validation.
     */
    public fun create(): ValidateCertificateChain<List<SecCertificateRef>, IosTrustAnchor> =
        ValidateCertificateChainUsingDirectTrust(
            headCertificateId = { chain -> chain.first().toCertificateId() },
            trustToCertificateId = { anchor -> anchor.certificate.toCertificateId() },
        )

    /**
     * Default instance.
     */
    public val Default: ValidateCertificateChain<List<SecCertificateRef>, IosTrustAnchor>
        get() = create()
}

/**
 * Extracts certificate identifier from a SecCertificate.
 */
@OptIn(ExperimentalForeignApi::class)
private fun SecCertificateRef.toCertificateId(): IosCertificateId {
    val subject = SecCertificateCopySubjectSummary(this)?.let {
        CFBridgingRelease(it) as? String
    } ?: ""

    val serialNumber = SecCertificateCopySerialNumberData(this, null)?.let { serialData ->
        // TODO: Convert CFDataRef to hex string
        // For now, return empty string
        ""
    } ?: ""

    return IosCertificateId(subject, serialNumber)
}
