package eu.europa.ec.eudi.etsi119602

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test

class ListOfTrustedEntitiesTest {
    @Test
    fun pidProviderLoTE() = runTest {
        val listOfTrustedEntities =
            getLoTE("https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/pid-providers.json")
        println(listOfTrustedEntities.schemeInformation)
        listOfTrustedEntities.entities.forEach { println(it) }
        with(PIDProvidersProfile) {
            listOfTrustedEntities.ensureScheme()
        }
    }

    @Test
    fun walletProviderLoTE() = runTest {
        val listOfTrustedEntities =
            getLoTE("https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/wallet-providers.json")
        println(listOfTrustedEntities.schemeInformation)
        listOfTrustedEntities.entities.forEach { println(it) }
        with(WalletProvidersProfile) {
            listOfTrustedEntities.ensureScheme()
        }
    }


    private suspend fun getLoTE(uri: String): ListOfTrustedEntities =
        createHttpClient(true).use { httpClient ->
            val (_, payload) = httpClient
                .get(uri)
                .bodyAsText()
                .let { fromCompact(it).getOrThrow() }
            println(JsonSupportDebug.encodeToString(payload))
            val loTEClaims = JsonSupportDebug.decodeFromJsonElement<ListOfTrustedEntitiesClaims>(payload)
            loTEClaims.listOfTrustedEntities

        }
}

fun createHttpClient(enableLogging: Boolean = true): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(JsonSupportDebug)
        }
        install(HttpCookies)

    }

private const val TWO_SPACES = "  "
internal val JsonSupportDebug: Json =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = TWO_SPACES
        encodeDefaults = false
        explicitNulls = false
    }

fun fromCompact(compact: String): Result<Pair<JsonObject, JsonObject>> =
    runCatching {
        require(compact.isNotBlank()) { "Input must not be empty" }
        compact.split(".").let { parts ->
            require(parts.size == 3) { "Input must be a JWS in compact form" }
            val header =
                JsonSupportDebug.parseToJsonElement(base64UrlSafeNoPadding.decode(parts[0]).decodeToString()).jsonObject
            val payload =
                JsonSupportDebug.parseToJsonElement(base64UrlSafeNoPadding.decode(parts[1]).decodeToString()).jsonObject
            header to payload
        }
    }

private val base64UrlSafeNoPadding: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)