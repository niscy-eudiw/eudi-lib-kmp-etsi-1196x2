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

import eu.europa.ec.eudi.etsi119602.PKIObject

/**
 * A way to create a trust anchor from a [PKIObject]
 *
 * @param TRUST_ANCHOR the type representing a trust anchor
 */
public typealias TrustAnchorCreator<TRUST_ANCHOR> = (PKIObject) -> TRUST_ANCHOR

/**
 * Represents a way to obtain a [TrustAnchorCreator] for a [VerificationContext]
 * @param TRUST_ANCHOR the type representing a trust anchor
 */
public fun interface TrustAnchorCreatorByVerificationContext<out TRUST_ANCHOR : Any> {
    /**
     * A way to obtain a [TrustAnchorCreator] for a [VerificationContext]
     * @param verificationContext the verification context
     * @return a trust anchor creator
     */
    public operator fun invoke(verificationContext: VerificationContext): TrustAnchorCreator<TRUST_ANCHOR>
}
