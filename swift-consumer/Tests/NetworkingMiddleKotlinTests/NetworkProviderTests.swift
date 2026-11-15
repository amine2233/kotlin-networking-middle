import Foundation
import NetworkingMiddleKotlin
import Testing

struct NetworkProviderTests {
    @Test func invalidBaseURLThrowsInvalidUrl() async throws {
        let provider = DarwinNetworkProviderFactory.shared.create(interceptors: [])
        let request = RequestFactory.shared.create(
            path: "/users",
            method: HttpMethod.companion.Get,
            parameters: nil,
            body: nil,
            headers: [:]
        )

        do {
            _ = try await provider.request(baseURL: "api.test", request: request)
            Issue.record("request returned instead of throwing ApiError.InvalidUrl")
        } catch {
            let kotlinException = try #require((error as NSError).kotlinException)
            #expect(kotlinException is ApiError.InvalidUrl)
        }
    }
}
