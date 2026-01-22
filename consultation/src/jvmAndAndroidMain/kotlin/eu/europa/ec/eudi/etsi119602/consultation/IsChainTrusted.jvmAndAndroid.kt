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

import eu.europa.ec.eudi.etsi119602.x509CertificateOf
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

//
// JVM Implementation
//

public fun IsChainTrusted.Companion.jvm(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    getTrustAnchorsByVerificationContext: GetTrustAnchorsByVerificationContext<TrustAnchor>,
): IsChainTrusted<List<X509Certificate>> =
    invoke(validateCertificateChain, getTrustAnchorsByVerificationContext)

public fun IsChainTrusted.Companion.jvmUsingLoTE(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    getLatestListOfTrustedEntitiesByType: GetLatestListOfTrustedEntitiesByType,
    trustAnchorCreatorByVerificationContext: TrustAnchorCreatorByVerificationContext<TrustAnchor> = createTrustAnchorWithNoNameConstraints(
        validateCertificateChain.certificateFactory,
    ),
    trustSourcePerVerificationContext: (VerificationContext) -> TrustSource.LoTE,
): IsChainTrusted<List<X509Certificate>> =
    usingLoTE(
        validateCertificateChain,
        getLatestListOfTrustedEntitiesByType,
        trustAnchorCreatorByVerificationContext,
        trustSourcePerVerificationContext,
    )

public fun createTrustAnchorWithNoNameConstraints(factory: CertificateFactory): TrustAnchorCreatorByVerificationContext<TrustAnchor> =
    TrustAnchorCreatorByVerificationContext { _ -> TrustAnchorCreator.jvm(factory) }

public fun TrustAnchorCreator.Companion.jvm(
    certificateFactory: CertificateFactory = ValidateCertificateChainJvm.X509_CERT_FACTORY,
    nameConstraints: ByteArray? = null,
): TrustAnchorCreator<TrustAnchor> =
    TrustAnchorCreator { pkiObject ->
        TrustAnchor(certificateFactory.x509CertificateOf(pkiObject), nameConstraints)
    }
