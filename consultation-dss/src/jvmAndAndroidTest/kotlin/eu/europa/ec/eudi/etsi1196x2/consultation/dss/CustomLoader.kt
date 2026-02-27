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
package eu.europa.ec.eudi.etsi1196x2.consultation.dss

import eu.europa.ec.eudi.etsi1196x2.consultation.AsyncCache
import eu.europa.esig.dss.model.DSSDocument
import eu.europa.esig.dss.model.FileDocument
import eu.europa.esig.dss.spi.client.http.DSSCacheFileLoader
import eu.europa.esig.dss.spi.client.http.DataLoader
import eu.europa.esig.dss.spi.exception.DSSDataLoaderMultipleException
import eu.europa.esig.dss.spi.exception.DSSExternalResourceException
import eu.europa.esig.dss.tsl.sync.SynchronizationStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public fun DssOptions.Companion.usingCustomLoader(
    fileCacheExpiration: Duration = DefaultFileCacheExpiration,
    cacheDirectory: Path? = null,
    cleanMemory: Boolean = DEFAULT_CLEAN_MEMORY,
    cleanFileSystem: Boolean = DEFAULT_CLEAN_FILE_SYSTEM,
    httpLoader: DataLoader = DefaultHttpLoader,
    httpCacheTtl: Duration = 5.seconds,
    maxCacheSize: Int = 100,
    cacheDispatcher: CoroutineDispatcher = Dispatchers.IO,
    clock: Clock = Clock.System,
    synchronizationStrategy: SynchronizationStrategy = DefaultSynchronizationStrategy,
    executorService: ExecutorService? = null,
    validateJobDispatcher: CoroutineDispatcher = Dispatchers.IO,
): DssOptions {
    val loader = CustomLoader(
        httpLoader = httpLoader,
        fileCacheExpiration = fileCacheExpiration,
        cacheDirectory = cacheDirectory,
        cacheDispatcher = cacheDispatcher,
        clock = clock,
        httpCacheTtl = httpCacheTtl,
        maxCacheSize = maxCacheSize,
    )
    return DssOptions(
        loader,
        cleanMemory,
        cleanFileSystem,
        synchronizationStrategy,
        executorService,
        validateJobDispatcher,
    )
}

public class CustomLoader(
    private val httpLoader: DataLoader,
    private val fileCacheExpiration: Duration,
    cacheDirectory: Path? = null,
    cacheDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.System,
    httpCacheTtl: Duration = 5.seconds,
    maxCacheSize: Int = 100,
) : DataLoader, DSSCacheFileLoader, AutoCloseable {

    init {
        require(fileCacheExpiration.isPositive() && fileCacheExpiration != Duration.INFINITE) {
            "fileCacheExpiration must be positive and not infinite"
        }
    }

    private val fileCacheDirectory: Path =
        (cacheDirectory ?: Paths.get(System.getProperty("java.io.tmpdir")))
            .also { Files.createDirectories(it) }

    private val mutexes = ConcurrentHashMap<String, MutexHolder>()

    private val httpCache = AsyncCache<String, ByteArray>(
        cacheDispatcher = cacheDispatcher,
        clock = clock,
        ttl = httpCacheTtl,
        maxCacheSize = maxCacheSize,
    ) { url ->
        httpLoader.get(url)
    }

    override fun get(url: String): ByteArray = get(url, refresh = false)

    override fun get(urlStrings: List<String>): DataLoader.DataAndUrl {
        if (urlStrings.isEmpty()) {
            throw DSSExternalResourceException("Cannot process the GET call. List of URLs is empty!")
        }
        val exceptions = mutableMapOf<String, Throwable>()
        for (url in urlStrings) {
            try {
                val bytes = get(url)
                if (bytes.isNotEmpty()) {
                    return DataLoader.DataAndUrl(url, bytes)
                }
            } catch (e: Exception) {
                exceptions[url] = e
            }
        }
        throw DSSDataLoaderMultipleException(exceptions)
    }

    override fun post(url: String, content: ByteArray): ByteArray = httpLoader.post(url, content)

    override fun setContentType(contentType: String) {
        httpLoader.setContentType(contentType)
    }

    public fun get(url: String, refresh: Boolean): ByteArray = runBlocking {
        val holder = mutexes.compute(url) { _, existing ->
            val current = existing ?: MutexHolder()
            current.refCount.incrementAndGet()
            current
        }
        try {
            holder!!.mutex.withLock {
                val cacheFile = cacheFileFor(url)
                if (!refresh) {
                    readIfFresh(cacheFile)?.let { return@withLock it }
                }
                val bytes = httpCache.invoke(url)
                if (bytes.isEmpty()) {
                    throw DSSExternalResourceException("Cannot retrieve data from url [$url]. Empty content is obtained!")
                }
                writeAtomically(cacheFile, bytes)
                bytes
            }
        } finally {
            mutexes.computeIfPresent(url) { _, current ->
                if (current.refCount.decrementAndGet() == 0) null else current
            }
        }
    }

    override fun getDocument(url: String): DSSDocument = getDocument(url, refresh = false)

    override fun getDocument(url: String, refresh: Boolean): DSSDocument {
        val cacheFile = cacheFileFor(url)
        val bytes = get(url, refresh)
        if (bytes.isNotEmpty()) {
            return FileDocument(cacheFile.toFile())
        }
        throw DSSExternalResourceException("Cannot retrieve data from url [$url]. Empty content is obtained!")
    }

    override fun getDocumentFromCache(url: String): DSSDocument? {
        val cacheFile = cacheFileFor(url)
        return if (Files.exists(cacheFile)) FileDocument(cacheFile.toFile()) else null
    }

    override fun remove(url: String): Boolean {
        val cacheFile = cacheFileFor(url)
        return try {
            Files.deleteIfExists(cacheFile)
        } catch (_: Exception) {
            false
        }
    }

    override fun close() {
        httpCache.close()
    }

    private fun cacheFileFor(url: String): Path {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return fileCacheDirectory.resolve("cache-$digest")
    }

    private fun readIfFresh(file: Path): ByteArray? {
        if (!Files.exists(file)) return null
        if (fileCacheExpiration.isPositive()) {
            val lastModified = Files.getLastModifiedTime(file).toMillis()
            val now = clock.now().toEpochMilliseconds()
            if (now - lastModified >= fileCacheExpiration.inWholeMilliseconds) {
                return null
            }
        }
        return Files.readAllBytes(file)
    }

    private fun writeAtomically(target: Path, bytes: ByteArray) {
        Files.createDirectories(target.parent)
        val tmpFile = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
        try {
            Files.write(tmpFile, bytes)
            try {
                Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            Files.deleteIfExists(tmpFile)
            throw e
        }
    }

    private class MutexHolder(
        val mutex: Mutex = Mutex(),
        val refCount: AtomicInteger = AtomicInteger(0),
    )
}
