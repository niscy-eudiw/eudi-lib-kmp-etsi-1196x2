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

import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntities
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

fun verifyJPLearningCredentialSignature(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    trustAnchorCreator: TrustAnchorCreator<TrustAnchor> = TrustAnchorCreator.jvm(),
    lote: ListOfTrustedEntities,
): IsChainTrusted<List<X509Certificate>, TrustSource.LoTE> =
    IsChainTrusted.usingLoTEs(validateCertificateChain, trustAnchorCreator) { _ -> lote }
