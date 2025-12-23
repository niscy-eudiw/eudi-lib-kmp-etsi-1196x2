package eu.europa.ec.eudi.etsi119602

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import netscape.javascript.JSObject

@Serializable
public data class TrustedEntity(
    @SerialName(ETSI19602.TRUSTED_ENTITY_INFORMATION) @Required val information: TrustedEntityInformation,
    @SerialName(ETSI19602.TRUSTED_ENTITY_SERVICES) @Required val services: List<TrustedEntityService>,
){
    init {
        requireNonEmpty(services, ETSI19602.TRUSTED_ENTITY_SERVICES)
    }
}

@Serializable
public data class TrustedEntityInformation(
    @SerialName(ETSI19602.TE_NAME) @Required val name: List<MultiLangString>,
    @SerialName(ETSI19602.TE_TRADE_NAME) val tradeName: List<MultiLangString>? = null,
    @SerialName(ETSI19602.TE_ADDRESS) @Required val address: TEAddress,
    @SerialName(ETSI19602.TE_INFORMATION_URI) @Required val informationURI: List<MultiLanguageURI>,
){
    init {
        requireNonEmpty(name, ETSI19602.TE_NAME)
        requireNullOrNonEmpty(tradeName, ETSI19602.TE_TRADE_NAME)
        requireNonEmpty(informationURI, ETSI19602.TE_INFORMATION_URI)
    }
}

@Serializable
public data class TEAddress(
    @SerialName(ETSI19602.TE_POSTAL_ADDRESS) @Required val postalAddresses: List<PostalAddress>,
    @SerialName(ETSI19602.TE_ELECTRONIC_ADDRESS) @Required val electronicAddresses: List<MultiLanguageURI>,
) {
    init {
        requireNonEmpty(postalAddresses, ETSI19602.TE_POSTAL_ADDRESS)
        requireNonEmpty(electronicAddresses, ETSI19602.TE_ELECTRONIC_ADDRESS)
    }
}

public typealias TrustedEntityService = JsonObject