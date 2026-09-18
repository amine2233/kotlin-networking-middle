# kotlin-networking-middle

Kotlin port of the Swift `NetworkingMiddle` package: a small **chain-of-responsibility** layer on top of an
injected HTTP engine. The core module has **no HTTP dependency**; Ktor is plugged in through `networking-middle-ktor`.

```
Request ──▶ RequestModifier* ──▶ Interceptor₁ ──▶ Interceptor₂ ──▶ … ──▶ NetworkService (Ktor) ──▶ Response
                                     ◀────────────── response flows back through the interceptors ◀──────────────
```

| Module                   | Contents                                                     | Depends on                          |
|--------------------------|--------------------------------------------------------------|-------------------------------------|
| `networking-middle`      | `Request`, `Response`, `NetworkProvider`, modifiers, interceptors, `NetworkService` contract | kotlinx-coroutines, kotlinx-serialization-json |
| `networking-middle-ktor` | `NetworkServiceKtor` — the only place that imports Ktor       | `networking-middle`, ktor-client-core |
| `sample`                 | Console app hitting httpbin.org through Ktor CIO              | both                                |

## Toolchain

Managed by [mise](https://mise.jdx.dev) (JDK, Node, ktlint) — `mise trust && mise install`, then:

```sh
mise run test     # unit tests
mise run lint     # ktlint
mise run format   # ktlint --format
mise run build    # full Gradle build
mise run sample   # run the Ktor sample
```

## Installation

Artifacts are published to GitHub Packages (`io.github.amine2233:networking-middle` and
`io.github.amine2233:networking-middle-ktor`):

```kotlin
repositories {
    maven("https://maven.pkg.github.com/amine2233/kotlin-networking-middle") {
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")   // read:packages
        }
    }
}
dependencies {
    implementation("io.github.amine2233:networking-middle-ktor:<version>")
}
```

## CI/CD

CI and releases come from [kotlin-ci-shared](https://github.com/amine2233/kotlin-ci-shared).
All logic lives in `mise.toml`:

- `mise run test` / `mise run lint` — what the `CI` workflow runs on pull requests.
- Releases are manual: run the `Release` workflow from the Actions tab on `main`. semantic-release reads the
  `feat:` / `fix:` commits since the last tag, tags `vX.Y.Z`, updates `CHANGELOG.md`, publishes
  `io.github.amine2233:networking-middle` and `io.github.amine2233:networking-middle-ktor` to GitHub Packages
  and creates the GitHub release with the jars attached.
- `mise run release --dry-run` previews the next version locally.

## Usage

```kotlin
val client = HttpClient(CIO)                                  // configure engine, timeouts, pinning here
val provider = NetworkProviderFactory.create(
    networkService = client.asNetworkService(),               // inverse injection: the core never sees Ktor
    modifiers = listOf(RequestModifierFactory.bearerToken("token")),
    interceptors = listOf(InterceptorFactory.interceptorTimeLogger(::println)),
).intercept { baseURL, request, next ->                        // ad-hoc interceptor
    next(baseURL, request).also { println(it.statusCode) }
}

val request = provider.newBuilder(
    path = "/users",
    method = HttpMethod.Post,
    parameters = RequestParametersFactory.queryParameters(mapOf("page" to "1")),
    body = RequestBodyFactory.encodableBody(CreateUser("amine")),   // kotlinx.serialization
)

val user: User = provider.request("https://api.example.com", request)
    .validate()          // rethrows transport errors
    .unauthenticated()   // throws ApiError.Unauthenticated on 401
    .decode()            // kotlinx.serialization, reified
```

Full walkthrough, API mapping and roadmap: [docs/USAGE.md](docs/USAGE.md).

## Testing your own code

`NetworkService` is a `fun interface`, so a fake is one line — no mocking library needed:

```kotlin
val provider = NetworkProviderFactory.create(NetworkService { UrlResponse(200, body = """{"id":1}""".encodeToByteArray()) })
```

## Android

Plain Kotlin/JVM library, Java 11 bytecode (`-Xjdk-release=11`), no `java.time`/`java.net.http`. Consume it from any Android module
with AGP 8+ (`minSdk 24+` recommended; see [docs/USAGE.md](docs/USAGE.md#android)).
