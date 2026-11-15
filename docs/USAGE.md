# Usage guide

## 1. Concepts (same as the Swift package)

| Swift                              | Kotlin                                                    | Notes |
|------------------------------------|-----------------------------------------------------------|-------|
| `NetworkService` (URLSession)      | `fun interface NetworkService { suspend fun execute(UrlRequest): UrlResponse }` | Injected engine. Throws on transport error; the provider converts it into a `Response` with `statusCode = 499`. |
| `URLRequest`                       | `data class UrlRequest(url, method, headers, body)`       | Platform-neutral. |
| `NetworkProvider` / `Factory`      | `NetworkProvider` / `NetworkProviderFactory.create(...)`  | Immutable; `modifyRequests` / `intercept` return a **new** provider. |
| `Request` / `RequestFactory` / `AnyRequest` | same                                             | `setHeader(key, value)` returns a copy (Swift `set(_:forHttpHeaderKey:)`); `null` removes the header. Keys match case-insensitively: any existing casing is replaced or removed, and the new key is stored as written. |
| `HTTPMethod`                       | `class HttpMethod(rawValue)`                              | `HttpMethod.Get`, `.Post`, … or `HttpMethod("CUSTOM")`. Plain class (not `value class`) so Java/Android callers get real signatures. |
| `RequestParameters` / `Factory`    | same                                                      | `queryParameters`, `queryEncodableParameters` (kotlinx.serialization), `stringRequestParameters`. `stringRequestParameters` percent-decodes keys and values before re-encoding them (a literal `+` stays `+`, a malformed escape is kept as written) and keeps the last value of a duplicate key. |
| `RequestBody` / `Factory`          | same, `suspend fun apply`                                 | `encodableBody`, `formBody`, `jsonBody(JsonElement)`, `stringBody`, `dataBody`. `encodableBody`/`jsonBody`/`formBody` set `Content-Type` only if it is not already set (under any casing). |
| `RequestModifier` (inout)          | `fun interface RequestModifier { fun modify(baseURL, request): Request }` | Returns the new request instead of mutating. |
| `Interceptor` + `Next`             | `fun interface Interceptor`, `typealias Next = suspend (String, Request) -> Response` | |
| `Response` + extensions            | same names: `validate`, `decode`, `decodeResult`, `decodeError`, `decodeErrorResult`, `map`, `flatMap`, `result`, `mapError`, `log`, `debug`, `unauthenticated`, `unauthorized`, `validate(statusCode)`, `description`, plus `header(name)` (case-insensitive) | `JSONDecoder` → `Json`. `decode<Unit>()` replaces `EmptyDecodable` and succeeds on a null or empty body. |
| `APIError` + `RequestError`        | `sealed class ApiError`                                   | Folded into one type (`ApiError.InvalidUrl` replaces `RequestError.invalidURL`). `asUrlRequest` throws `ApiError.InvalidUrl` when the URL has whitespace, has no scheme or has an empty host. |

## 2. Flow

```
provider.request(baseURL, request)
  1. modifiers.fold(request)              // first → last, each returns a new Request
  2. request.asUrlRequest(baseURL)        // path join, headers, parameters, body  (throws ApiError.RequestParameters / Body / InvalidUrl)
  3. interceptors chain                   // interceptors[0] is outermost, exactly like the Swift `reversed()` fold
  4. networkService.execute(urlRequest)   // Ktor
  5. Response (ResponseDefault) — or ResponseError(499) if the service threw
```

Interceptors can short-circuit (`return Response.error(...)`), retry (`next(...)` twice), or rewrite the request.

## 3. Setup with Ktor

```kotlin
val client = HttpClient(CIO) {
    install(HttpTimeout) { requestTimeoutMillis = 10_000 }
    // engine { https { trustManager = pinnedTrustManager } }  ← certificate pinning belongs here
}
val provider = NetworkProviderFactory.create(client.asNetworkService())
```

`NetworkServiceKtor` notes:
- `expectSuccess = true` on the client is harmless: 4xx/5xx are still returned as a `Response`, never thrown.
- Transport failures (`IOException`, timeouts) become a `Response` with `statusCode = 499` and `error` set; `validate()` rethrows them.
- Cancelling the calling coroutine always propagates `CancellationException`, even if the engine reports it as an `IOException`.
- Empty bodies map to `body == null` (URLSession gives empty `Data`), so `decode<Unit>()` on a 204 returns `Unit`, while decoding any other model from a 204 throws `ApiError.Response`.
- The body's content type comes from the request's `Content-Type` header (any casing). `stringBody`/`dataBody` set none, so the adapter sends `application/octet-stream` when no `Content-Type` header is present. Add a header if the server cares.
- Request headers override client defaults such as `DefaultRequest` headers: the request's value replaces the default instead of being sent next to it.
- Response headers keep the order the server sent them, and lookup by name is case-insensitive (`headers["content-type"]` finds `Content-Type`).
- A `Content-Type` that Ktor cannot parse fails as `ApiError.Client` before any I/O (through the provider: `statusCode = 499`, `error` set).
- The adapter never closes the `HttpClient`: the caller owns it and must `close()` it.
- `dataBody` copies its input when called, so later changes to the source array do not affect the request; `body` arrays on `UrlRequest`, `UrlResponse` and `Response` must not be mutated.

On Android use the `OkHttp` or `Android` Ktor engine instead of `CIO`; nothing else changes.

## 4. Requests

```kotlin
provider.newBuilder(path = "/items/42", method = HttpMethod.Get)

provider.newBuilder(
    path = "/search",
    method = HttpMethod.Get,
    parameters = RequestParametersFactory.queryEncodableParameters(SearchFilter(page = 1, q = "kotlin")),
)

provider.newBuilder(
    path = "/login",
    method = HttpMethod.Post,
    body = RequestBodyFactory.formBody(mapOf("user" to "a", "password" to "b")),
)

RequestFactory.create("/me", HttpMethod.Get).authorised(bearer = "token")
```

## 5. Responses

```kotlin
val response = provider.request(baseURL, request)

response.validate()                     // throws the transport error (IOException, …)
response.decode<User>()                 // ApiError.Response if body == null, ApiError.Parser on bad JSON
response.decodeResult<User>()           // Result<User>, parser failures wrapped in ApiError.Parser
response.unauthenticated()              // throws ApiError.Unauthenticated on 401
response.unauthorized()                 // throws ApiError.Unauthorized on 403
response.validate(statusCode = 500)     // throws ApiError.StatusCode when the code IS 500 (Swift semantics)
response.log(::println)                 // full request/response dump
response.result()                       // Result<Response>
```

## 6. Cross-cutting concerns

```kotlin
// Auth header on every call
NetworkProviderFactory.create(service, modifiers = listOf(RequestModifierFactory.bearerToken(token)))

// Logging
InterceptorFactory.interceptorLogger(::println)
InterceptorFactory.interceptorTimeLogger(::println)

// Bearer auth with single-flight token refresh
val auth =
    InterceptorFactory.bearerAuth(
        token = { tokenStore.accessToken },
        refresh = { failedToken -> authApi.refresh(failedToken)?.also { tokenStore.accessToken = it } },
    )
NetworkProviderFactory.create(service, interceptors = listOf(auth))
```

`refresh` must store the new token so that later `token()` calls return it. Concurrent 401s for the same token
trigger a single refresh, each request is retried at most once, and a `null` refresh result returns the original 401.

## 7. Testing

`NetworkService` doubles as the test seam; no mocking framework:

```kotlin
val service = NetworkService { request ->
    assertEquals("https://api.test/items", request.url)
    UrlResponse(200, body = """[{"id":1}]""".encodeToByteArray())
}
val provider = NetworkProviderFactory.create(service)
```

For Ktor-level tests use `ktor-client-mock` (`MockEngine`), see `networking-middle-ktor/src/commonTest`.

## 8. Android

- Both `networking-middle` and `networking-middle-ktor` are Kotlin Multiplatform; Android uses their JVM target, compiled
  to Java 11 bytecode (the core also has Apple targets, which Android does not use). Add `implementation("io.github.amine2233:networking-middle-ktor:0.1.0")` plus a Ktor engine
  (`ktor-client-okhttp` or `ktor-client-android`). Maven consumers use the `-jvm` artifacts:
  `io.github.amine2233:networking-middle-jvm` and `io.github.amine2233:networking-middle-ktor-jvm`.
- Compiled with `jvmTarget = 11` and `-Xjdk-release=11`, so no JDK 17-only API (e.g. `URLEncoder.encode(String, Charset)`,
  API 33 on Android) can leak in. Safe on `minSdk 21+`.
- `NetworkService`, `Interceptor` and `RequestBody` are `fun interface`s with `suspend` members: trivial from Kotlin,
  impractical from Java. The library is Kotlin-first.
- Published to GitHub Packages by the release workflow; `./gradlew publishToMavenLocal -Pversion=x.y.z` for local testing.

## 9. Divergences from Swift (intentional)

| Swift                                   | Kotlin                                              | Why |
|-----------------------------------------|-----------------------------------------------------|-----|
| `RequestModifier.modify(inout Request)` | returns `Request`                                   | No `inout` in Kotlin. |
| `Request.set(_:forHttpHeaderKey:)`      | `Request.setHeader(key, value)`                     | Kotlin has no argument labels; value-before-key reads backwards. |
| `URLComponents` query encoding          | RFC 3986 percent-encoding: only the unreserved set stays literal, so `~` is left as is and `*`, `+`, `/`, `:` are encoded | Stricter and unambiguous; servers decode both. |
| `NetworkService.dataTask(...)` + `NetworkTask` | `suspend fun execute(...)`                   | Coroutines carry cancellation; no task handle needed. |
| `NetworkClientConfiguration`            | removed                                             | Empty struct in Swift → YAGNI. |
| `RequestError`                          | folded into `ApiError`                              | One error type per module. |
| `Response.headers: [AnyHashable: AnyCodable]` | `Map<String, List<String>>`                   | HTTP headers are multi-valued; no AnyCodable needed. |
| `RequestBodyFactory.jsonBody(any)`      | `jsonBody(JsonElement)`                             | No `JSONSerialization`; `JsonElement` is the typed equivalent. |
| `NetworkingMiddleTesting` robots        | not ported                                          | `NetworkService { … }` lambda covers stubbing. |
| `NetworkingMiddleMocking` (MockingBird) | not ported                                          | Ktor `MockEngine` covers it. |
| `NetworkingMiddlePinning`               | not ported                                          | Pinning is engine configuration in Ktor. |

## 10. Roadmap / next iterations

1. **Kotlin Multiplatform** — the core no longer uses JVM-only APIs (it has internal percent-encoding, URL parsing and
   seconds formatting) and both `networking-middle` and `networking-middle-ktor` are now Kotlin Multiplatform (Maven
   artifacts `networking-middle-jvm` and `networking-middle-ktor-jvm`). The core has `jvm`, `iosArm64`,
   `iosSimulatorArm64` and `macosArm64` targets; the Ktor adapter is JVM-only. Publishing the Apple artifacts is next.
2. **Maven Central** — GitHub Packages is wired; add signing + Sonatype only if a public registry is needed.
3. **Retry / backoff interceptor** in `InterceptorFactory`. The refresh-token interceptor already ships as `InterceptorFactory.bearerAuth`.
4. **Certificate pinning helper** for the Ktor OkHttp/CIO engines, mirroring `NetworkingMiddlePinning`.
5. **JSON bundle mocking** (MockingBird port) on top of `MockEngine` for offline UI tests.
6. **Structured logging** — accept a `kotlin.Logger`/SLF4J sink instead of `(String) -> Unit`, plus header redaction.
7. **Streaming bodies** — `RequestBody`/`Response` on `ByteReadChannel` for large uploads/downloads.
8. **Detekt** on top of the shared ktlint CI job.
9. **Header redaction** in `description` (the `Authorization` header is logged today, as in Swift).
10. **`Response.description` parity** — the Swift `QueryItems:` line is omitted; add it if log diffing against iOS matters.
