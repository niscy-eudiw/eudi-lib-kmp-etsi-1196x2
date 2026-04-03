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

import eu.europa.ec.eudi.etsi1196x2.consultation.SensitiveApi

/**
 * A [VerifyJwtSignature] implementation that skips JWT signature verification.
 *
 * **WARNING: DO NOT USE IN PRODUCTION!**
 *
 * This is intended for:
 * - Testing and development
 * - Acceptance/staging environments where trust lists are not yet signed
 * - Scenarios where signature verification is handled externally
 *
 * In production, you must implement proper JWT signature verification using
 * the appropriate cryptographic libraries and trusted public keys.
 */
@SensitiveApi
public object NotValidating : VerifyJwtSignature {
    override suspend fun invoke(jwt: String): VerifyJwtSignature.Outcome =
        VerifyJwtSignature.Outcome.Verified(jwt)
}