package eu.europa.ec.eudi.etsi119602

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class ListOfTrustedEntities(
    @SerialName(ETSI19602.LIST_AND_SCHEME_INFORMATION) @Required val schemeInformation: ListAndSchemeInformation,
    @SerialName(ETSI19602.TRUSTED_ENTITIES_LIST) @Required val entities: List<TrustedEntity>,
){
    init {
        requireNonEmpty(entities, ETSI19602.TRUSTED_ENTITIES_LIST)
    }
}

@Serializable
public data class ListOfTrustedEntitiesClaims(
    @SerialName(ETSI19602.LOTE) @Required val listOfTrustedEntities: ListOfTrustedEntities,
)
