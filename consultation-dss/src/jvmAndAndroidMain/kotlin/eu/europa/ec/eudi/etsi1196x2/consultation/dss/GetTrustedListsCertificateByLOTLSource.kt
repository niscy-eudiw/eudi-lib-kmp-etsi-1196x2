package eu.europa.ec.eudi.etsi1196x2.consultation.dss

import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchors
import eu.europa.ec.eudi.etsi1196x2.consultation.JvmSecurity
import eu.europa.ec.eudi.etsi1196x2.consultation.TrustAnchorCreator
import eu.europa.ec.eudi.etsi1196x2.consultation.dss.AsyncCache.Entry
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.*
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

public fun interface GetTrustedListsCertificateByLOTLSource {
    public suspend operator fun invoke(trustSource: LOTLSource): TrustedListsCertificateSource

    public companion object {
        public fun fromBlocking(
            scope: CoroutineScope,
            expectedTrustSourceNo: Int,
            ttl: Duration = 10.minutes,
            block: (LOTLSource) -> TrustedListsCertificateSource,
        ): GetTrustedListsCertificateByLOTLSource =
            GetTrustedListsCertificateByLOTLSourceBlocking(scope, expectedTrustSourceNo, ttl, block)
    }
}

public fun GetTrustedListsCertificateByLOTLSource.asGetTrustAnchors(
    trustSource: LOTLSource,
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.trustAnchorCreator()
): GetTrustAnchors<TrustAnchor> =
    GetTrustAnchors {
        invoke(trustSource).trustAnchors(trustAnchorCreator)
    }

internal fun TrustedListsCertificateSource.trustAnchors(
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor>
): List<TrustAnchor> = certificates.map { trustAnchorCreator(it.certificate) }


internal class GetTrustedListsCertificateByLOTLSourceBlocking(
    scope: CoroutineScope,
    expectedTrustSourceNo: Int,
    ttl: Duration = 10.minutes,
    block: (LOTLSource) -> TrustedListsCertificateSource,
) : GetTrustedListsCertificateByLOTLSource {

    private val cached: AsyncCache<LOTLSource, TrustedListsCertificateSource> =
        AsyncCache(scope, expectedTrustSourceNo, ttl) { trustSource ->
            withContext(Dispatchers.IO) {
                block(trustSource)
            }
        }

    override suspend fun invoke(trustSource: LOTLSource): TrustedListsCertificateSource = cached(trustSource)
}

internal class AsyncCache<A : Any, B : Any>(
    private val scope: CoroutineScope,
    private val maxCacheSize: Int,
    private val ttl: Duration,
    private val supplier: suspend (A) -> B,
) : suspend (A) -> B {

    private data class Entry<B>(val deferred: Deferred<B>, val createdAt: Long)

    private val cache = object : LinkedHashMap<A, Entry<B>>(maxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<A, Entry<B>>) = size > maxCacheSize
    }

    override suspend fun invoke(key: A): B {
        val now = System.currentTimeMillis()
        val entry = synchronized(cache) {
            val existing = cache[key]
            if (existing != null && (now - existing.createdAt) < ttl.inWholeMilliseconds) {
                existing
            } else {
                // Launch new computation
                val newDeferred = scope.async(Dispatchers.IO) {
                    try {
                        supplier(key)
                    } catch (e: Exception) {
                        // Evict on failure so next call retries
                        synchronized(cache) { cache.remove(key) }
                        throw e
                    }
                }
                Entry(newDeferred, now).also { cache[key] = it }
            }
        }
        return entry.deferred.await()
    }
}