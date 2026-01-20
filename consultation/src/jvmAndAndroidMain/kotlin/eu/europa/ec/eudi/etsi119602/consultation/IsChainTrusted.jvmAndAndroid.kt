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
import java.security.InvalidAlgorithmParameterException
import java.security.Provider
import java.security.cert.*

//
// JVM Implementation
//

public class IsChainTrustedJvm
internal constructor(
    private val certificateFactory: () -> CertificateFactory,
    private val certPathValidator: () -> CertPathValidator,
    private val getTrustAnchorsByVerification: GetTrustAnchorsByVerification,
    private val customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
) : IsChainTrusted<List<X509Certificate>> {

    public constructor(
        getTrustAnchorsByVerification: GetTrustAnchorsByVerification,
        customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
    ) : this(
        { CertificateFactory.getInstance(X_509) },
        { CertPathValidator.getInstance(PKIX) },
        getTrustAnchorsByVerification,
        customization,
    )

    public constructor(
        provider: Provider,
        getTrustAnchorsByVerification: GetTrustAnchorsByVerification,
        customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
    ) : this(
        { CertificateFactory.getInstance(X_509, provider) },
        { CertPathValidator.getInstance(PKIX, provider) },
        getTrustAnchorsByVerification,
        customization,
    )

    override suspend fun invoke(
        chain: List<X509Certificate>,
        signatureVerification: IsChainTrusted.SignatureVerification,
    ): IsChainTrusted.Outcome {
        require(chain.isNotEmpty()) { "Chain must not be empty" }
        val trustAnchors = getTrustAnchorsByVerification(signatureVerification)
        return validate(chain, trustAnchors)
    }

    private fun validate(chain: List<X509Certificate>, anchors: Set<TrustAnchor>): IsChainTrusted.Outcome =
        try {
            val pkixParameters =
                PKIXParameters(anchors.toSet()).apply(customization)
            val certPath = certificateFactory().generateCertPath(chain)
            certPathValidator().validate(certPath, pkixParameters)
            IsChainTrusted.Outcome.Trusted
        } catch (e: CertPathValidatorException) {
            IsChainTrusted.Outcome.Untrusted(e)
        } catch (e: InvalidAlgorithmParameterException) {
            IsChainTrusted.Outcome.Untrusted(e)
        }

    public companion object {
        internal val DEFAULT_CUSTOMIZATION: PKIXParameters.() -> Unit = { isRevocationEnabled = false }
        private const val X_509 = "X.509"
        private const val PKIX = "PKIX"
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
    public operator fun invoke(profile: EUListOfTrustedEntitiesProfile, serviceType: URI): (X509Certificate) -> TrustAnchor

    public companion object {
        public val WITH_NO_NAME_CONSTRAINTS: CreateTrustAnchor = CreateTrustAnchor { _, _ -> { TrustAnchor(it, null) } }
    }
}

public fun interface GetListByProfile {
    public suspend operator fun invoke(loteType: URI): ListOfTrustedEntities
}
