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

public fun interface IsChainTrusted<in CHAIN : Any, in TRUST_SOURCE : TrustSource> {

    public sealed interface Outcome {
        public data object Trusted : Outcome
        public data class NotTrusted(val cause: Throwable) : Outcome
        public data object TrustSourceNotFound : Outcome
    }

    public suspend operator fun invoke(chain: CHAIN, trustSource: TRUST_SOURCE): Outcome

    public companion object {
        public fun <CHAIN : Any, TRUST_ANCHOR : Any> usingLoTEs(
            validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
            trustAnchorCreator: TrustAnchorCreator<TRUST_ANCHOR>,
            getLatestListOfTrustedEntitiesByType: GetLatestListOfTrustedEntitiesByType,
        ): IsChainTrusted<CHAIN, TrustSource.LoTE> = IsChainTrusted { chain, trustSource ->
            when (val lote = getLatestListOfTrustedEntitiesByType(trustSource.loteType)) {
                null -> Outcome.TrustSourceNotFound
                else -> with(trustAnchorCreator) {
                    val trustAnchors = lote.trustAnchorsOfType(trustSource.serviceType)
                    when (val outcome = validateCertificateChain(chain, trustAnchors.toSet())) {
                        is ValidateCertificateChain.Outcome.Trusted -> Outcome.Trusted
                        is ValidateCertificateChain.Outcome.Untrusted -> Outcome.NotTrusted(outcome.cause)
                    }
                }
            }
        }
    }
}
