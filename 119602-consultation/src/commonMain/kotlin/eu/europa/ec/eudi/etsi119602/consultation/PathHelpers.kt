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

import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
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
 * Helper to create HttpClient for Swift/iOS.
 * This creates a properly configured HttpClient with JSON support for LoTE downloads.
 *
 * NOTE: On iOS, this uses the Darwin (NSURLSession) engine automatically.
 * Make sure you've added ktor-client-darwin dependency to the iOS framework.
 *
 * @return Configured HttpClient for LoTE operations
 */
public fun createHttpClientForIOS(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}

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
 * @return Configured provisioner for EUDIW verification contexts using Signum
 */
public fun createProvisionTrustAnchorsSignumWithConfig(
    loadLoTEAndPointers: LoadLoTEAndPointers,
    svcTypePerCtx: SupportedLists<LotEMeta<VerificationContext>>,
): ProvisionTrustAnchorsFromLoTEs<List<at.asitplus.signum.indispensable.pki.X509Certificate>, VerificationContext, at.asitplus.signum.indispensable.pki.X509Certificate, at.asitplus.signum.indispensable.pki.X509Certificate> =
    ProvisionTrustAnchorsFromLoTEs.eudiwSignum(
        loadLoTEAndPointers = loadLoTEAndPointers,
        svcTypePerCtx = svcTypePerCtx,
    )
