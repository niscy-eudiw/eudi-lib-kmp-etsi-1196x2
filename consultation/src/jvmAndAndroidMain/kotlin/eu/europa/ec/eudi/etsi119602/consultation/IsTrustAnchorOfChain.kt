/*
 * Copyright (c) 2023 European Commission
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
package eu.europa.ec.eudi.etsi119602.consultation

import eu.europa.ec.eudi.etsi119602.ServiceDigitalIdentity
import eu.europa.ec.eudi.etsi119602.x509CertificateOf
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

public class IsTrustAnchorOfChain private constructor(
    private val validateCertificateChain: ValidateCertificateChainJvm,
    private val chain: List<X509Certificate>,
) {

    private val certificateFactory: CertificateFactory
        get() = validateCertificateChain.certificateFactory

    public suspend operator fun invoke(value: ServiceDigitalIdentity): Boolean {
        val trustAnchors =
            value.x509Certificates.orEmpty().map { pkiObj ->
                val anchorCert = certificateFactory.x509CertificateOf(pkiObj)
                TrustAnchor(anchorCert, null)
            }.toSet()
        return when (validateCertificateChain(chain, trustAnchors)) {
            ValidateCertificateChain.Outcome.Trusted -> true
            is ValidateCertificateChain.Outcome.Untrusted -> false
        }
    }
}
