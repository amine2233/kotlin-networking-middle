package io.github.amine2233.networkingmiddle

/** Modifies a [Request] before it is executed (Swift used `inout`; Kotlin returns the new request). */
public fun interface RequestModifier {
    public fun modify(
        baseURL: String,
        request: Request,
    ): Request
}

public object RequestModifierFactory {
    public fun bearerToken(token: String): RequestModifier = RequestModifier { _, request -> request.authorised(token) }

    public fun token(token: String): RequestModifier = RequestModifier { _, request -> request.token(token) }

    public fun header(
        key: String,
        value: String,
    ): RequestModifier = RequestModifier { _, request -> request.setHeader(key, value) }
}
