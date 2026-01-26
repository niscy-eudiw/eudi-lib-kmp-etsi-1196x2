package eu.europa.ec.eudi.etsi1196x2.consultation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public fun interface GetTrustAnchors<out TRUST_ANCHORS : Any> : suspend () -> List<TRUST_ANCHORS> {
    public companion object {
        public fun <TRUST_ANCHOR : Any> once(source: suspend () -> List<TRUST_ANCHOR>): GetTrustAnchors<TRUST_ANCHOR> =
            GetTrustAnchorsOnce(source)
    }
}


private class GetTrustAnchorsOnce<TRUST_ANCHOR : Any>(
    private val source: suspend () -> List<TRUST_ANCHOR>,
) : GetTrustAnchors<TRUST_ANCHOR> {

    private val readKeystore = Mutex()
    private var trustAnchors: List<TRUST_ANCHOR>? = null

    private suspend fun readTrustAnchors(): List<TRUST_ANCHOR> = source.invoke()

    override suspend fun invoke(): List<TRUST_ANCHOR> =
        trustAnchors ?: readKeystore.withLock {
            // check again in case another thread read the keystore before us
            trustAnchors ?: readTrustAnchors().also { trustAnchors = it }
        }
}