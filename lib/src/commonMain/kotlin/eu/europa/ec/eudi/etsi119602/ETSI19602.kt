package eu.europa.ec.eudi.etsi119602

/**
 * [JSON Schema](https://forge.etsi.org/rep/esi/x19_60201_lists_of_trusted_entities/-/blob/main/1960201_json_schema/1960201_json_schema.json?ref_type=heads)
 */
public object ETSI19602 {
    public const val VERSION : String = "1.1.1"

    public const val LOTE_VERSION: Int = 1
    public const val INITIAL_SEQUENCE_NUMBER: Int = 1
    public const val COUNTRY_CODE_EU: String = "EU"
    public const val COUNTRY_CODE_SIZE : Int = 2

    public const val LOTE: String = "LoTE"
    public const val LIST_AND_SCHEME_INFORMATION: String = "ListAndSchemeInformation"
    public const val LOTE_VERSION_IDENTIFIER: String = "LoTEVersionIdentifier"
    public const val LOTE_SEQUENCE_NUMBER: String = "LoTESequenceNumber"
    public const val LOTE_TYPE: String = "LoTEType"
    public const val SCHEME_OPERATOR_NAME: String = "SchemeOperatorName"
    public const val SCHEME_OPERATOR_ADDRESS: String = "SchemeOperatorAddress"
    public const val SCHEME_OPERATOR_POSTAL_ADDRESS: String = "SchemeOperatorPostalAddress"
    public const val SCHEME_OPERATOR_ELECTRONIC_ADDRESS: String = "SchemeOperatorElectronicAddress"
    public const val SCHEME_NAME: String = "SchemeName"

    public const val SCHEME_INFORMATION_URI: String = "SchemeInformationURI"
    public const val STATUS_DETERMINATION_APPROACH: String = "StatusDeterminationApproach"
    public const val SCHEME_TYPE_COMMUNITY_RULES: String = "SchemeTypeCommunityRules"
    public const val SCHEME_TERRITORY: String = "SchemeTerritory"
    public const val POLICY_OR_LEGAL_NOTICE: String = "PolicyOrLegalNotice"
    public const val HISTORICAL_INFORMATION_PERIOD: String = "HistoricalInformationPeriod"
    public const val POINTER_TO_OTHER_LOTE: String = "PointerToOtherLoTE"
    public const val LIST_ISSUE_DATE_TIME: String = "ListIssueDateTime"
    public const val NEXT_UPDATE: String = "NextUpdate"
    public const val DISTRIBUTION_POINTS: String = "DistributionPoints"
    public const val SCHEME_EXTENSIONS: String = "SchemeExtensions"

    public const val LANG: String = "lang"
    public const val STRING_VALUE: String = "value"
    public const val URI_VALUE: String = "uriValue"
    public const val MIME_TYPE: String = "MimeType"

    public const val POSTAL_ADDRESS_STREET_ADDRESS: String = "StreetAddress"
    public const val POSTAL_ADDRESS_LOCALITY: String = "Locality"
    public const val POSTAL_ADDRESS_STATE_OR_PROVINCE: String = "StateOrProvince"
    public const val POSTAL_ADDRESS_POSTAL_CODE: String = "PostalCode"
    public const val POSTAL_ADDRESS_COUNTRY: String = "Country"

    public const val LOTE_POLICY: String = "LoTEPolicy"
    public const val LOTE_LEGAL_NOTICE: String = "LoTELegalNotice"

    public const val LOTE_LOCATION: String = "LoTELocation"
    public const val LOTE_QUALIFIERS :String = "LoTEQualifiers"

    public const val SERVICE_DIGITAL_IDENTITIES: String = "ServiceDigitalIdentities"

    public const val TRUSTED_ENTITIES_LIST: String = "TrustedEntitiesList"
    public const val TRUSTED_ENTITY_INFORMATION: String = "TrustedEntityInformation"
    public const val TE_NAME : String = "TEName"
    public const val TE_TRADE_NAME : String = "TETradeName"
    public const val TE_ADDRESS : String = "TEAddress"
    public const val TE_INFORMATION_URI : String = "TEInformationURI"
    public const val TE_POSTAL_ADDRESS : String = "TEPostalAddress"
    public const val TE_ELECTRONIC_ADDRESS : String = "TEElectronicAddress"
    public const val TRUSTED_ENTITY_SERVICES: String = "TrustedEntityServices"

    //
    // PID Provider's LoTE
    //
    public const val PID_PROVIDERS_LOTE_TYPE: String ="http://uri.etsi.org/19602/LoTEType/EUPIDProvidersList"
    public const val PID_PROVIDERS_STATUS_DETERMINATION_APPROACH: String = "http://uri.etsi.org/19602/PIDProvidersList/StatusDetn/EU"
    public const val PID_PROVIDERS_SCHEME_COMMUNITY_RULES: String = "http://uri.etsi.org/19602/PIDProviders/schemerules/EU"

    //
    // Wallet Provider's LoTE
    //
    public const val WALLET_PROVIDERS_LOTE_TYPE: String ="http://uri.etsi.org/19602/LoTEType/EUWalletProvidersList"
    public const val WALLET_PROVIDERS_STATUS_DETERMINATION_APPROACH: String = "http://uri.etsi.org/19602/WalletProvidersList/StatusDetn/EU"
    public const val WALLET_PROVIDERS_SCHEME_COMMUNITY_RULES: String = "http://uri.etsi.org/19602/WalletProvidersList/schemerules/EU"
}
