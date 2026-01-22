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

import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi119602.profile.*

public enum class VerificationContext {
    /**
     * Check the wallet provider's signature for a WIA
     * Can be used by an Authorization Server implementing
     * Attestation-Based Client Authentication
     */
    EU_WIA,

    /**
     * Check the wallet provider's signature for a WUA
     * Can be used by a Credential Issuer, issuing device-bound attestations
     * that require WUA
     */
    EU_WUA,

    /**
     * Check the wallet provider's signature for the Token Status List that keeps the status of a WUA
     *
     * Can be used by a Credential Issuer, issuing device-bound attestations to keep track of WUA status
     */
    EU_WUA_STATUS,

    /**
     * Check PID Provider's signature for a PID
     *
     * Can be used by Wallets after issuance and Verifiers during presentation verification
     */
    EU_PID,

    /**
     * Check the signature of a Status Lists or Identifiers List that keeps the status of a PID
     *
     * Can be used by Wallets and Verifiers to check the status of a PID
     */
    EU_PID_STATUS,

    /**
     * Check the issuer's signature for a Public EAA
     *
     * Can be used by Wallets after issuance and Verifiers during presentation verification
     */
    EU_PUB_EAA,

    /**
     * Check the signature of a Status Lists or Identifiers List that keeps the status of a Public EAA
     *
     * Can be used by Wallets and Verifiers to check the status of a PUB_EAA
     */
    EU_PUB_EAA_STATUS,

    /**
     * Check the signature of a registration certificate of an Issuer or Verifier
     *
     * Can be used by Wallets to verify the signature of the registration certificate of an Issuer or Verifier, during
     * issuance and presentation respectively.
     */
    EU_WRPRC,

    /**
     * Check the access certificate of an Issuer or Verifier
     *
     * Can be used by Wallets to verify the signature of the registration certificate of an Issuer or Verifier, during
     * issuance (signed credential issuer metadata) and presentation respectively (signed authorization request).
     */
    EU_WRPAC,

    /**
     * Check mDL Provider's signature for an mDL
     * Can be used by Wallets and Verifiers to check the status of an mDL
     */
    EU_MDL,

    /**
     * Check the signature of a Status Lists or Identifiers List that keeps the status of an mDL
     */
    EU_MDL_STATUS,
}

/**
 * Interface for checking the trustworthiness of a certificate chain
 * in the context of a specific [verification][VerificationContext]
 *
 * @param CHAIN type representing a certificate chain
 */
public fun interface IsChainTrustedForContext<in CHAIN : Any> {

    /**
     * Check certificate chain is trusted in the context of
     * specific verification
     *
     * @param chain certificate chain to check
     * @param verificationContext verification context
     * @return outcome of the check
     */
    public suspend operator fun invoke(
        chain: CHAIN,
        verificationContext: VerificationContext,
    ): ValidateCertificateChain.Outcome

    public companion object {

        public operator fun <CHAIN : Any> invoke(
            trustSourcePerVerificationContext: (VerificationContext) -> TrustSource,
            isChainTrusted: IsChainTrusted<CHAIN, TrustSource>,
        ): IsChainTrustedForContext<CHAIN> =
            IsChainTrustedForContext { chain, verificationContext ->
                val trustSource = trustSourcePerVerificationContext(verificationContext)
                isChainTrusted(chain, trustSource)
            }
    }
}

public interface EURules {

    public fun VerificationContext.loteTrustSource(usedProfiles: List<URI>): TrustSource? {
        fun EUListOfTrustedEntitiesProfile.svcTypeWithSuffix(suffix: String): URI {
            val svcType = trustedEntities.serviceTypeIdentifiers.firstOrNull { it.endsWith(suffix) }
            return checkNotNull(svcType) { "Unable to find service type for $this with suffix $suffix" }
        }

        fun EUListOfTrustedEntitiesProfile.trustSource(suffix: String): TrustSource.LoTE? {
            val loteType = listAndSchemeInformation.type
            return if (loteType in usedProfiles) {
                TrustSource.LoTE(loteType, svcTypeWithSuffix(suffix))
            } else {
                null
            }
        }

        val issuance = "Issuance"
        val revocation = "Revocation"
        return when (this) {
            VerificationContext.EU_WIA -> EUWalletProvidersList.trustSource(issuance)
            VerificationContext.EU_WUA -> EUWalletProvidersList.trustSource(issuance)
            VerificationContext.EU_WUA_STATUS -> EUWalletProvidersList.trustSource(revocation)
            VerificationContext.EU_PID -> EUPIDProvidersList.trustSource(issuance)
            VerificationContext.EU_PID_STATUS -> EUPIDProvidersList.trustSource(revocation)
            VerificationContext.EU_PUB_EAA -> null
            VerificationContext.EU_PUB_EAA_STATUS -> null
            VerificationContext.EU_WRPRC -> EUWRPRCProvidersList.trustSource(issuance)
            VerificationContext.EU_WRPAC -> EUWRPACProvidersList.trustSource(issuance)
            VerificationContext.EU_MDL -> EUMDLProvidersList.trustSource(issuance)
            VerificationContext.EU_MDL_STATUS -> EUMDLProvidersList.trustSource(revocation)
        }
    }

    public companion object : EURules
}
