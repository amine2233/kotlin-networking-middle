package io.github.amine2233.networkingmiddle.apple

import io.github.amine2233.networkingmiddle.Interceptor
import io.github.amine2233.networkingmiddle.NetworkProvider
import io.github.amine2233.networkingmiddle.NetworkProviderFactory
import io.github.amine2233.networkingmiddle.ktor.asNetworkService
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/** Builds [NetworkProvider]s backed by Ktor's Darwin engine, for Swift callers of the `NetworkingMiddleKotlin` framework. */
public object DarwinNetworkProviderFactory {
    /**
     * Returns a provider that sends requests through a new `HttpClient(Darwin)` and runs [interceptors] in order.
     *
     * The provider owns that client and never closes it: create one provider and keep it for the app's lifetime.
     */
    public fun create(interceptors: List<Interceptor> = emptyList()): NetworkProvider {
        val client = HttpClient(Darwin)
        return NetworkProviderFactory.create(networkService = client.asNetworkService(), interceptors = interceptors)
    }
}
