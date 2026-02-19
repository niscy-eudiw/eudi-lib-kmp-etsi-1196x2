/*
 * Copyright (c) 2026 European Commission
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
package eu.europa.ec.eudi.etsi1196x2.consultation

public typealias SourceAndValidate<C, CTX, TA> =
    Pair<GetTrustAnchors<CTX, TA>, ValidateCertificateChain<C, TA>>

private typealias Sources<CHAIN, CTX, TRUST_ANCHOR> = Map<Set<CTX>, SourceAndValidate<CHAIN, CTX, TRUST_ANCHOR>>

public class IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR>
internal constructor(private val sources: Sources<CHAIN, CTX, TRUST_ANCHOR>) :
    IsChainTrustedForContextF<CHAIN, CTX, TRUST_ANCHOR>
    where CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any {

    public constructor() : this(sources = emptyMap())

    internal constructor(
        supportedQueries: Set<CTX>,
        sourceAndValidate: SourceAndValidate<CHAIN, CTX, TRUST_ANCHOR>,
    ) : this(sources = mapOf(supportedQueries to sourceAndValidate))

    public constructor(
        supportedQueries: Set<CTX>,
        getTrustAnchors: GetTrustAnchors<CTX, TRUST_ANCHOR>,
        validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
    ) : this(supportedQueries = supportedQueries, sourceAndValidate = (getTrustAnchors to validateCertificateChain))

    override suspend fun invoke(
        chain: CHAIN,
        verificationContext: CTX,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        findSource(verificationContext)?.let { (trustOf, validate) ->
            trustOf(verificationContext)?.let { trustAnchors ->
                validate(chain, trustAnchors)
            }
        }

    public val supportedContexts: Set<CTX> by lazy { sources.keys.flatten().toSet() }

    public val getTrustAnchors: GetTrustAnchors<CTX, TRUST_ANCHOR> =
        GetTrustAnchors { verificationContext ->
            findSource(verificationContext)?.let { (trustOf, _) ->
                val trustAnchors = trustOf(verificationContext)
                trustAnchors
            }
        }

    private fun findSource(ctx: CTX): SourceAndValidate<CHAIN, CTX, TRUST_ANCHOR>? =
        sources.entries
            .find { (ctxs, _) -> ctx in ctxs }
            ?.value

    /**
     * Combines this source with an additional query set and [GetTrustAnchors].
     *
     * @param other a pair consisting of a set of supported queries and the corresponding [GetTrustAnchors]
     * @return a combined source
     * @throws IllegalArgumentException if the new queries overlap with existing ones
     */
    @Throws(IllegalArgumentException::class)
    public infix fun plus(
        other: Pair<Set<CTX>, SourceAndValidate<@UnsafeVariance CHAIN, @UnsafeVariance CTX, @UnsafeVariance TRUST_ANCHOR>>,
    ): IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR> {
        val (queries, sv) = other
        return this + IsChainTrustedForContext(queries, sv)
    }

    /**
     * Combines two sources into one.
     *
     * This operation merges the routing tables of both sources. It requires that the sets of
     * supported queries in both sources are completely disjoint to ensure unambiguous routing.
     *
     * @param other the other source to combine with
     * @return a combined source
     * @throws IllegalArgumentException if there are overlapping queries between the two sources
     */
    @Throws(IllegalArgumentException::class)
    public infix operator fun plus(
        other: IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR>,
    ): IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR> {
        val common = this.supportedContexts.intersect(other.supportedContexts)
        require(common.isEmpty()) { "Sources have overlapping queries: $common" }
        return IsChainTrustedForContext(this.sources + other.sources)
    }

//    /**
//     * Creates a new [IsChainTrustedForContext]
//     * that applies the specified recovery logic in addition to the current
//     *
//     * Do not use this method unless you know what you are doing.
//     *
//     * @param recovery  a recovery function that generates alternative validations based on a
//     *     [CTX] and a [CertificationChainValidation.NotTrusted] result.
//     * @return a new instance that applies the specified recovery logic in addition to the current
//     *         validation logic.
//     */
//    @SensitiveApi
//    public fun recoverWith(
//        recovery: (CertificationChainValidation.NotTrusted) -> GetTrustAnchorsForSupportedQueries<CTX, @UnsafeVariance TRUST_ANCHOR>?,
//    ): IsChainTrustedForContextF<CHAIN, CTX, TRUST_ANCHOR> =
//        UnsafeIsChainTrustedForContext(this) { notTrusted ->
//            recovery(notTrusted)?.let {
//                IsChainTrustedForContext(validateCertificateChain, it)
//            }
//        }

    public fun <C2 : Any> contraMap(transformation: (C2) -> CHAIN): IsChainTrustedForContext<C2, CTX, TRUST_ANCHOR> {
        return IsChainTrustedForContext(
            sources.mapValues { (_, sourceAndValidator) ->
                val (source, validator) = sourceAndValidator
                source to validator.contraMap(transformation)
            },
        )
    }

    public companion object {

        public fun <CHAIN : Any, CTX1 : Any, TA : Any, CTX2 : Any> transform(
            getTrustAnchors: GetTrustAnchors<CTX1, TA>,
            validateCertificateChain: ValidateCertificateChain<CHAIN, TA>,
            transformation: Map<CTX2, CTX1>,
        ): IsChainTrustedForContext<CHAIN, CTX2, TA> {
            val updatedGetTrustAnchors =
                getTrustAnchors.contraMap<CTX1, TA, CTX2> { checkNotNull(transformation[it]) }
            return IsChainTrustedForContext(
                supportedQueries = transformation.keys,
                sourceAndValidate = updatedGetTrustAnchors to validateCertificateChain,
            )
        }
    }
}
