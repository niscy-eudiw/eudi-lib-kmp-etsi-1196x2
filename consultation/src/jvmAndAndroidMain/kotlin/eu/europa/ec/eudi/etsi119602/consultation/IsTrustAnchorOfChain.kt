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

import eu.europa.ec.eudi.etsi119602.ServiceDigitalIdentity
import eu.europa.ec.eudi.etsi119602.x509CertificateOf
import java.security.Provider
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.collections.map
import kotlin.collections.orEmpty

public class IsTrustAnchorOfChain private constructor(
    private val certificateFactory: () -> CertificateFactory,
    private val certPathValidator: () -> CertPathValidator,
    private val chain: List<X509Certificate>,
) : Predicate<ServiceDigitalIdentity> {

    public constructor(chain: List<X509Certificate>) : this(
        { CertificateFactory.getInstance(X_509) },
        { CertPathValidator.getInstance(PKIX) },
        chain,
    )

    public constructor(chain: List<X509Certificate>, provider: String) : this(
        { CertificateFactory.getInstance(X_509, provider) },
        { CertPathValidator.getInstance(PKIX, provider) },
        chain,
    )

    public constructor(chain: List<X509Certificate>, provider: Provider) : this(
        { CertificateFactory.getInstance(X_509, provider) },
        { CertPathValidator.getInstance(PKIX, provider) },
        chain,
    )

    override suspend operator fun invoke(value: ServiceDigitalIdentity): Boolean {
        val crtFactory = certificateFactory()
        val certPath = crtFactory.generateCertPath(chain)
        val pkixParameters = run {
            val anchors =
                value.x509Certificates.orEmpty().map { pkiObj ->
                    val anchorCert = crtFactory.x509CertificateOf(pkiObj)
                    TrustAnchor(anchorCert, null)
                }
            PKIXParameters(anchors.toSet())
        }
        return try {
            certPathValidator().validate(certPath, pkixParameters) != null
        } catch (_: Throwable) {
            false
        }
    }
    internal companion object {
        internal const val X_509 = "X.509"
        internal const val PKIX = "PKIX"
    }
}
