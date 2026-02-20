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

import kotlin.collections.plus

/**
 * Default implementation of [IsChainTrustedForContextF]
 *
 * It provides the means to combine multiple instances of trusted anchors and validation logic.
 * Combinators:
 * - [plus] combines two sources into one
 * - [contraMap] transforms the chain representation
 *
 * @param CHAIN the type of the certificate chain to be validated
 * @param CTX the type of the verification context
 * @param TRUST_ANCHOR the type of the trust anchor to be used for validation
 */
public class IsChainTrustedForContext<in CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any>
private constructor(private val sources: Map<Set<CTX>, DefaultIsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR>>) :
    IsChainTrustedForContextF<CHAIN, CTX, TRUST_ANCHOR> {

    /**
     * The contexts supported
     */
    public val supportedContexts: Set<CTX> by lazy { sources.keys.flatten().toSet() }

    /**
     * A function for retrieving trust anchors for a given context.
     */
    public val getTrustAnchors: GetTrustAnchors<CTX, TRUST_ANCHOR> =
        @Suppress("ObjectLiteralToLambda")
        object : GetTrustAnchors<CTX, TRUST_ANCHOR> {
            // Do not convert to lamda. Crashes in JVM due to NonEmptyList being a value class
            override suspend fun invoke(query: CTX): NonEmptyList<TRUST_ANCHOR>? =
                findSource(query)?.getTrustAnchors(query)
        }

    override suspend fun invoke(
        chain: CHAIN,
        verificationContext: CTX,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        findSource(verificationContext)?.invoke(chain, verificationContext)

    private fun findSource(ctx: CTX): DefaultIsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR>? =
        sources.values.firstOrNull { ctx in it }

    /**
     * Combines two instances of [IsChainTrustedForContext] into one.
     * It is required that the two instances have disjoint sets of supported contexts.
     *
     * @param other the other source to combine with
     * @return a combined source
     * @throws IllegalArgumentException if the two instances have overlapping sets of supported contexts
     */
    @Throws(IllegalArgumentException::class)
    public infix operator fun plus(
        other: IsChainTrustedForContext<@UnsafeVariance CHAIN, CTX, TRUST_ANCHOR>,
    ): IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR> {
        val common = this.supportedContexts.intersect(other.supportedContexts)
        require(common.isEmpty()) { "Sources have overlapping queries: $common" }
        return IsChainTrustedForContext(this.sources + other.sources)
    }

    /**
     * Creates a new [IsChainTrustedForContext]
     * that applies the specified recovery logic in addition to the current
     *
     * Do not use this method unless you know what you are doing.
     *
     * @param recovery  a recovery function that generates alternative validations based on a
     *     [CTX] and a [CertificationChainValidation.NotTrusted] result.
     * @return a new instance that applies the specified recovery logic in addition to the current
     *         validation logic.
     */
    @SensitiveApi
    public fun recoverWith(
        recovery: (CTX, CertificationChainValidation.NotTrusted) -> DefaultIsChainTrustedForContext<@UnsafeVariance CHAIN, CTX, TRUST_ANCHOR>?,
    ): IsChainTrustedForContextF<CHAIN, CTX, TRUST_ANCHOR> =
        UnsafeIsChainTrustedForContext(this, recovery)

    public fun <C2 : Any> contraMap(transformation: (C2) -> CHAIN): IsChainTrustedForContext<C2, CTX, TRUST_ANCHOR> =
        IsChainTrustedForContext(
            sources.mapValues { (_, src) -> src.contraMap(transformation) },
        )

    public companion object {

        public fun <CHAIN : Any, CTX : Any, TA : Any> empty(): IsChainTrustedForContext<CHAIN, CTX, TA> =
            IsChainTrustedForContext(emptyMap())

        public operator fun <CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any> invoke(
            supportedContexts: Set<CTX>,
            getTrustAnchors: GetTrustAnchors<CTX, TRUST_ANCHOR>,
            validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
        ): IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR> {
            val source =
                DefaultIsChainTrustedForContext(supportedContexts, getTrustAnchors, validateCertificateChain)
            return IsChainTrustedForContext(mapOf(source.supportedContexts to source))
        }

        public fun <CHAIN : Any, CTX : Any, TA : Any> of(
            sources: Map<Set<CTX>, Pair<GetTrustAnchors<CTX, TA>, ValidateCertificateChain<CHAIN, TA>>>,
        ): IsChainTrustedForContext<CHAIN, CTX, TA> =
            sources.entries
                .map { (k, v) ->
                    invoke(k, v.first, v.second)
                }
                .fold(empty()) { acc, next -> acc + next }

        public fun <CHAIN : Any, CTX1 : Any, TA : Any, CTX2 : Any> transform(
            getTrustAnchors: GetTrustAnchors<CTX1, TA>,
            validateCertificateChain: ValidateCertificateChain<CHAIN, TA>,
            transformation: Map<CTX2, CTX1>,
        ): IsChainTrustedForContext<CHAIN, CTX2, TA> {
            val supportedCtxs = transformation.keys
            val transformedGetTrustAnchors =
                getTrustAnchors.contraMap<CTX1, TA, CTX2> { checkNotNull(transformation[it]) }
            return invoke(supportedCtxs, transformedGetTrustAnchors, validateCertificateChain)
        }
    }
}

/**
 * An [IsChainTrustedForContextF] for a set of supported contexts, that
 * - share the same [GetTrustAnchors] source
 * - share the same [ValidateCertificateChain]
 *
 * That's the elementary aggregation unit of [IsChainTrustedForContext].
 */
public class DefaultIsChainTrustedForContext<in CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any>(
    public val supportedContexts: Set<CTX>,
    public val getTrustAnchors: GetTrustAnchors<CTX, TRUST_ANCHOR>,
    public val validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
) : IsChainTrustedForContextF<CHAIN, CTX, TRUST_ANCHOR> {

    override suspend fun invoke(chain: CHAIN, verificationContext: CTX): CertificationChainValidation<TRUST_ANCHOR>? {
        if (!contains(verificationContext)) return null
        return getTrustAnchors(verificationContext)?.let { validateCertificateChain(chain, it) }
    }

    public operator fun contains(ctx: CTX): Boolean = ctx in supportedContexts

    public fun <C1 : Any> contraMap(transformation: (C1) -> CHAIN): DefaultIsChainTrustedForContext<C1, CTX, TRUST_ANCHOR> =
        DefaultIsChainTrustedForContext(supportedContexts, getTrustAnchors, validateCertificateChain.contraMap(transformation))

    public companion object {
        internal operator fun <CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any> invoke(
            other: Triple<Set<CTX>, GetTrustAnchors<CTX, TRUST_ANCHOR>, ValidateCertificateChain<CHAIN, TRUST_ANCHOR>>,
        ): DefaultIsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR> {
            val (supportedCtx, getTrustAnchors, validateCertificateChain) = other
            return DefaultIsChainTrustedForContext(supportedCtx, getTrustAnchors, validateCertificateChain)
        }
    }
}
