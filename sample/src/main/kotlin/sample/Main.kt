package sample

import io.github.amine2233.networkingmiddle.HttpMethod
import io.github.amine2233.networkingmiddle.InterceptorFactory
import io.github.amine2233.networkingmiddle.NetworkProviderFactory
import io.github.amine2233.networkingmiddle.RequestBodyFactory
import io.github.amine2233.networkingmiddle.RequestModifierFactory
import io.github.amine2233.networkingmiddle.RequestParametersFactory
import io.github.amine2233.networkingmiddle.decode
import io.github.amine2233.networkingmiddle.ktor.asNetworkService
import io.github.amine2233.networkingmiddle.unauthenticated
import io.github.amine2233.networkingmiddle.validate
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class HttpBinResponse(
    val url: String,
    val headers: Map<String, String>,
    val args: Map<String, String> = emptyMap(),
    val json: JsonObject? = null,
)

@Serializable
data class Login(
    val user: String,
    val password: String,
)

private const val BASE_URL = "https://httpbin.org"

fun main(): Unit =
    runBlocking {
        HttpClient(CIO).use { client -> run(client) }
    }

private suspend fun run(client: HttpClient) {
    val json = Json { ignoreUnknownKeys = true }

    val provider =
        NetworkProviderFactory
            .create(
                networkService = client.asNetworkService(),
                modifiers =
                    listOf(
                        RequestModifierFactory.bearerToken("demo-token"),
                        RequestModifierFactory.header("X-App", "sample"),
                    ),
                interceptors =
                    listOf(
                        InterceptorFactory.interceptorTimeLogger(::println),
                    ),
            ).intercept { baseURL, request, next ->
                println("→ ${request.method.rawValue} $baseURL${request.path}")
                next(baseURL, request).also { println("← ${it.statusCode}") }
            }

    val get =
        provider.newBuilder(
            path = "/get",
            method = HttpMethod.Get,
            parameters = RequestParametersFactory.queryParameters(mapOf("page" to "1", "q" to "kotlin ktor")),
        )
    val getResult =
        provider
            .request(BASE_URL, get)
            .validate()
            .unauthenticated()
            .decode<HttpBinResponse>(json)
    println("GET args=${getResult.args} auth=${getResult.headers["Authorization"]}")

    val post =
        provider.newBuilder(
            path = "/post",
            method = HttpMethod.Post,
            body = RequestBodyFactory.encodableBody(Login("amine", "secret")),
        )
    val postResult = provider.request(BASE_URL, post).validate().decode<HttpBinResponse>(json)
    println("POST json=${postResult.json}")

    val failing = provider.newBuilder(path = "/status/401", method = HttpMethod.Get)
    runCatching { provider.request(BASE_URL, failing).validate().unauthenticated() }
        .onFailure { println("Expected failure: $it") }
}
