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
package eu.europa.ec.eudi.etsi1196x2.consultation.dss

import eu.europa.ec.eudi.etsi1196x2.consultation.IsChainTrustedForContext
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader
import eu.europa.esig.dss.spi.client.http.DSSCacheFileLoader
import eu.europa.esig.dss.spi.client.http.IgnoreDataLoader
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.cache.CacheCleaner
import eu.europa.esig.dss.tsl.job.TLValidationJob
import eu.europa.esig.dss.tsl.source.LOTLSource
import eu.europa.esig.dss.tsl.sync.ExpirationAndSignatureCheckStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Path
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.sql.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant

@Suppress("SameParameterValue")
fun buildLoTLTrust(
    clock: Clock = Clock.System,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    cacheDir: Path,
    revocationEnabled: Boolean = false,
    builder: MutableMap<VerificationContext, LOTLSource>.() -> Unit,
): DssLoadAndTrust = DssLoadAndTrust(
    clock,
    scope,
    cacheDir,
    revocationEnabled,
    buildMap(builder),
)

data class DssLoadAndTrust private constructor(
    val dssLoader: DSSLoader,
    val isChainTrustedForContext: IsChainTrustedForContext<List<X509Certificate>, TrustAnchor>,
) {
    companion object {
        operator fun invoke(
            clock: Clock = Clock.System,
            scope: CoroutineScope,
            cacheDir: Path,
            revocationEnabled: Boolean = false,
            instructions: Map<VerificationContext, LOTLSource>,
        ): DssLoadAndTrust {
            val dssLoader =
                DSSLoader.invoke(cacheDir, instructions.values.toList())

            val isChainTrustedForContext = run {
                val validateCertificateChain =
                    ValidateCertificateChainJvm {
                        isRevocationEnabled = revocationEnabled
                        date = Date.from(clock.now().toJavaInstant())
                    }
                val getTrustedListsCertificateByLOTLSource =
                    GetTrustedListsCertificateByLOTLSource.fromBlocking(
                        scope = scope,
                        expectedTrustSourceNo = instructions.size,
                        ttl = 10.minutes,
                        block = dssLoader::trustedListsCertificateSourceOf,
                    )
                IsChainTrustedForContext.usingLoTL(
                    validateCertificateChain,
                    instructions.mapValues { (_, lotlSource) -> lotlSource to null },
                    getTrustedListsCertificateByLOTLSource,
                )
            }

            return DssLoadAndTrust(dssLoader, isChainTrustedForContext)
        }
    }
}

class DSSLoader(
    private val lotlLocationPerSource: List<LOTLSource>,
    private val onlineLoader: DSSCacheFileLoader,
    private val offlineLoader: DSSCacheFileLoader?,
    private val cacheCleaner: CacheCleaner?,
) {

    fun trustedListsCertificateSourceOf(
        lotlSource: LOTLSource,
    ): TrustedListsCertificateSource {
        require(lotlSource in lotlLocationPerSource) { "Not configured $lotlSource" }
        return TrustedListsCertificateSource().also { source ->
            validationJob(lotlSource, source).refresh()
        }
    }

    private fun TLValidationJob.refresh() {
        try {
            onlineRefresh()
        } catch (e: Exception) {
            if (offlineLoader != null) {
                try {
                    offlineRefresh()
                } catch (_: Exception) {
                    throw RuntimeException("Both online and offline trust loading failed", e)
                }
            } else {
                throw e
            }
        }
    }

    private fun validationJob(
        lotlSource: LOTLSource,
        source: TrustedListsCertificateSource,
    ) = TLValidationJob().apply {
        setListOfTrustedListSources(lotlSource)
        setOnlineDataLoader(onlineLoader)
        setTrustedListCertificateSource(source)
        setSynchronizationStrategy(ExpirationAndSignatureCheckStrategy())
        offlineLoader?.let { setOfflineDataLoader(it) }
        cacheCleaner?.let { setCacheCleaner(it) }
    }

    companion object {
        operator fun invoke(
            cacheDir: Path,
            lotlLocationPerSource: List<LOTLSource>,
        ): DSSLoader {
            val tlCacheDirectory = cacheDir.toFile()
            val offlineLoader: DSSCacheFileLoader = FileCacheDataLoader().apply {
                setCacheExpirationTime(24 * 60 * 60 * 1000)
                setFileCacheDirectory(tlCacheDirectory)
                dataLoader = IgnoreDataLoader()
            }

            val onlineLoader: DSSCacheFileLoader = FileCacheDataLoader().apply {
                setCacheExpirationTime(24 * 60 * 60 * 1000)
                setFileCacheDirectory(tlCacheDirectory)
                dataLoader = CommonsDataLoader()
            }

            val cacheCleaner = CacheCleaner().apply {
                setCleanMemory(true)
                setCleanFileSystem(true)
                setDSSFileLoader(offlineLoader)
            }
            return DSSLoader(lotlLocationPerSource, onlineLoader, offlineLoader, cacheCleaner)
        }
    }
}
