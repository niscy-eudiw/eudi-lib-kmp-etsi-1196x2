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
package eu.europa.ec.eudi.etsi1196x2.signum

import at.asitplus.signum.indispensable.pki.X509Certificate
import eu.europa.ec.eudi.etsi119602.ServiceDigitalIdentity
import eu.europa.ec.eudi.etsi119602.consultation.ContinueOnProblem
import eu.europa.ec.eudi.etsi119602.consultation.LoadLoTEAndPointers
import eu.europa.ec.eudi.etsi119602.consultation.LotEMeta
import eu.europa.ec.eudi.etsi119602.consultation.ProvisionTrustAnchorsFromLoTEs
import eu.europa.ec.eudi.etsi119602.consultation.eu
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingDirectTrust
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIX
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfileValidator

/**
 * Creates a [ProvisionTrustAnchorsFromLoTEs] instance configured for EU Digital Identity Wallet
 * using Signum cross-platform certificate validation.
 *
 * This function provides a Signum-based provisioner similar to [eudiwJvm]
 * but using Signum's cross-platform X509Certificate and crypto primitives, making it suitable for
 * iOS, JVM, and Android platforms.
 *
 * **Use cases:**
 * - iOS wallet applications using native Swift/Kotlin
 * - Cross-platform wallet SDKs
 * - Testing and development on all platforms
 *
 * **Example:**
 * ```kotlin
 * // Create HTTP client and file cache
 * val httpClient = createHttpClient()
 * val fileStore = LoTEFileStore(cacheDirectory = Path("/tmp/lote-cache"))
 *
 * // Create LoTE loader with caching
 * val loadLoTE = LoadSingleLoTEWithFileCache(
 *     fileStore = fileStore,
 *     downloadSingleLoTE = DownloadSingleLoTE(httpClient),
 *     fileCacheExpiration = 24.hours
 * )
 *
 * // Configure trust list URLs
 * val loteLocations = SupportedLists(
 *     pidProviders = "https://trust.tech.ec.europa.eu/lists/eudiw/pid-providers.json",
 *     walletProviders = "https://trust.tech.ec.europa.eu/lists/eudiw/wallet-providers.json",
 *     wrpacProviders = "https://trust.tech.ec.europa.eu/lists/eudiw/wrpac-providers.json"
 * )
 *
 * // Create provisioner with Signum validators
 * val provisioner = ProvisionTrustAnchorsFromLoTEs.eudiwSignum(
 *     loadLoTEAndPointers = LoadLoTEAndPointers(
 *         constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
 *         verifyJwtSignature = NotValidating,
 *         loadLoTE = loadLoTE
 *     )
 * )
 *
 * // Get validator for all contexts
 * val validator = provisioner.nonCached(loteLocations)
 *
 * // Validate a PID certificate chain
 * val pidChain: List<X509Certificate> = parsePIDCredential(pidJWT)
 * when (val result = validator.isChainTrusted(pidChain, VerificationContext.PID)) {
 *     is CertificationChainValidation.Trusted -> println("PID is trusted")
 *     is CertificationChainValidation.NotTrusted -> println("PID not trusted: ${result.cause}")
 * }
 * ```
 *
 * @param loadLoTEAndPointers Loader for fetching LoTEs and their pointers from URLs
 * @param svcTypePerCtx Service type metadata per verification context (defaults to EU standard contexts)
 * @param continueOnProblem Strategy for handling errors during LoTE loading
 * @param pkix PKIX validator for CA certificate chains (defaults to Signum-based validator)
 *
 * @return Configured provisioner for EUDIW verification contexts using Signum
 *
 * @see ProvisionTrustAnchorsFromLoTEs
 * @see eu.europa.ec.eudi.etsi1196x2.signum.ValidateChainCertificateUsingPKIXSignum
 * @see eu.europa.ec.eudi.etsi1196x2.signum.ValidateChainCertificateUsingDirectTrustSignum
 */
public fun ProvisionTrustAnchorsFromLoTEs.Companion.eudiwSignum(
    loadLoTEAndPointers: LoadLoTEAndPointers,
    svcTypePerCtx: SupportedLists<LotEMeta<VerificationContext>> = SupportedLists.eu(),
    continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    pkix: ValidateCertificateChainUsingPKIX<List<X509Certificate>, X509Certificate> =
        ValidateChainCertificateUsingPKIXSignum(),
): ProvisionTrustAnchorsFromLoTEs<List<X509Certificate>, VerificationContext, X509Certificate, X509Certificate> =
    signum(
        loadLoTEAndPointers = loadLoTEAndPointers,
        svcTypePerCtx = svcTypePerCtx,
        createTrustAnchors = ::defaultCreateTrustAnchorsSignum,
        continueOnProblem = continueOnProblem,
        pkix = pkix,
    )

/**
 * Creates a generic [ProvisionTrustAnchorsFromLoTEs] instance using Signum cross-platform validation.
 *
 * This is the low-level factory function allowing full customization of context types and trust anchor creation.
 * Most users should use [eudiwSignum] instead.
 *
 * @param CTX The type representing a verification context
 *
 * @param loadLoTEAndPointers Loader for fetching LoTEs and their pointers
 * @param svcTypePerCtx Service type metadata per context
 * @param createTrustAnchors Function to convert ServiceDigitalIdentity to trust anchors
 * @param continueOnProblem Error handling strategy
 * @param pkix PKIX validator for certificate chains
 *
 * @return Configured provisioner using Signum validators
 */
public fun <CTX : Any> ProvisionTrustAnchorsFromLoTEs.Companion.signum(
    loadLoTEAndPointers: LoadLoTEAndPointers,
    svcTypePerCtx: SupportedLists<LotEMeta<CTX>>,
    createTrustAnchors: (ServiceDigitalIdentity) -> List<X509Certificate> = ::defaultCreateTrustAnchorsSignum,
    continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    pkix: ValidateCertificateChainUsingPKIX<List<X509Certificate>, X509Certificate> =
        ValidateChainCertificateUsingPKIXSignum(),
): ProvisionTrustAnchorsFromLoTEs<List<X509Certificate>, CTX, X509Certificate, X509Certificate> =
    ProvisionTrustAnchorsFromLoTEs(
        loadLoTEAndPointers = loadLoTEAndPointers,
        svcTypePerCtx = svcTypePerCtx,
        createTrustAnchors = createTrustAnchors,
        continueOnProblem = continueOnProblem,
        directTrust = createSignumDirectTrust(),
        pkix = pkix,
        certificateProfileValidator = CertificateProfileValidator(SignumCertificateOperations()),
        endEntityCertificateOf = { checkNotNull(it.firstOrNull()) { "Chain cannot be empty" } },
    )

/**
 * Creates a direct trust validator for Signum certificates.
 *
 * This compares certificates by their DER-encoded bytes, which is the most reliable
 * cross-platform comparison method.
 */
private fun createSignumDirectTrust(): ValidateCertificateChainUsingDirectTrust<List<X509Certificate>, X509Certificate, ByteArray> =
    ValidateCertificateChainUsingDirectTrust(
        headCertificateId = { chain ->
            val head = chain.firstOrNull()
            checkNotNull(head) { "Chain cannot be empty" }
            head.encodeToDer()
        },
        trustToCertificateId = { trustAnchor ->
            trustAnchor.encodeToDer()
        },
    )

/**
 * Default trust anchor creation from ServiceDigitalIdentity using Signum X509Certificate.
 *
 * Extracts X.509 certificates from the service digital identity and parses them using
 * Signum's cross-platform X509Certificate parser.
 *
 * @param serviceDigitalIdentity Service identity containing X.509 certificates
 * @return List of parsed X509Certificate trust anchors
 */
public fun defaultCreateTrustAnchorsSignum(serviceDigitalIdentity: ServiceDigitalIdentity): List<X509Certificate> =
    serviceDigitalIdentity.x509Certificates.orEmpty().mapNotNull { pkiObject ->
        try {
            // Parse DER-encoded certificate bytes using Signum
            X509Certificate.decodeFromDer(pkiObject.value)
        } catch (e: Exception) {
            // Log parsing error but continue with other certificates
            // In production, you may want to use proper logging
            println("Warning: Failed to parse certificate: ${e.message}")
            null
        }
    }
