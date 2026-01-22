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
import eu.europa.ec.eudi.etsi119602.profile.*

/**
 * A way to get a set of trust anchors for a given [VerificationContext]
 *
 * @param TRUST_ANCHOR the type representing a trust anchor
 */
public fun interface GetTrustAnchorsByVerificationContext<out TRUST_ANCHOR : Any> {

    /**
     * Gets a set of trust anchors for a given [VerificationContext]
     * @param verificationContext the verification context
     * @return a set of trust anchors
     */
    public suspend operator fun invoke(verificationContext: VerificationContext): List<TRUST_ANCHOR>

    /**
     * Combines this [GetTrustAnchorsByVerificationContext] with another to create a new one
     * @param other the other [GetTrustAnchorsByVerificationContext]
     * @return a new [GetTrustAnchorsByVerificationContext]
     */
    public operator fun plus(other: GetTrustAnchorsByVerificationContext<@UnsafeVariance TRUST_ANCHOR>): GetTrustAnchorsByVerificationContext<TRUST_ANCHOR> =
        GetTrustAnchorsByVerificationContext { signatureVerification ->
            this.invoke(signatureVerification) + other.invoke(signatureVerification)
        }

    public companion object {

        /**
         * Creates an instance of [GetTrustAnchorsByVerificationContext] using the provided [getLatestListOfTrustedEntitiesByType]
         * @param getLatestListOfTrustedEntitiesByType the function to obtain the list of trusted entities by type
         * @param trustAnchorCreatorByVerificationContext the function to obtain the trust anchor creator by verification context
         * @Param TRUST_ANCHOR the type representing a trust anchor
         * @return an instance of [GetTrustAnchorsByVerificationContext]
         */
        public fun <TRUST_ANCHOR : Any> usingLoTE(
            getLatestListOfTrustedEntitiesByType: GetLatestListOfTrustedEntitiesByType,
            trustAnchorCreatorByVerificationContext: TrustAnchorCreatorByVerificationContext<TRUST_ANCHOR>,
            trustSourcePerVerificationContext: (VerificationContext) -> TrustSource.LoTE,
        ): GetTrustAnchorsByVerificationContext<TRUST_ANCHOR> =
            UsingLote(
                getLatestListOfTrustedEntitiesByType,
                trustAnchorCreatorByVerificationContext,
                trustSourcePerVerificationContext,
            )
    }
}

internal class UsingLote<out TRUST_ANCHOR : Any>(
    private val getLatestListOfTrustedEntitiesByType: GetLatestListOfTrustedEntitiesByType,
    private val trustAnchorCreatorByVerificationContext: TrustAnchorCreatorByVerificationContext<TRUST_ANCHOR>,
    private val trustSourcePerVerificationContext: (VerificationContext) -> TrustSource.LoTE,
) : GetTrustAnchorsByVerificationContext<TRUST_ANCHOR> {

    override suspend fun invoke(verificationContext: VerificationContext): List<TRUST_ANCHOR> {
        val (loteType, serviceType) = trustSourcePerVerificationContext(verificationContext)
        val listOfTrustedEntities = listOf(loteType)
        val trustAnchorCreator = trustAnchorCreatorByVerificationContext(verificationContext)
        return with(trustAnchorCreator) {
            listOfTrustedEntities.trustAnchorsOfType(serviceType)
        }
    }

    @Throws(IllegalStateException::class)
    private suspend fun listOf(loteType: URI): ListOfTrustedEntities {
        val lote = getLatestListOfTrustedEntitiesByType(loteType)
        return checkNotNull(lote) { "Unable to find List of Trusted Entities for $loteType" }
    }

    companion object {

        val WELL_KNOWN_PROFILES: List<EUListOfTrustedEntitiesProfile> = listOf(
            EUPIDProvidersList,
            EUWalletProvidersList,
            EUWRPRCProvidersList,
            EUWRPACProvidersList,
            EUMDLProvidersList,
        )

        fun trustSources(verificationContext: VerificationContext, usedProfiles: List<EUListOfTrustedEntitiesProfile>): Pair<EUListOfTrustedEntitiesProfile, URI> {
            val trustSource = verificationContext.loteTrustSource(usedProfiles)
            checkNotNull(trustSource) { "Unable to find trust source for $verificationContext" }
            val (loteType, svcType) = trustSource
            val profile = usedProfiles.first { it.listAndSchemeInformation.type == loteType }
            return profile to svcType
        }

        private fun VerificationContext.loteTrustSource(usedProfiles: List<EUListOfTrustedEntitiesProfile>): TrustSource.LoTE? {
            fun EUListOfTrustedEntitiesProfile.svcTypeWithSuffix(suffix: String): URI {
                val svcType = trustedEntities.serviceTypeIdentifiers.firstOrNull { it.endsWith(suffix) }
                return checkNotNull(svcType) { "Unable to find service type for $this with suffix $suffix" }
            }

            fun EUListOfTrustedEntitiesProfile.trustSource(suffix: String): TrustSource.LoTE? =
                if (this in usedProfiles) {
                    TrustSource.LoTE(this, svcTypeWithSuffix(suffix))
                } else {
                    null
                }

            val issuance = "Issuance"
            val revocation = "Revocation"
            return when (this) {
                VerificationContext.EU_WIA,
                VerificationContext.EU_WUA,
                -> EUWalletProvidersList.trustSource(issuance)
                VerificationContext.EU_WUA_STATUS -> EUWalletProvidersList.trustSource(revocation)
                VerificationContext.EU_PID -> EUPIDProvidersList.trustSource(issuance)
                VerificationContext.EU_PID_STATUS -> EUPIDProvidersList.trustSource(revocation)
                VerificationContext.EU_PUB_EAA -> EUPubEAAProvidersList.trustSource(issuance)
                VerificationContext.EU_PUB_EAA_STATUS -> EUPubEAAProvidersList.trustSource(revocation)
                VerificationContext.EU_WRPRC -> EUWRPRCProvidersList.trustSource(issuance)
                VerificationContext.EU_WRPRC_STATUS -> EUWRPRCProvidersList.trustSource(revocation)
                VerificationContext.EU_WRPAC -> EUWRPACProvidersList.trustSource(issuance)
                VerificationContext.EU_WRPAC_STATUS -> EUWRPACProvidersList.trustSource(revocation)
                VerificationContext.EU_MDL -> EUMDLProvidersList.trustSource(issuance)
                VerificationContext.EU_MDL_STATUS -> EUMDLProvidersList.trustSource(revocation)
            }
        }
    }
}
