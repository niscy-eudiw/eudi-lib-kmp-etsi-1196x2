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
package eu.europa.ec.eudi.etsi1196x2.consultation.certs

/**
 * Object IDs and constants from ETSI EN 319 412-1 - Certificate Policy Requirements.
 *
 * This object contains OID constants for ETSI-specific extensions as defined in
 * ETSI EN 319 412-1 (Policy requirements for certification authorities issuing public key certificates).
 *
 * @see [ETSI EN 319 412-1 - Policy requirements for certification authorities](https://www.etsi.org/deliver/etsi_en/319400_319499/31941201/)
 */
public object ETSI319412Part1 {
    //
    // ETSI-specific Extension OIDs
    //

    /**
     * id-etsi-valassured-ST-certs OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   valassured(194121) extensions(2) valAssuredSTCerts(1) }
     *
     * Extension indicating a short-term certificate with validity assurance.
     * Per EN 319 412-1, this extension may be present in certificates with validity period
     * of 7 days or less, and exempts the certificate from CRL distribution point requirements.
     *
     * @see [ETSI EN 319 412-1 Clause 5.2 - Short-term certificates](https://www.etsi.org/deliver/etsi_en/319400_319499/31941201/)
     */
    public const val EXT_ETSI_VAL_ASSURED_ST_CERTS: String = "0.4.0.194121.2.1"

    /**
     * id-etsi-qcp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   qcp(194121) 1 }
     *
     * Base OID for ETSI Qualified Certificate Policy identifiers.
     */
    public const val OID_ETSI_QCP_BASE: String = "0.4.0.194121.1"

    /**
     * id-etsi-ncp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   ncp(194121) 1 }
     *
     * Base OID for ETSI Non-Qualified Certificate Policy identifiers.
     */
    public const val OID_ETSI_NCP_BASE: String = "0.4.0.194121.1.1"
}
