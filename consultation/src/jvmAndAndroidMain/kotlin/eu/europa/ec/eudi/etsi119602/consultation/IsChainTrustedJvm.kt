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

import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntities
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi119602.certificatesOf
import eu.europa.ec.eudi.etsi119602.profile.EUListOfTrustedEntitiesProfile
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

//
// JVM Implementation
//

public class IsChainTrustedJvm(
    private val validateCertificateChain: ValidateCertificateChainJvm,
    private val getTrustAnchorsByVerification: GetTrustAnchorsByVerification,
) : IsChainTrusted<List<X509Certificate>> {

    override suspend fun invoke(
        chain: List<X509Certificate>,
        signatureVerification: IsChainTrusted.SignatureVerification,
    ): ValidateCertificateChain.Outcome {
        val trustAnchors = getTrustAnchorsByVerification(signatureVerification)
        return validateCertificateChain(chain, trustAnchors)
    }
}

public fun interface GetTrustAnchorsByVerification {
    public suspend operator fun invoke(signatureVerification: IsChainTrusted.SignatureVerification): Set<TrustAnchor>

    public operator fun plus(other: GetTrustAnchorsByVerification): GetTrustAnchorsByVerification =
        GetTrustAnchorsByVerification { signatureVerification ->
            this.invoke(signatureVerification) + other.invoke(signatureVerification)
        }

    public companion object {
        public fun usingLoTE(
            getListByProfile: GetListByProfile,
            createTrustAnchor: CreateTrustAnchor = CreateTrustAnchor.WITH_NO_NAME_CONSTRAINTS,
        ): GetTrustAnchorsByVerification =
            GetTrustAnchorsByVerification { signatureVerification ->
                suspend fun EUListOfTrustedEntitiesProfile.getList(): ListOfTrustedEntities =
                    getListByProfile(this.listAndSchemeInformation.type).also { it.ensureCompliesToProfile() }

                val profile = signatureVerification.profile
                val serviceType = signatureVerification.serviceType()
                val listOfTrustedEntities = profile.getList()
                val certificates = listOfTrustedEntities.certificatesOf(serviceType)
                val trustAnchorFactory = createTrustAnchor(profile, serviceType)
                certificates.map(trustAnchorFactory).toSet()
            }
    }
}

public fun interface CreateTrustAnchor {
    public operator fun invoke(
        profile: EUListOfTrustedEntitiesProfile,
        serviceType: URI,
    ): (X509Certificate) -> TrustAnchor

    public companion object {
        public val WITH_NO_NAME_CONSTRAINTS: CreateTrustAnchor = CreateTrustAnchor { _, _ -> { TrustAnchor(it, null) } }
    }
}

public fun interface GetListByProfile {
    public suspend operator fun invoke(loteType: URI): ListOfTrustedEntities
}
