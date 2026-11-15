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
| `networking-middle-apple` | `NetworkingMiddleKotlin` static XCFramework for Swift and `DarwinNetworkProviderFactory` (Apple targets only, not published) | both, ktor-client-darwin |
| `sample`                 | Console app hitting httpbin.org through Ktor CIO              | both                                |

Both `networking-middle` and `networking-middle-ktor` also build for iOS and macOS on arm64 (`iosArm64`,
`iosSimulatorArm64`, `macosArm64`). Every target of both modules (JVM, Android and Apple) is published.

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
mise run verify-publish        # publish to build/local-maven-repo, check the metadata, compile kmp-consumer (macOS)
mise run verify-xcframework    # build the NetworkingMiddleKotlin XCFramework, check its slices, run swift-consumer tests (macOS)
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

GitHub Packages needs a token with the `read:packages` scope even to read public packages.

| Platform                | Core (`io.github.amine2233:`)         | Ktor adapter (`io.github.amine2233:`)      |
|-------------------------|---------------------------------------|--------------------------------------------|
| Gradle Multiplatform    | `networking-middle`                   | `networking-middle-ktor`                   |
| JVM                     | `networking-middle-jvm`               | `networking-middle-ktor-jvm`               |
| Android                 | `networking-middle-android`           | `networking-middle-ktor-android`           |
| iOS device (`iosArm64`) | `networking-middle-iosarm64`          | `networking-middle-ktor-iosarm64`          |
| iOS simulator           | `networking-middle-iossimulatorarm64` | `networking-middle-ktor-iossimulatorarm64` |
| macOS (`macosArm64`)    | `networking-middle-macosarm64`        | `networking-middle-ktor-macosarm64`        |

Gradle consumers (JVM, Android or Kotlin Multiplatform) depend on the root coordinate, `networking-middle` /
`networking-middle-ktor`, as above; Gradle picks the right target variant from the module metadata. The per-target ids
are for Maven or other non-Gradle tools.

## CI/CD

CI and releases come from [kotlin-ci-shared](https://github.com/amine2233/kotlin-ci-shared).
All logic lives in `mise.toml`:

- `mise run test-coverage` / `mise run lint` — what the `CI` workflow runs on pull requests.
- The `apple-tests` job of the `CI` workflow runs `./gradlew :networking-middle:allTests :networking-middle-ktor:allTests`
  on a macOS arm64 runner (JVM, macOS and iOS simulator tests for both modules) and caches `~/.konan`. It then runs
  `mise run verify-publish`: both modules are published to `build/local-maven-repo` with version `0.0.0-verify`, the
  artifact set and `.module` metadata are checked, and the standalone `kmp-consumer` fixture compiles for `jvm` and
  `iosSimulatorArm64` against them. Last, it runs `mise run verify-xcframework`: the release `NetworkingMiddleKotlin`
  XCFramework is built, its `ios-arm64`, `ios-arm64-simulator` and `macos-arm64` slices are checked, and the
  `swift-consumer` Swift Testing suite runs against it on macOS.
- The `android-tests` job of the `CI` workflow runs the Android host tests of both libraries, checks that the
  `android-consumer` fixture resolves their Android variants and assembles the AARs, using the Android SDK preinstalled
  on `ubuntu-latest`.
- Releases are manual: run the `Release` workflow from the Actions tab on `main`. The release job runs on a macOS
  arm64 runner, so one build publishes every target of both modules. semantic-release reads the `feat:` / `fix:`
  commits since the last tag, tags `vX.Y.Z`, updates `CHANGELOG.md`, publishes `io.github.amine2233:networking-middle`
  and `io.github.amine2233:networking-middle-ktor` with all their targets to GitHub Packages and creates the GitHub
  release with the two JVM jars attached (`networking-middle-jvm-X.Y.Z.jar`, `networking-middle-ktor-jvm-X.Y.Z.jar`).
- `mise run release --dry-run` previews the next version locally.

### First release

1. Run the `Release` workflow from the Actions tab on `main`.
2. Check that the GitHub release has the two `-jvm` jars and that GitHub Packages lists every artifact from the
   coordinates table in [Installation](#installation).
3. With a `read:packages` token, point a KMP consumer (for example a copy of `kmp-consumer` with the GitHub Packages
   repository instead of the local one) at the new version and run its `compileKotlinJvm` and
   `compileKotlinIosSimulatorArm64` tasks.

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

## Swift / XCFramework

`networking-middle-apple` packages both libraries into one static XCFramework for Swift callers. Build it locally on
macOS with:

```sh
mise run verify-xcframework   # or: ./gradlew :networking-middle-apple:assembleNetworkingMiddleKotlinReleaseXCFramework
```

The output lands in `networking-middle-apple/build/XCFrameworks/release/NetworkingMiddleKotlin.xcframework`, with
`ios-arm64`, `ios-arm64-simulator` and `macos-arm64` slices. The framework is named `NetworkingMiddleKotlin`, not
`NetworkingMiddle`, so it never clashes with the native Swift `NetworkingMiddle` package in the same app.

```swift
import NetworkingMiddleKotlin

let provider = DarwinNetworkProviderFactory.shared.create(interceptors: [])   // Ktor Darwin client, never closed
let request = RequestFactory.shared.create(
    path: "/users", method: HttpMethod.companion.Get, parameters: nil, body: nil, headers: [:]
)
do {
    let response = try await provider.request(baseURL: "https://api.example.com", request: request)
} catch {
    if (error as NSError).kotlinException is ApiError.InvalidUrl { /* bad base URL */ }
}
```

- Kotlin errors arrive in Swift as thrown `NSError`s. Their `kotlinException` is the `ApiError` subclass, or the
  transport `Throwable` that `validate()` rethrows.
- Cancelling the Swift task does not cancel the Kotlin call.
- Interceptors written in Swift are not supported; use the Kotlin `InterceptorFactory` ones.
- No released Swift package exists yet: build the XCFramework from source. `swift-consumer/` is a test fixture, not a
  shipped package.

See [docs/USAGE.md](docs/USAGE.md#9-swift--xcframework).

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
