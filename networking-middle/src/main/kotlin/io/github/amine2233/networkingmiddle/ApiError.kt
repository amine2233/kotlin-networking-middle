package io.github.amine2233.networkingmiddle

/** The single error type of the library, mirroring the Swift `APIError`. */
public sealed class ApiError(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    public class Parser(
        cause: Throwable,
    ) : ApiError(cause.message, cause)

    public class RequestParameters(
        cause: Throwable,
    ) : ApiError(cause.message, cause)

    public class Body(
        cause: Throwable,
    ) : ApiError(cause.message, cause)

    public class Client(
        message: String,
    ) : ApiError(message)

    public class CreateQueryParameter(
        message: String,
    ) : ApiError(message)

    public class Response(
        message: String,
    ) : ApiError(message)

    public class Api(
        message: String?,
        public val statusCode: Int?,
    ) : ApiError(message)

    public class InvalidUrl(
        url: String,
    ) : ApiError("Can't create URL($url)")

    public class CertificatePinningFailed(
        message: String,
    ) : ApiError(message)

    public class Unknown : ApiError("Unknown error")

    public class Unauthorized : ApiError("Unauthorized (403)")

    public class Unauthenticated : ApiError("Unauthenticated (401)")

    public class StatusCode : ApiError("Invalid status code")
}
