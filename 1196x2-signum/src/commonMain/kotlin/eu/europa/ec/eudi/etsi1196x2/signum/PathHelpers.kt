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
package eu.europa.ec.eudi.etsi1196x2.signum

import eu.europa.ec.eudi.etsi119602.consultation.LoadLoTEAndPointers
import eu.europa.ec.eudi.etsi119602.consultation.LotEMeta
import eu.europa.ec.eudi.etsi119602.consultation.ProvisionTrustAnchorsFromLoTEs
import eu.europa.ec.eudi.etsi119602.consultation.eu
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Helper function to create a Path from a String.
 * This is primarily for Swift/iOS interoperability where kotlinx.io types
 * may not be properly exposed.
 *
 * @param pathString the path as a string
 * @return a Path object
 */
public fun createPath(pathString: String): Path = Path(pathString)

/**
 * Helper to get SystemFileSystem for Swift/iOS.
 */
public fun getSystemFileSystem(): FileSystem = SystemFileSystem

/**
 * Helper to get Clock.System for Swift/iOS.
 */
public fun getSystemClock(): Clock = Clock.System

/**
 * Helper to create Duration from hours for Swift/iOS.
 */
public fun hoursAsDuration(hours: Int): Duration = hours.hours

/**
 * Helper to get Dispatchers.Default for Swift/iOS.
 */
public fun getDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

/**
 * Helper to get ContinueOnProblem.Never for Swift/iOS.
 * This strategy stops immediately when any problem occurs during LoTE loading.
 */
public fun getContinueOnProblemNever(): eu.europa.ec.eudi.etsi119602.consultation.ContinueOnProblem =
    eu.europa.ec.eudi.etsi119602.consultation.ContinueOnProblem.Never

/**
 * Helper to get ContinueOnProblem.Always for Swift/iOS.
 * This strategy continues even if all LoTEs fail to load.
 */
public fun getContinueOnProblemAlways(): eu.europa.ec.eudi.etsi119602.consultation.ContinueOnProblem =
    eu.europa.ec.eudi.etsi119602.consultation.ContinueOnProblem.Always

/**
 * Helper to get ContinueOnProblem.AlwaysIfDownloaded for Swift/iOS.
 * This strategy continues if at least the main LoTE was successfully downloaded,
 * even if other (pointer) lists fail to load. This is the recommended default for iOS.
 */
public fun getContinueOnProblemAlwaysIfDownloaded(): eu.europa.ec.eudi.etsi119602.consultation.ContinueOnProblem =
    eu.europa.ec.eudi.etsi119602.consultation.ContinueOnProblem.AlwaysIfDownloaded

/**
 * Helper to create HttpClient for Swift/iOS.
 * This uses the same configuration as the JVM/Android createHttpClient().
 *
 * NOTE: On iOS, this uses the Darwin (NSURLSession) engine automatically.
 *
 * @return Configured HttpClient for LoTE operations
 */
public fun createHttpClientForIOS(): HttpClient = eu.europa.ec.eudi.etsi119602.consultation.createHttpClient()

/**
 * Helper to get EU SupportedLists configuration for Swift/iOS.
 * This wraps the SupportedLists.Companion.eu() extension function which is not
 * directly accessible from Swift.
 *
 * @return SupportedLists configured with EU EUDI Wallet verification contexts
 */
public fun getEUSupportedLists(): SupportedLists<LotEMeta<VerificationContext>> =
    SupportedLists.eu()

/**
 * Helper to create ProvisionTrustAnchorsFromLoTEs for Swift/iOS.
 * This wraps ProvisionTrustAnchorsFromLoTEs.Companion.eudiwSignum() which has generic
 * type inference issues in Swift.
 *
 * @param loadLoTEAndPointers Loader for fetching LoTEs and their pointers
 * @return Configured provisioner for EUDIW verification contexts using Signum
 */
public fun createProvisionTrustAnchorsSignum(
    loadLoTEAndPointers: LoadLoTEAndPointers,
): ProvisionTrustAnchorsFromLoTEs<List<at.asitplus.signum.indispensable.pki.X509Certificate>, VerificationContext, at.asitplus.signum.indispensable.pki.X509Certificate, at.asitplus.signum.indispensable.pki.X509Certificate> =
    ProvisionTrustAnchorsFromLoTEs.eudiwSignum(loadLoTEAndPointers = loadLoTEAndPointers)

/**
 * Helper to create ProvisionTrustAnchorsFromLoTEs with custom service type config for Swift/iOS.
 *
 * @param loadLoTEAndPointers Loader for fetching LoTEs and their pointers
 * @param svcTypePerCtx Service type metadata per verification context
 * @param continueOnProblem Error handling strategy (defaults to AlwaysIfDownloaded for better iOS reliability)
 * @return Configured provisioner for EUDIW verification contexts using Signum
 */
public fun createProvisionTrustAnchorsSignumWithConfig(
    loadLoTEAndPointers: LoadLoTEAndPointers,
    svcTypePerCtx: SupportedLists<LotEMeta<VerificationContext>>,
    continueOnProblem: eu.europa.ec.eudi.etsi119602.consultation.ContinueOnProblem = eu.europa.ec.eudi.etsi119602.consultation.ContinueOnProblem.AlwaysIfDownloaded,
): ProvisionTrustAnchorsFromLoTEs<List<at.asitplus.signum.indispensable.pki.X509Certificate>, VerificationContext, at.asitplus.signum.indispensable.pki.X509Certificate, at.asitplus.signum.indispensable.pki.X509Certificate> =
    ProvisionTrustAnchorsFromLoTEs.eudiwSignum(
        loadLoTEAndPointers = loadLoTEAndPointers,
        svcTypePerCtx = svcTypePerCtx,
        continueOnProblem = continueOnProblem,
    )
