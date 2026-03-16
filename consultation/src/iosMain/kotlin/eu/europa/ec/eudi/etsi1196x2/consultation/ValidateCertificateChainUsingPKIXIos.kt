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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Security.SecCertificateRef

/**
 * Configuration for iOS PKIX validation.
 *
 * @property revocationCheckingEnabled whether to enable certificate revocation checking
 * @property networkFetchAllowed whether to allow network fetching for validation (e.g., OCSP, CRL)
 */
public data class IosPKIXConfiguration(
    var revocationCheckingEnabled: Boolean = true,
    var networkFetchAllowed: Boolean = true,
)

/**
 * Wrapper for iOS trust anchor (SecCertificate).
 */
@OptIn(ExperimentalForeignApi::class)
public data class IosTrustAnchor(val certificate: SecCertificateRef)

/**
 * iOS-specific implementation of [ValidateCertificateChainUsingPKIX] using Security.framework.
 *
 * This implementation uses SecTrust APIs to perform PKIX validation:
 * - Creates SecPolicy for X.509 basic validation
 * - Configures SecTrust with custom trust anchors
 * - Evaluates trust using SecTrustEvaluateWithError
 *
 * @param dispatcher the coroutine dispatcher to use for validating certificate chains.
 *        Defaults to [Dispatchers.Default]
 * @param customization customization for PKIX parameters.
 *        Defaults to [DEFAULT_CUSTOMIZATION]
 */
@OptIn(ExperimentalForeignApi::class)
public class ValidateCertificateChainUsingPKIXIos(
    dispatcher: CoroutineDispatcher = DEFAULT_DISPATCHER,
    private val customization: IosPKIXConfiguration.() -> Unit = DEFAULT_CUSTOMIZATION,
) : ValidateCertificateChainUsingPKIX<List<SecCertificateRef>, IosTrustAnchor> {

    private val coroutineContext = CoroutineName(name = "ValidateCertificateChainIos") + dispatcher

    override suspend fun invoke(
        chain: List<SecCertificateRef>,
        trustAnchors: NonEmptyList<IosTrustAnchor>,
    ): CertificationChainValidation<IosTrustAnchor> =
        withContext(coroutineContext) {
            require(chain.isNotEmpty()) { "Chain must not be empty" }

            try {
                // TODO: Implement SecTrust-based validation
                // Steps:
                // 1. Create SecPolicy using SecPolicyCreateBasicX509()
                // 2. Create SecTrust using SecTrustCreateWithCertificates()
                // 3. Set trust anchors using SecTrustSetAnchorCertificates()
                // 4. Configure options based on customization
                // 5. Evaluate using SecTrustEvaluateWithError()
                // 6. Extract matched trust anchor and return result

                // Placeholder: Return NotTrusted for now
                CertificationChainValidation.NotTrusted(
                    NotImplementedError("iOS PKIX validation not yet implemented"),
                )
            } catch (e: Exception) {
                CertificationChainValidation.NotTrusted(e)
            }
        }

    public companion object {

        /**
         * Default customization for PKIX parameters.
         * Enables revocation checking by default as per RFC 5280 Section 6.3.
         */
        public val DEFAULT_CUSTOMIZATION: IosPKIXConfiguration.() -> Unit = {
            revocationCheckingEnabled = true
            networkFetchAllowed = true
        }

        /**
         * Default coroutine dispatcher for validating certificate chains.
         * Points to [Dispatchers.Default]
         */
        public val DEFAULT_DISPATCHER: CoroutineDispatcher = Dispatchers.Default

        /**
         * Default implementation of [ValidateCertificateChainUsingPKIXIos] for iOS platform.
         * Uses [DEFAULT_DISPATCHER] and [DEFAULT_CUSTOMIZATION].
         */
        public val Default: ValidateCertificateChainUsingPKIXIos
            get() = ValidateCertificateChainUsingPKIXIos(
                customization = DEFAULT_CUSTOMIZATION,
                dispatcher = DEFAULT_DISPATCHER,
            )
    }
}
