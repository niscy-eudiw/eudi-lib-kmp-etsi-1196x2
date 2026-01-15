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
package eu.europa.ec.eudi.etsi119602.profile

import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntities
import eu.europa.ec.eudi.etsi119602.ServiceDigitalIdentity
import eu.europa.ec.eudi.etsi119602.ServiceInformation
import eu.europa.ec.eudi.etsi119602.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


public interface EULens {

    public fun ListOfTrustedEntities.digitalIdentities(predicate: suspend (ServiceInformation) -> Boolean = { _ -> true }): Flow<ServiceDigitalIdentity>

    public fun ListOfTrustedEntities.digitalIdentitiesOfIssuanceServices(): Flow<ServiceDigitalIdentity>

    public fun ListOfTrustedEntities.digitalIdentitiesOfRevocationServices(): Flow<ServiceDigitalIdentity>

    public companion object {
        public operator fun invoke(profile: ListOfTrustedEntitiesProfile): EULens =
            EULens(profile)
    }
}

private fun EULens(
    profile: ListOfTrustedEntitiesProfile
): EULens = object : EULens {

    public override fun ListOfTrustedEntities.digitalIdentities(predicate: suspend (ServiceInformation) -> Boolean): Flow<ServiceDigitalIdentity> =
        with(profile) {
            ensureProfile()
            flow {
                entities.orEmpty().forEach { entity ->
                    entity.services.forEach { service ->
                        if (predicate(service.information)) {
                            emit(service.information.digitalIdentity)
                        }
                    }
                }
            }
        }

    override fun ListOfTrustedEntities.digitalIdentitiesOfIssuanceServices(): Flow<ServiceDigitalIdentity> =
        digitalIdentitiesForServiceType(profile.trustedEntities.issuanceServiceTypeIdentifier)

    override fun ListOfTrustedEntities.digitalIdentitiesOfRevocationServices(): Flow<ServiceDigitalIdentity> =
        digitalIdentitiesForServiceType(profile.trustedEntities.revocationServiceTypeIdentifier)

    private fun ListOfTrustedEntities.digitalIdentitiesForServiceType(serviceTypeIdentifier: URI): Flow<ServiceDigitalIdentity> =
        digitalIdentities { it.typeIdentifier == serviceTypeIdentifier }


}
