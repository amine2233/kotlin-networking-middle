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
| `networking-middle-ktor` | `NetworkServiceKtor` — the only place that imports Ktor (Kotlin Multiplatform, JVM, Android and the same Apple targets as the core) | `networking-middle`, ktor-client-core |
| `sample`                 | Console app hitting httpbin.org through Ktor CIO              | both                                |

Both `networking-middle` and `networking-middle-ktor` also build for iOS and macOS on arm64 (`iosArm64`,
`iosSimulatorArm64`, `macosArm64`). The Apple artifacts of both modules are not published yet (item 14, issue #14).

## Toolchain

Managed by [mise](https://mise.jdx.dev) (JDK, Node, ktlint) — `mise trust && mise install`, then:

```sh
mise run test                  # unit tests
mise run test-coverage         # unit tests + merged Kover report (build/reports/kover/html) and coverage check
mise run lint                  # ktlint
mise run format                # ktlint --format
mise run build                 # full Gradle build
mise run build_documentations  # aggregated Dokka HTML docs (build/dokka/html)
mise run sample                # run the Ktor sample
```

Building also needs an Android SDK (the libraries have an Android target, `compileSdk 36`). AGP finds it through
`ANDROID_HOME` or an `sdk.dir` entry in a `local.properties` file at the repo root; `local.properties` is git-ignored and
must not be committed.

`./gradlew build` checks the public API of `networking-middle` and `networking-middle-ktor` against the committed
`api/jvm/*.api` and `api/android/*.api` dumps (Kotlin Gradle plugin ABI validation). Both modules also have a klib
dump for their Apple targets (`api/*.klib.api`). For an intended API change, run `./gradlew updateKotlinAbi`
and commit the updated dumps together with the change.

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

Gradle consumers depend on `networking-middle` / `networking-middle-ktor` as above; both modules are Kotlin
Multiplatform with JVM and Android targets, and Gradle picks the JVM or Android variant from their metadata. Maven consumers use
`io.github.amine2233:networking-middle-jvm` for the core and `io.github.amine2233:networking-middle-ktor-jvm` for the
Ktor adapter.

## CI/CD

CI and releases come from [kotlin-ci-shared](https://github.com/amine2233/kotlin-ci-shared).
All logic lives in `mise.toml`:

- `mise run test-coverage` / `mise run lint` — what the `CI` workflow runs on pull requests.
- The `apple-tests` job of the `CI` workflow runs `./gradlew :networking-middle:allTests :networking-middle-ktor:allTests`
  on a macOS arm64 runner (JVM, macOS and iOS simulator tests for both modules) and caches `~/.konan`.
- The `android-tests` job of the `CI` workflow runs the Android host tests of both libraries, checks that the
  `android-consumer` fixture resolves their Android variants and assembles the AARs, using the Android SDK preinstalled
  on `ubuntu-latest`.
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

The library ships no Ktor engine: the consumer adds the engine for its platform and passes the `HttpClient` in.

| Platform    | Engine           | Artifact                                     |
|-------------|------------------|----------------------------------------------|
| iOS / macOS | Darwin           | `ktor-client-darwin`                         |
| Android     | OkHttp / Android | `ktor-client-okhttp` / `ktor-client-android` |
| JVM server  | CIO / Java       | `ktor-client-cio` / `ktor-client-java`       |

On iOS and macOS:

```kotlin
val client = HttpClient(Darwin)
val provider = NetworkProviderFactory.create(networkService = client.asNetworkService())
```

Full walkthrough, API mapping and roadmap: [docs/USAGE.md](docs/USAGE.md).

## Testing your own code

`NetworkService` is a `fun interface`, so a fake is one line — no mocking library needed:

```kotlin
val provider = NetworkProviderFactory.create(NetworkService { UrlResponse(200, body = """{"id":1}""".encodeToByteArray()) })
```

## Android

Kotlin Multiplatform core and Ktor adapter with an Android target: each module publishes an AAR
(`networking-middle-android`, `networking-middle-ktor-android`) built with `minSdk 24` and Java 11 bytecode, with no
`java.time`/`java.net.http`. Gradle Android consumers depend on `networking-middle-ktor` as usual and get the AAR
automatically from the module metadata. Add your own Ktor engine (`ktor-client-okhttp` or `ktor-client-android`); see
[docs/USAGE.md](docs/USAGE.md#android).
