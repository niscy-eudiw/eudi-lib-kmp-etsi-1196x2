package eu.europa.ec.eudi.etsi119602

import eu.europa.ec.eudi.etsi119602.ListAndSchemeInformationAssertions.ensureWalletProvidersScheme

/**
 * A LoTE profile aimed at supporting the publication by the European Commission of a list of
 * wallet providers according to CIR 2024/2980 [i.2] Article 5(2)
 */
public object PIDProvidersProfile : Profile {
    override val scheme: Scheme get() = Scheme.EXPLICIT
    override val type: LoTEType get() = LoTEType(ETSI19602.PID_PROVIDERS_LOTE_TYPE);
    override val statusDeterminationApproach: String get() = ETSI19602.PID_PROVIDERS_STATUS_DETERMINATION_APPROACH
    override val schemeCommunityRules: List<MultiLanguageURI>
        get() = listOf(
            MultiLanguageURI(Language.EN, URIValue(ETSI19602.PID_PROVIDERS_SCHEME_COMMUNITY_RULES))
        )
    override val schemeTerritory: CountryCode get() = CountryCode.EU
    override val maxMonthsUntilNextUpdate: Int get() = 6
    override val historicalInformationPeriod: ValueRequirement get() = ValueRequirement.ABSENT

    private const val PROFILE_NAME = "PID Providers"

    public fun ListOfTrustedEntities.ensureScheme() {
        try {
            schemeInformation.ensureWalletProvidersScheme(PIDProvidersProfile)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Violation of ${PROFILE_NAME}: ${e.message}")
        }
    }
}
