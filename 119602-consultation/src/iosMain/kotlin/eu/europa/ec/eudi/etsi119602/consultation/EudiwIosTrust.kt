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
package eu.europa.ec.eudi.etsi119602.consultation

import eu.europa.ec.eudi.etsi119602.Uri
import eu.europa.ec.eudi.etsi119602.consultation.eu.EUMDLProvidersListSpec
import eu.europa.ec.eudi.etsi119602.consultation.eu.ServiceDigitalIdentityCertificateType
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificationChainValidation
import eu.europa.ec.eudi.etsi1196x2.consultation.ComposeChainTrust
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchors
import eu.europa.ec.eudi.etsi1196x2.consultation.NonEmptyList
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChain
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingDirectTrustIos
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIXIos
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.validator
import platform.Foundation.NSData

/**
 * Swift-friendly outcome of a chain validation. Flattens the generic
 * [CertificationChainValidation] sealed type so Swift consumers don't need generic casts.
 *
 * @param isTrusted whether the chain is trusted for the requested context
 * @param matchedAnchor on success, the DER of the trust anchor that validated the chain
 * @param failureReason on failure, a human-readable reason
 */
public data class IosValidationResult(
    val isTrusted: Boolean,
    val matchedAnchor: NSData?,
    val failureReason: String?,
)

/**
 * The chain-validation strategy for [EudiwIosTrust.usingBundledAnchors].
 */
public enum class BundledAnchorMethod {
    /**
     * Full PKIX path validation via Apple `SecTrust`: the presented chain must build to one of the
     * bundled anchors. Use when the bundled anchors are **CA** certificates (roots/intermediates).
     */
    PKIX,

    /**
     * Direct trust (certificate pinning): the chain's leaf must byte-match one of the bundled
     * anchors. Use when the bundled anchors are the **exact end-entity** certificates to pin.
     */
    DIRECT_TRUST,
}

/**
 * One-call assembly of the iOS LoTE-based trust validator, intended for Swift consumers.
 *
 * It wires together: a Darwin [io.ktor.client.HttpClient] → [DownloadSingleLoTE] →
 * [LoadLoTEAndPointers] → [ProvisionTrustAnchorsFromLoTEs.eudiwIos] → [ComposeChainTrust.nonCached],
 * and exposes Swift-friendly entry points that avoid Kotlin value classes ([Uri], [NonEmptyList])
 * at the boundary — LoTE locations are passed as plain URL strings and trust anchors come back as
 * a plain list of DER [NSData].
 */
public object EudiwIosTrust {

    /**
     * Builds a non-cached validator for the given per-context LoTE download URLs. Pass `null` for
     * contexts you do not need. Suitable for low-concurrency use (e.g. one validation per screen).
     *
     * @param verifyJwtSignature verifies each downloaded LoTE JWT — supply a real implementation in
     *        production; this is a required, explicit choice so trust is never silently bypassed.
     */
    /**
     * The use-case key under which the mDL list is registered (in both the locations and the
     * service-type metadata). Swift consumers select the mDL context with
     * `VerificationContextEAA(useCase: EudiwIosTrust.shared.mdlUseCase)`.
     */
    public val mdlUseCase: String get() = "mdl"

    public fun nonCached(
        pidProvidersUrl: String?,
        walletProvidersUrl: String?,
        wrpacProvidersUrl: String?,
        wrprcProvidersUrl: String?,
        pubEaaProvidersUrl: String?,
        qeaProvidersUrl: String?,
        mdlProvidersUrl: String?,
        verifyJwtSignature: VerifyJwtSignature,
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> {
        val locations = SupportedLists(
            pidProviders = pidProvidersUrl?.let(::Uri),
            walletProviders = walletProvidersUrl?.let(::Uri),
            wrpacProviders = wrpacProvidersUrl?.let(::Uri),
            wrprcProviders = wrprcProvidersUrl?.let(::Uri),
            pubEaaProviders = pubEaaProvidersUrl?.let(::Uri),
            qeaProviders = qeaProvidersUrl?.let(::Uri),
            eaaProviders = buildMap {
                mdlProvidersUrl?.let { put(mdlUseCase, Uri(it)) }
            },
        )

        // The baseline EU metadata has no mDL entry, so add one when an mDL URL is supplied.
        // mDL uses a null end-entity profile (the advertised DIGIT lists do not satisfy the strict
        // ETSI profiles), so its validation is pure direct trust / PKIX. Mirrors DIGIT.SVC_TYPE_PER_CTX.
        val svcTypePerCtx = SupportedLists.eu().let { baseline ->
            if (mdlProvidersUrl != null) baseline.copy(eaaProviders = mapOf(mdlUseCase to mdlMeta())) else baseline
        }

        val httpClient = IosLoTEHttpClient.create()
        val downloadSingleLoTE = DownloadSingleLoTE(httpClient)
        val loadLoTEAndPointers = LoadLoTEAndPointers(
            constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
            verifyJwtSignature = verifyJwtSignature,
            loadLoTE = downloadSingleLoTE,
        )
        return ProvisionTrustAnchorsFromLoTEs
            .eudiwIos(loadLoTEAndPointers = loadLoTEAndPointers, svcTypePerCtx = svcTypePerCtx)
            .nonCached(locations)
    }

    /**
     * Builds a validator backed by **bundled / hardcoded** certificate anchors instead of a
     * downloaded LoTE — no network, JWT, or LoTE is involved. This is the iOS counterpart of the
     * JVM `IsChainTrustedForContext.usingKeyStore(...)`.
     *
     * Pass the DER anchors ([NSData]) shipped with the app per context; pass `null` for contexts you
     * do not need. The mDL context is registered under [mdlUseCase] (i.e. `VerificationContext.EAA("mdl")`).
     * The returned validator uses the same [trustAnchors] / [validate] entry points as [nonCached].
     *
     * No ETSI end-entity profile is applied — these contexts validate purely by [method] — so bundled
     * CA anchors are not rejected by the strict EUDI end-entity profiles.
     *
     * @param method [BundledAnchorMethod.PKIX] for chain-to-anchor path validation (anchors are CA
     *        certificates) or [BundledAnchorMethod.DIRECT_TRUST] for leaf pinning (anchors are the
     *        exact end-entity certificates).
     */
    public fun usingBundledAnchors(
        pidAnchors: List<NSData>?,
        walletAnchors: List<NSData>?,
        wrpacAnchors: List<NSData>?,
        wrprcAnchors: List<NSData>?,
        pubEaaAnchors: List<NSData>?,
        qeaAnchors: List<NSData>?,
        mdlAnchors: List<NSData>?,
        method: BundledAnchorMethod,
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> {
        val anchorsByContext: Map<VerificationContext, List<NSData>> = buildMap {
            pidAnchors?.let { put(VerificationContext.PID, it) }
            walletAnchors?.let { put(VerificationContext.WalletProviderAttestation, it) }
            wrpacAnchors?.let { put(VerificationContext.WalletRelyingPartyAccessCertificate, it) }
            wrprcAnchors?.let { put(VerificationContext.WalletRelyingPartyRegistrationCertificate, it) }
            pubEaaAnchors?.let { put(VerificationContext.PubEAA, it) }
            qeaAnchors?.let { put(VerificationContext.QEAA, it) }
            mdlAnchors?.let { put(VerificationContext.EAA(mdlUseCase), it) }
        }

        val getTrustAnchors = GetTrustAnchors<VerificationContext, NSData> { ctx ->
            anchorsByContext[ctx]?.let { NonEmptyList.nelOrNull(it) }
        }

        val validateChain: ValidateCertificateChain<List<NSData>, NSData> = when (method) {
            BundledAnchorMethod.PKIX -> ValidateCertificateChainUsingPKIXIos()
            BundledAnchorMethod.DIRECT_TRUST -> ValidateCertificateChainUsingDirectTrustIos
        }

        return ComposeChainTrust(getTrustAnchors.validator(anchorsByContext.keys, validateChain))
    }

    private fun mdlMeta(): LotEMeta<VerificationContext> = LotEMeta(
        svcTypePerCtx = buildMap {
            put(
                VerificationContext.EAA(mdlUseCase),
                LotEMeta.SvcAndEEProfile(Uri(EUMDLProvidersListSpec.SVC_TYPE_ISSUANCE), null),
            )
            put(
                VerificationContext.EAAStatus(mdlUseCase),
                LotEMeta.SvcAndEEProfile(Uri(EUMDLProvidersListSpec.SVC_TYPE_REVOCATION), null),
            )
        },
        serviceDigitalIdentityCertificateType = ServiceDigitalIdentityCertificateType.EndEntityOrCA,
    )

    /**
     * Resolves the trust anchors (DER bytes) for [context] using [validator], as a plain list.
     * Returns an empty list if the validator has no anchors for that context. Suspends while the
     * underlying LoTE is fetched/parsed.
     *
     * `@Throws` is required: without it, any failure (network, JSON parse, cancellation) would be
     * an unhandled Kotlin/Native exception that crashes the process instead of surfacing to Swift
     * as a catchable error.
     */
    @Throws(Throwable::class)
    public suspend fun trustAnchors(
        validator: ComposeChainTrust<List<NSData>, VerificationContext, NSData>,
        context: VerificationContext,
    ): List<NSData> =
        validator.getTrustAnchors.invoke(context)?.list ?: emptyList()

    /**
     * Validates a DER-encoded certificate [chain] for [context] using [validator].
     *
     * The chain is the leaf-first list of DER certificates from a received credential/attestation
     * (e.g. the `x5c` of a PID or mDL). Returns an [IosValidationResult]; a `null` underlying
     * result (context not supported by the validator) is reported as not-trusted.
     *
     * `@Throws` for the same reason as [trustAnchors]: validation fetches/parses the LoTE and could
     * otherwise crash the process on failure instead of surfacing a Swift error.
     */
    @Throws(Throwable::class)
    public suspend fun validate(
        validator: ComposeChainTrust<List<NSData>, VerificationContext, NSData>,
        chain: List<NSData>,
        context: VerificationContext,
    ): IosValidationResult =
        when (val outcome = validator.invoke(chain, context)) {
            null -> IosValidationResult(
                isTrusted = false,
                matchedAnchor = null,
                failureReason = "No validator configured for this context",
            )

            is CertificationChainValidation.Trusted -> IosValidationResult(
                isTrusted = true,
                matchedAnchor = outcome.trustAnchor,
                failureReason = null,
            )

            is CertificationChainValidation.NotTrusted -> IosValidationResult(
                isTrusted = false,
                matchedAnchor = null,
                failureReason = outcome.cause.message ?: "Chain is not trusted",
            )
        }
}
