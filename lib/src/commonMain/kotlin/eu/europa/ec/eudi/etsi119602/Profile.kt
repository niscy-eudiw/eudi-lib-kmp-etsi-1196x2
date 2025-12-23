package eu.europa.ec.eudi.etsi119602


public enum class Scheme{
    EXPLICIT, IMPLICIT, BOTH
}

public enum class ValueRequirement {
    REQUIRED, OPTIONAL, ABSENT
}

public interface Profile {
    public val scheme: Scheme
    public val type: LoTEType
    public val statusDeterminationApproach: String
    public val schemeCommunityRules: List<MultiLanguageURI>
    public val schemeTerritory: CountryCode
    public val maxMonthsUntilNextUpdate: Int
    public val historicalInformationPeriod : ValueRequirement
}
