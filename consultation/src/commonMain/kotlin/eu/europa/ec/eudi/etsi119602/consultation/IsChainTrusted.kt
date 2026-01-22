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

    public suspend operator fun invoke(chain: CHAIN, trustSource: TRUST_SOURCE): ValidateCertificateChain.Outcome

    public companion object {
        public fun <CHAIN : Any, TRUST_ANCHOR : Any> usingLoTEs(
            validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
            trustAnchorCreator: TrustAnchorCreator<TRUST_ANCHOR>,
            getLatestListOfTrustedEntitiesByType: GetLatestListOfTrustedEntitiesByType,
        ): IsChainTrusted<CHAIN, TrustSource.LoTE> = IsChainTrusted { chain, trustSource ->
            when (val lote = getLatestListOfTrustedEntitiesByType(trustSource.loteType)) {
                null -> ValidateCertificateChain.Outcome.Untrusted(IllegalStateException("No Lote found for ${trustSource.loteType}"))
                else -> with(trustAnchorCreator) {
                    val trustAnchors = lote.trustAnchorsOfType(trustSource.serviceType)
                    validateCertificateChain(chain, trustAnchors.toSet())
                }
            }
        }
    }
}
