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
package eu.europa.ec.eudi.etsi119602.profile

import eu.europa.ec.eudi.etsi119602.URI
import kotlinx.coroutines.flow.first
import java.security.cert.*

enum class SignatureVerification {
    EU_WIA,
    EU_WUA,
    EU_WUA_STATUS,
    EU_PID,
    EU_PID_STATUS,
    EU_PUB_EAA,
    EU_PUB_EAA_STATUS,
    EU_WRPRC,
    EU_WRPRC_STATUS,
    EU_WRPAC,
    EU_WRPAC_STATUS,
}

val SignatureVerification.euProfile: EUListOfTrustedEntitiesProfile
    get() = when (this) {
        SignatureVerification.EU_WIA,
        SignatureVerification.EU_WUA,
        SignatureVerification.EU_WUA_STATUS,
        -> EUWalletProvidersList

        SignatureVerification.EU_PID,
        SignatureVerification.EU_PID_STATUS,
        -> EUPIDProvidersList

        SignatureVerification.EU_PUB_EAA,
        SignatureVerification.EU_PUB_EAA_STATUS,
        -> EUPubEAAProvidersList

        SignatureVerification.EU_WRPRC,
        SignatureVerification.EU_WRPRC_STATUS,
        -> EUWRPRCProvidersList

        SignatureVerification.EU_WRPAC,
        SignatureVerification.EU_WRPAC_STATUS,
        -> EUWRPACProvidersList
    }

fun SignatureVerification.svcType(): URI {
    val suffix = run {
        val issuance = "Issuance"
        val revocation = "Revocation"
        when (this) {
            SignatureVerification.EU_WIA -> issuance
            SignatureVerification.EU_WUA -> issuance
            SignatureVerification.EU_WUA_STATUS -> revocation
            SignatureVerification.EU_PID -> issuance
            SignatureVerification.EU_PID_STATUS -> revocation
            SignatureVerification.EU_PUB_EAA -> issuance
            SignatureVerification.EU_PUB_EAA_STATUS -> revocation
            SignatureVerification.EU_WRPRC -> issuance
            SignatureVerification.EU_WRPRC_STATUS -> revocation
            SignatureVerification.EU_WRPAC -> issuance
            SignatureVerification.EU_WRPAC_STATUS -> revocation
        }
    }
    return euProfile.trustedEntities.serviceTypeIdentifiers.first { it.endsWith(suffix) }
}

fun interface IsChainTrusted {
    suspend operator fun invoke(
        chain: List<X509Certificate>,
        signatureVerification: SignatureVerification,
    ): Outcome

    sealed interface Outcome {
        data object Trusted : Outcome
        data class Untrusted(val cause: Throwable) : Outcome
    }
}
