# Missing Extraction Operations Implementation Plan

## Assessment Verification Summary

✅ **Assessment is accurate** - The `wrpac-assessment.md` correctly identifies:
- Current implementation covers ~28% of ETSI TS 119 411-8 requirements
- Priority 1 fixes (AIA enforcement, QCStatement validation) are complete
- Missing extraction operations prevent full compliance
- ETSI references are correct and appropriate

---

## Implementation Plan

### Phase 0: Create OID Constants Objects (NEW - Address Magic Values)

**Goal**: Eliminate magic string values by creating specification-based constant objects following project conventions.

**Files to create**:

1. **`consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/RFC5280.kt`**
   ```kotlin
   public object RFC5280 {
       // Extension OIDs
       public const val EXT_AUTHORITY_KEY_IDENTIFIER: String = "2.5.29.35"
       public const val EXT_SUBJECT_KEY_IDENTIFIER: String = "2.5.29.14"
       public const val EXT_KEY_USAGE: String = "2.5.29.15"
       public const val EXT_CERTIFICATE_POLICIES: String = "2.5.29.32"
       public const val EXT_SUBJECT_ALT_NAME: String = "2.5.29.17"
       public const val EXT_ISSUER_ALT_NAME: String = "2.5.29.18"
       public const val EXT_BASIC_CONSTRAINTS: String = "2.5.29.19"
       public const val EXT_CRL_DISTRIBUTION_POINTS: String = "2.5.29.31"
       public const val EXT_AUTHORITY_INFO_ACCESS: String = "1.3.6.1.5.5.7.1.1"
       public const val EXT_SUBJECT_INFO_ACCESS: String = "1.3.6.1.5.5.7.1.11"
       public const val EXT_NO_REVOCATION_AVAILABLE: String = "2.5.29.56"
       
       // Access Method OIDs (AIA)
       public const val AD_CA_ISSUERS: String = "1.3.6.1.5.5.7.48.2"
       public const val AD_OCSP: String = "1.3.6.1.5.5.7.48.1"
   }
   ```

2. **`consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/ETSI319412_1.kt`**
   ```kotlin
   public object ETSI319412_1 {
       // Extension OIDs specific to EN 319 412-1
       public const val EXT_ETSI_VAL_ASSURED_ST_CERTS: String = "0.4.0.194121.2.1"
       // Add other EN 319 412-1 specific OIDs
   }
   ```

3. **`119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/ETSI119411_8.kt`** (or extend existing `ETSI119411.kt`)
   ```kotlin
   public object ETSI119411_8 {
       // Certificate Policy OIDs from TS 119 411-8 Clause 5.3
       public const val CP_NCP_N_EUDIWRP: String = "0.4.0.194118.1.1"
       public const val CP_NCP_L_EUDIWRP: String = "0.4.0.194118.1.2"
       public const val CP_QCP_N_EUDIWRP: String = "0.4.0.194118.1.3"
       public const val CP_QCP_L_EUDIWRP: String = "0.4.0.194118.1.4"
   }
   ```

**Usage in constraints**:
```kotlin
// Instead of:
requireCriticalExtension("2.5.29.15")  // keyUsage

// Use:
requireCriticalExtension(RFC5280.EXT_KEY_USAGE)
```

---

### Phase 1: Core Infrastructure - Add Data Classes (Priority 2.2, 2.6, 2.7, 2.8)

**File**: `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateOperations.kt`

Add the following data classes:

1. **`DistinguishedName`** - For subject/issuer DN attributes
   - Properties: `attributes: Map<String, String>` (OID/short name → value)
   - Helper methods for common attributes (countryName, organizationName, etc.)

2. **`SerialNumber`** - Wrapper for certificate serial number
   - Property: `value: BigInteger`
   - Validation: must be positive

3. **`Version`** - X.509 version wrapper
   - Property: `value: Int` (1=v1, 2=v2, 3=v3)
   - Helper: `isV3()` convenience method

4. **`PublicKeyInfo`** - Public key algorithm and size
   - Properties: `algorithm: String`, `keySize: Int?`, `parameters: ByteArray?`

5. **`SubjectAlternativeName`** - Sealed interface for SAN types
   - Subtypes: `Uri`, `Email`, `Telephone`, `DNSName`, `IPAddress`, `RFC822Name`

6. **`AuthorityKeyIdentifier`** - AKI extension content
   - Properties: `keyIdentifier: ByteArray?`, `authorityCertIssuer: List<String>?`, `authorityCertSerialNumber: BigInteger?`

7. **`CrlDistributionPoint`** - CRL DP information
   - Properties: `distributionPointUri: String?`, `crlIssuer: List<String>?`

8. **`ExtensionCriticality`** - Wrapper for extension with criticality flag
   - Generic: `data class ExtensionInfo<T>(val value: T, val isCritical: Boolean)`

---

### Phase 2: Extend CertificateOperations Interface (Priority 2.1-2.8)

**File**: `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateOperations.kt`

Add to `CertificateOperations<CERT>` interface:

```kotlin
public fun getSubject(certificate: CERT): DistinguishedName?
public fun getIssuer(certificate: CERT): DistinguishedName?
public fun getSubjectAltNames(certificate: CERT): List<SubjectAlternativeName>
public fun getCrlDistributionPoints(certificate: CERT): List<CrlDistributionPoint>
public fun getAuthorityKeyIdentifier(certificate: CERT): AuthorityKeyIdentifier?
public fun getSerialNumber(certificate: CERT): SerialNumber
public fun getVersion(certificate: CERT): Version
public fun getSubjectPublicKeyInfo(certificate: CERT): PublicKeyInfo
```

Add corresponding `CertificateOperationsAlgebra` sealed interface members:

```kotlin
public data object GetSubject : CertificateOperationsAlgebra<DistinguishedName?>
public data object GetIssuer : CertificateOperationsAlgebra<DistinguishedName?>
public data object GetSubjectAltNames : CertificateOperationsAlgebra<List<SubjectAlternativeName>>
public data object GetCrlDistributionPoints : CertificateOperationsAlgebra<List<CrlDistributionPoint>>
public data object GetAuthorityKeyIdentifier : CertificateOperationsAlgebra<AuthorityKeyIdentifier?>
public data object GetSerialNumber : CertificateOperationsAlgebra<SerialNumber>
public data object GetVersion : CertificateOperationsAlgebra<Version>
public data object GetSubjectPublicKeyInfo : CertificateOperationsAlgebra<PublicKeyInfo>
```

---

### Phase 3: Implement JVM/Android Extractors (Priority 2.x)

**File**: `consultation/src/jvmAndAndroidMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/CertificateOperationsJvm.kt`

Implement all new extraction methods using BouncyCastle ASN.1 parsing:

1. **`getSubject()`** - Parse `certificate.subjectX500Principal` into DN attributes
2. **`getIssuer()`** - Parse `certificate.issuerX500Principal` into DN attributes
3. **`getSubjectAltNames()`** - Parse extension OID `2.5.29.17` (subjectAltName)
4. **`getCrlDistributionPoints()`** - Parse extension OID `2.5.29.31` (cRLDistributionPoints)
5. **`getAuthorityKeyIdentifier()`** - Parse extension OID `2.5.29.35` (authorityKeyIdentifier)
6. **`getSerialNumber()`** - Wrap `certificate.serialNumber`
7. **`getVersion()`** - Wrap `certificate.version` (X.509 v3 = 2)
8. **`getSubjectPublicKeyInfo()`** - Parse `certificate.publicKey` algorithm and size

Each implementation will:
- Use BouncyCastle ASN.1 parsers (similar to existing `parseAiaExtension()`)
- Handle parsing errors gracefully with logging
- Return null/empty where appropriate

---

### Phase 4: Update CertificateProfile.kt DSL (Priority 3)

**File**: `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateProfile.kt`

Add DSL builder methods to `ProfileBuilder`:

```kotlin
public fun subject(check: (DistinguishedName?) -> CertificateConstraintEvaluation)
public fun issuer(check: (DistinguishedName?) -> CertificateConstraintEvaluation)
public fun subjectAltNames(check: (List<SubjectAlternativeName>) -> CertificateConstraintEvaluation)
public fun crlDistributionPoints(check: (List<CrlDistributionPoint>) -> CertificateConstraintEvaluation)
public fun authorityKeyIdentifier(check: (AuthorityKeyIdentifier?) -> CertificateConstraintEvaluation)
public fun serialNumber(check: (SerialNumber) -> CertificateConstraintEvaluation)
public fun version(check: (Version) -> CertificateConstraintEvaluation)
public fun subjectPublicKeyInfo(check: (PublicKeyInfo) -> CertificateConstraintEvaluation)
```

---

### Phase 5: Add Profile Constraints to CertificateProfileConstraints.kt (Priority 3)

**File**: `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateProfileConstraints.kt`

Add constraint functions for WRPAC requirements:

1. **Version validation**:
   - `requireVersion(expected: Int)` - Require X.509 v3

2. **Serial number validation**:
   - `requirePositiveSerialNumber()` - Serial must be positive

3. **Subject DN validation**:
   - `requireSubjectNaturalPersonAttributes()` - countryName, givenName/surname/pseudonym, commonName, serialNumber
   - `requireSubjectLegalPersonAttributes()` - countryName, organizationName, organizationIdentifier, commonName

4. **Issuer DN validation**:
   - `requireIssuerAttributes()` - Appropriate attributes based on issuer type

5. **SubjectAltName validation**:
   - `requireSubjectAltNameForWRPAC()` - Must contain URI, email, or telephone

6. **AuthorityKeyIdentifier validation**:
   - `requireAuthorityKeyIdentifier()` - AKI must be present

7. **CRLDistributionPoints conditional validation**:
   - `requireCrlDistributionPointsIfNoOcspAndNotValAssured()` - Conditional per EN 319 412-2

8. **Extension criticality validation**:
   - `requireCriticalExtension(oid: String)` - Require specific extensions to be critical

9. **Public key validation**:
   - `requirePublicKeyAlgorithm(vararg algorithms: String)` - ECDSA or RSA
   - `requireMinimumKeySize(minSize: Int)` - Minimum key size

Add corresponding violation messages to `Violations` object.

---

### Phase 6: Update CertificateProfileValidator.kt Interpreter (Priority 3)

**File**: `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateProfileValidator.kt`

Update `CertificateProfileInterpreter` when block to handle new algebra types:

```kotlin
when (op) {
    // ... existing cases ...
    is CertificateOperationsAlgebra.GetSubject -> operations.getSubject(certificate) as T
    is CertificateOperationsAlgebra.GetIssuer -> operations.getIssuer(certificate) as T
    is CertificateOperationsAlgebra.GetSubjectAltNames -> operations.getSubjectAltNames(certificate) as T
    is CertificateOperationsAlgebra.GetCrlDistributionPoints -> operations.getCrlDistributionPoints(certificate) as T
    is CertificateOperationsAlgebra.GetAuthorityKeyIdentifier -> operations.getAuthorityKeyIdentifier(certificate) as T
    is CertificateOperationsAlgebra.GetSerialNumber -> operations.getSerialNumber(certificate) as T
    is CertificateOperationsAlgebra.GetVersion -> operations.getVersion(certificate) as T
    is CertificateOperationsAlgebra.GetSubjectPublicKeyInfo -> operations.getSubjectPublicKeyInfo(certificate) as T
}
```

---

### Phase 7: Update WRPAC Profile (Priority 3)

**File**: `119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/eu/EUWRPACProvidersList.kt`

Enhance `wrpAccessCertificateProfile()` with new constraints using OID constants:

```kotlin
return certificateProfile {
    requireEndEntityCertificate()
    requireDigitalSignature()
    requireValidAt(at)
    requirePolicy(policies)
    requireNoSelfSigned()
    requireAiaForCaIssued()
    
    // New constraints:
    requireVersion(3)  // X.509 v3
    requirePositiveSerialNumber()
    requireAuthorityKeyIdentifier()
    requireSubjectAltNameForWRPAC()
    
    // Conditional based on policy type
    when (policy) {
        NCP_N_EUDIWRP, QCP_N_EUDIWRP -> requireSubjectNaturalPersonAttributes()
        NCP_L_EUDIWRP, QCP_L_EUDIWRP -> requireSubjectLegalPersonAttributes()
    }
    
    // Conditional CRLDP
    requireCrlDistributionPointsIfNoOcspAndNotValAssured()
    
    // Criticality checks using OID constants
    requireCriticalExtension(RFC5280.EXT_KEY_USAGE)
    requireCriticalExtension(RFC5280.EXT_CERTIFICATE_POLICIES)
    
    // QCStatements for qualified certs (existing)
    if (isQcp) {
        requireQcStatement(ETSI319412.QC_COMPLIANCE)
        requireQcStatement(ETSI319412.QC_SSCD)
        if (policy == QCP_L_EUDIWRP) {
            requireQcStatement(ETSI319412.QC_TYPE)
        }
    }
}
```

---

### Phase 8: Testing

1. **Unit tests** for each new extraction method
2. **Test certificates** covering:
   - All four policy types (NCP-n, NCP-l, QCP-n, QCP-l)
   - Natural person vs legal person subjects
   - With/without optional extensions
3. **Negative tests** for each constraint violation
4. **Integration tests** with full certificate chains

---

## Implementation Order

1. ✅ **Phase 0**: Create OID constant objects (RFC5280, ETSI319412_1, etc.)
2. ✅ **Phase 1**: Data classes (foundation)
3. ✅ **Phase 2**: Interface extension
4. ✅ **Phase 3**: JVM implementations
5. ✅ **Phase 4**: Profile DSL methods
6. ✅ **Phase 5**: Constraint functions
7. ✅ **Phase 6**: Validator interpreter
8. ✅ **Phase 7**: WRPAC profile updates
9. ✅ **Phase 8**: Testing

---

## Expected Outcome

After implementation:
- Compliance score improves from **5/10 → 8-9/10**
- ~80-90% of ETSI TS 119 411-8 requirements covered
- Only advanced features (validity-assured extensions, short-term cert handling) remain
- Full WRPAC certificate validation for production use
- All OID values follow project conventions (no magic strings)

---

## Files Summary

### New Files to Create

| File | Purpose |
|------|---------|
| `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/RFC5280.kt` | RFC 5280 OID constants |
| `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/ETSI319412_1.kt` | ETSI EN 319 412-1 OID constants |
| `119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/ETSI119411_8.kt` | ETSI TS 119 411-8 policy OIDs |

### Files to Modify

| File | Changes |
|------|---------|
| `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateOperations.kt` | Add data classes, interface methods, algebra types |
| `consultation/src/jvmAndAndroidMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/CertificateOperationsJvm.kt` | Implement extraction methods |
| `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateProfile.kt` | Add DSL builder methods |
| `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateProfileConstraints.kt` | Add constraint functions |
| `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateProfileValidator.kt` | Update interpreter |
| `119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/eu/EUWRPACProvidersList.kt` | Enhance WRPAC profile |

---

## References

- Assessment: [`plan/wrpac-assessment.md`](../plan/wrpac-assessment.md)
- ETSI TS 119 411-8: Wallet Relying Party Access Certificate specifications
- ETSI EN 319 412-1/2/3/5: Certificate policy and profile requirements
- RFC 5280: X.509 Certificate and CRL Profile
