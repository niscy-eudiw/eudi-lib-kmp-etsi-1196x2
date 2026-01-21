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

public fun interface IsChainTrusted<in CHAIN : Any> {

    public enum class SignatureVerification {
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
        EU_WRPRC_STATUS,

        /**
         * Check the access certificate of an Issuer or Verifier
         *
         * Can be used by Wallets to verify the signature of the registration certificate of an Issuer or Verifier, during
         * issuance (signed credential issuer metadata) and presentation respectively (signed authorization request).
         */
        EU_WRPAC,
        EU_WRPAC_STATUS,

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

    public suspend operator fun invoke(
        chain: CHAIN,
        signatureVerification: SignatureVerification,
    ): ValidateCertificateChain.Outcome
}

public inline fun <C : Any, C1 : Any> IsChainTrusted<C>.contraMap(crossinline f: (C1) -> C): IsChainTrusted<C1> =
    IsChainTrusted { c1, sv -> invoke(f(c1), sv) }

public val IsChainTrusted.SignatureVerification.profile: EUListOfTrustedEntitiesProfile
    get() = when (this) {
        IsChainTrusted.SignatureVerification.EU_WIA,
        IsChainTrusted.SignatureVerification.EU_WUA,
        IsChainTrusted.SignatureVerification.EU_WUA_STATUS,
        -> EUWalletProvidersList

        IsChainTrusted.SignatureVerification.EU_PID,
        IsChainTrusted.SignatureVerification.EU_PID_STATUS,
        -> EUPIDProvidersList

        IsChainTrusted.SignatureVerification.EU_PUB_EAA,
        IsChainTrusted.SignatureVerification.EU_PUB_EAA_STATUS,
        -> EUPubEAAProvidersList

        IsChainTrusted.SignatureVerification.EU_WRPRC,
        IsChainTrusted.SignatureVerification.EU_WRPRC_STATUS,
        -> EUWRPRCProvidersList

        IsChainTrusted.SignatureVerification.EU_WRPAC,
        IsChainTrusted.SignatureVerification.EU_WRPAC_STATUS,
        -> EUWRPACProvidersList

        IsChainTrusted.SignatureVerification.EU_MDL,
        IsChainTrusted.SignatureVerification.EU_MDL_STATUS,
        -> EUMDLProvidersList
    }

public fun IsChainTrusted.SignatureVerification.serviceType(): URI {
    val suffix = run {
        val issuance = "Issuance"
        val revocation = "Revocation"
        when (this) {
            IsChainTrusted.SignatureVerification.EU_WIA -> issuance
            IsChainTrusted.SignatureVerification.EU_WUA -> issuance
            IsChainTrusted.SignatureVerification.EU_WUA_STATUS -> revocation
            IsChainTrusted.SignatureVerification.EU_PID -> issuance
            IsChainTrusted.SignatureVerification.EU_PID_STATUS -> revocation
            IsChainTrusted.SignatureVerification.EU_PUB_EAA -> issuance
            IsChainTrusted.SignatureVerification.EU_PUB_EAA_STATUS -> revocation
            IsChainTrusted.SignatureVerification.EU_WRPRC -> issuance
            IsChainTrusted.SignatureVerification.EU_WRPRC_STATUS -> revocation
            IsChainTrusted.SignatureVerification.EU_WRPAC -> issuance
            IsChainTrusted.SignatureVerification.EU_WRPAC_STATUS -> revocation
            IsChainTrusted.SignatureVerification.EU_MDL -> issuance
            IsChainTrusted.SignatureVerification.EU_MDL_STATUS -> revocation
        }
    }
    val result = profile.trustedEntities.serviceTypeIdentifiers.firstOrNull { it.endsWith(suffix) }
    return checkNotNull(result) { "Unable to find service type for $this with suffix $suffix" }
}
