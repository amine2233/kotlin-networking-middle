package io.github.amine2233.networkingmiddle.androidconsumer

import io.github.amine2233.networkingmiddle.NetworkProvider
import io.github.amine2233.networkingmiddle.NetworkProviderFactory
import io.github.amine2233.networkingmiddle.ktor.asNetworkService
import io.ktor.client.HttpClient

fun networkProvider(client: HttpClient): NetworkProvider = NetworkProviderFactory.create(networkService = client.asNetworkService())
