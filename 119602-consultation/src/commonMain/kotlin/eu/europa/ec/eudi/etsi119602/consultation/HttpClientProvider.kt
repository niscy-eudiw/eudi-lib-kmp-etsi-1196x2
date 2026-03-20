package eu.europa.ec.eudi.etsi119602.consultation

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val TWO_SPACES = "  "

internal val JsonSupportDebug: Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    prettyPrintIndent = TWO_SPACES
    encodeDefaults = false
    explicitNulls = false
}

public fun createHttpClient(): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(JsonSupportDebug)
        }
        install(HttpCookies)
    }