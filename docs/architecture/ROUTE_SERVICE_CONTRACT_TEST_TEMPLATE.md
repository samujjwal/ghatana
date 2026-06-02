# Route Service Contract Test Template

## Purpose

Every stable route should have a route-service-contract test that validates:
1. The route follows the standard handler pattern
2. The route delegates to the correct application service
3. The route uses the correct policy facade
4. The route uses the correct response mapper
5. The route handles errors correctly

## Test Template

```java
package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.YourService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Route service contract tests for YourRoute.
 *
 * @doc.type class
 * @doc.purpose Route service contract validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YourRoute - service contract")
@ExtendWith(MockitoExtension.class)
class YourRouteServiceContractTest extends EventloopTestBase {

    @Mock
    private YourService yourService;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new YourRoute(eventloop(), yourService, policyEvaluator).getServlet();
        
        // Configure default mock behaviors
        lenient().when(policyEvaluator.canAccess(any(), anyString(), anyString(), anyString()))
            .thenAnswer(inv -> Promise.of(new PolicyDecision(true, "ALLOWED")));
    }

    @Nested
    @DisplayName("Service delegation")
    class ServiceDelegation {

        @Test
        @DisplayName("delegates to application service for valid request")
        void delegatesToApplicationService() throws Exception {
            String requestBody = """
                {
                  "patientId": "patient-001",
                  "accessorId": "accessor-001",
                  "scope": "view-records"
                }
                """;
            lenient().when(yourService.performAction(any()))
                .thenAnswer(inv -> Promise.of(new ServiceResult("result-id")));

            HttpRequest request = postRequest("/api/v1/your-endpoint", requestBody);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(yourService).performAction(any());
        }
    }

    @Nested
    @DisplayName("Policy enforcement")
    class PolicyEnforcement {

        @Test
        @DisplayName("denies access when policy decision is not allowed")
        void deniesAccessWhenPolicyDecisionIsNotAllowed() throws Exception {
            lenient().when(policyEvaluator.canAccess(any(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> Promise.of(new PolicyDecision(false, "POLICY_DENIED")));

            String requestBody = """
                {
                  "patientId": "patient-001",
                  "accessorId": "accessor-001",
                  "scope": "view-records"
                }
                """;
            HttpRequest request = postRequest("/api/v1/your-endpoint", requestBody);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("returns 400 for invalid request body")
        void returns400ForInvalidRequestBody() throws Exception {
            String requestBody = """
                {
                  "invalid": "data"
                }
                """;
            HttpRequest request = postRequest("/api/v1/your-endpoint", requestBody);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 500 when service fails")
        void returns500WhenServiceFails() throws Exception {
            lenient().when(yourService.performAction(any()))
                .thenAnswer(inv -> Promise.ofException(new RuntimeException("Service error")));

            String requestBody = """
                {
                  "patientId": "patient-001",
                  "accessorId": "accessor-001",
                  "scope": "view-records"
                }
                """;
            HttpRequest request = postRequest("/api/v1/your-endpoint", requestBody);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(500);
        }
    }

    private static HttpRequest postRequest(String path, String body) {
        return HttpRequest.builder(HttpMethod.POST, "http://localhost" + path)
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .withHeader(HttpHeaders.of("X-Session-Token"), "test-session")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }
}
```

## Validation Check

Run the validation check to ensure all stable routes have service contract tests:

```bash
node scripts/check-route-service-contract-tests.mjs
```

## Test Requirements

1. **Service Delegation**: Verify the route delegates to the correct application service
2. **Policy Enforcement**: Verify the route uses the policy facade for authorization
3. **Error Handling**: Verify the route handles validation errors and service failures
4. **Response Mapping**: Verify the route uses the correct response mapper
5. **Context Resolution**: Verify the route resolves Kernel context before processing

## Naming Convention

Test files should be named: `{RouteName}ServiceContractTest.java`

Example:
- `PhrConsentRoutesServiceContractTest.java`
- `PhrPatientRecordRoutesServiceContractTest.java`
- `PhrDashboardRoutesServiceContractTest.java`
