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

public fun interface IsChainTrusted<in CHAIN : Any> {

    public suspend operator fun invoke(chain: CHAIN): ValidateCertificateChain.Outcome

    public companion object {
        public operator fun <CHAIN : Any, TRUST_ANCHOR : Any> invoke(
            validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
            getTrustAnchors: suspend () -> List<TRUST_ANCHOR>,
        ): IsChainTrusted<CHAIN> =
            IsChainTrusted { chain ->
                val trustAnchors = getTrustAnchors()
                validateCertificateChain(chain, trustAnchors.toSet())
            }
    }
}

public inline fun <C1 : Any, C2 : Any> IsChainTrusted<C1>.contraMap(crossinline f: (C2) -> C1): IsChainTrusted<C2> =
    IsChainTrusted { chain -> invoke(f(chain)) }
