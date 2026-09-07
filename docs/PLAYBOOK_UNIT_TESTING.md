# Playbook: Unit Testing `interpj-sdk-java`

## Overview

This playbook describes how unit tests are written, run and gated in this repository. The SDK is a thin
Java 8 client over the Banco Inter API: Lombok models, Jackson (de)serialization, Apache HttpClient 4.5
plus an OAuth token cache, and one `*Client` class per API area behind the `InterSdk` /
`BankingSdk` / `BillingSdk` / `PixSdk` facades.

The stack is **locked** and must not be migrated:

| Concern | Locked value |
|---------|--------------|
| Language level | Java 8 (`maven.compiler.source/target = 1.8`) |
| Test framework | JUnit 4.13.2 (`org.junit.*`, `@Before` / `@After`, `@Test(expected=...)`, `@Rule TemporaryFolder`) |
| Mocking | Mockito 3.12.4 `mockito-core` **+** `mockito-inline` 3.12.4 (enables `mockStatic` / `mockConstruction` on Java 8) |
| Other test libs | AssertJ 3.19.0, Awaitility 4.0.3, WireMock JRE8 2.27.2 (already declared; keep) |
| Coverage | JaCoCo 0.8.11 – `prepare-agent`, `report` (bound to `test`), `check` |
| Gate | BUNDLE `LINE COVEREDRATIO >= 0.95`, `BRANCH COVEREDRATIO >= 0.85` |
| Exclusions | **Only** `inter/functests/**` (interactive `System.in`-driven runner that hits the live API) |
| Lombok models | `inter/sdk/**/models/**` stay **inside** the gate and are covered by POJO/contract tests |
| Network | Never. `httpClient.execute(...)` must never reach a live endpoint; OAuth is never called for real |

## User Inputs

Before starting, know:

1. **Target class(es)** – which production class the test is for (utility, client, facade, model).
2. **Layer** – decides the mocking strategy (see Procedure §3).
3. **Whether the target *is* `SslUtils`** – only then are the real `.p12` fixtures used.
4. **Whether the class serialises a request body** – decides if the `IOException` catch block needs a
   simulated Jackson failure.
5. **Whether the class has pagination / optional filters / error branches** – each branch needs a test.

Run everything with Java 8:

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64   # adjust to the local JDK 8
mvn -q -DskipITs test                                  # unit tests + JaCoCo report
mvn -q -DskipITs test jacoco:check@check               # + enforce the 95 / 85 gate
```

## Procedure

### 1. Shared infrastructure (already in place, reuse it)

All under `src/test/java/inter/sdk/commons/`:

| Helper | Purpose |
|--------|---------|
| `TestStateReset` | Reflectively clears `TokenUtils.TOKEN_MAP` and nulls `HttpUtils.lastUrl` / `lastRequest`. Call `TestStateReset.resetAll()` from `@After` in every test touching `HttpUtils`, `TokenUtils`, `GetToken`, a `*Client` or a facade. |
| `TestFixtures` | Sandbox `Config` builder with dummy credentials, `validCertPath()`, `expiredCertPath()`, `DUMMY_CERT_PASSWORD`, `freshToken(...)`. |
| `ClientTestSupport` | Base class for `*Client` tests: opens `mockStatic(HttpUtils.class)` in `@Before`, closes it and resets state in `@After`; `onGet/onPost/onPut/onPatch/onDelete` stubs and `verifyGet/...` assertions with URL, scope, error message and body fragments; `assertWrapsParseFailure(...)` for malformed-JSON branches. |
| `JsonFailure` | `mockConstruction(ObjectMapper.class)` that makes `writerWithDefaultPrettyPrinter().writeValueAsString(..)` throw `JsonMappingException`, to exercise the `catch (IOException)` around request serialisation. |
| `ModelContractTester` | Reflection-driven POJO contract checker (no-args ctor, Lombok builder, getters/setters, `equals`/`hashCode`/`toString`, Jackson round trip) used by `ModelContractTest` for every `**/models/**` class. |

Fixtures under `src/test/resources/`:

* `dummy-test-cert.p12` – self-signed, ~100-year validity, password `changeit`.
* `dummy-expired-cert.p12` – self-signed, already expired, password `changeit`.
* `README-fixtures.md` – regeneration commands. **These are dummy `keytool` keystores, never a Banco Inter certificate.**

### 2. Create the test class

* Same package as the target, under `src/test/java`, name `<Target>Test`.
* JUnit 4 only: `import org.junit.Test;` `@Before` / `@After`; no `org.junit.jupiter`.
* Java 8 syntax only (no `var`, records, text blocks, `List.of`).

### 3. Pick the mocking strategy by layer

| Target | Strategy |
|--------|----------|
| `SslUtils` | **Real** fixtures: `buildConnectionManager(TestFixtures.validCertPath(), DUMMY_CERT_PASSWORD)`; missing path → `CertificateNotFoundException`; `expiredCertPath()` → `CertificateExpiredException`; wrong password → `SdkException`. Checked-exception catch blocks are reached with `mockStatic(SslUtils.class, CALLS_REAL_METHODS)` returning a `mock(KeyStore.class)` from `getKeyStore`, or `mockStatic(KeyStore.class)`. A 5-day certificate for the "close to expire" branch is generated at test time with `keytool` (guarded by `assumeTrue`). |
| `HttpUtils` | `mockStatic(TokenUtils.class)`, `mockStatic(SslUtils.class)`, `mockStatic(HttpClients.class)` returning a mocked `HttpClientBuilder` → mocked `CloseableHttpClient`. Responses are `mock(CloseableHttpResponse.class)` with a `BasicStatusLine` and a **`StringEntity`** (repeatable, so the body can be read more than once). |
| `GetToken` | `mockStatic(HttpClients.class)` + `mockStatic(SslUtils.class)`; capture the `HttpPost` and assert URL (`/oauth/v2/token`), `Content-Type: application/x-www-form-urlencoded`, and the `client_id` / `client_secret` / `grant_type` / `scope` form fields. |
| `TokenUtils` | `mockConstruction(GetToken.class)` (or `mockStatic`) for the fetch; seed `TestStateReset.tokenMap()` for cache-hit and near-expiry cases; `resetAll()` in `@After`. |
| `*Client` | Extend `ClientTestSupport`; stub `HttpUtils.call*` with `eq(config), eq(url), eq(scope), anyString()[, anyString()]`, call the client, assert URL / scope / error message / serialised body fragments and the parsed domain object. Cover: page-size vs. walk-all-pages, each optional filter, malformed JSON (`NOT_JSON`) and, where a request body is serialised, `JsonFailure.failingSerialization()`. |
| Facades (`InterSdk`, `BankingSdk`, `BillingSdk`, `PixSdk`, `PixWebhookSdk`) | `mockConstruction(<Client>.class)`; invoke each facade method twice on a fresh facade and assert exactly one client was constructed and the delegate was called twice with `config` + arguments (covers the lazy-create and reuse branches). `InterSdk` additionally mocks `SslUtils.isCloseToExpire`. |
| Models | Add the class to `ModelContractTest` (it is discovered automatically when it lives under `**/models/**`) or write a `GetTokenResponseTest`-style POJO test for anything special (custom JSON property names, `@JsonIgnore`, enums). |
| Enums / exceptions / constants | Plain JUnit 4 assertions over `values()`, `valueOf`, getters, messages and `Error` payloads. |

### 4. Keep the 60-second retry sleep out of the build

`HttpUtils.call` sleeps `SLEEP = 60000` ms before retrying a `429` when `rateLimitControl` is on.

* Mockito 3.12.4 **refuses** `mockStatic(Thread.class)` (`It is not possible to mock static methods of
  java.lang.Thread`). Do not try to no-op `Thread.sleep` that way.
* Instead pre-interrupt the test thread (`Thread.currentThread().interrupt()`) so `Thread.sleep` throws
  `InterruptedException` immediately, which `HttpUtils` wraps in a `RuntimeException`; assert the cause
  and that only one HTTP call happened. Clear the flag in `finally` (`Thread.interrupted()`).
* `handleResponse(..., 429, rateLimitControl=true)` is asserted directly to return `true`.
* Surefire has `forkedProcessTimeoutInSeconds=600` as a backstop; a run that takes minutes means a test
  blocked on the real sleep – fix the test, do not raise the timeout.

### 5. Run and read the gate

```bash
rm -rf target
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -o -DskipITs test jacoco:check@check
```

* `target/site/jacoco/index.html` and `target/site/jacoco/jacoco.xml` are produced by the `report`
  execution bound to `test`.
* If the gate fails, list uncovered lines per source file from `jacoco.xml` (`line[@ci=0]`) and add
  tests package by package; never lower the thresholds or add exclusions.

## Output Specification

A change is complete when all of the following hold:

1. `mvn -q -DskipITs test` compiles on Java 8 and finishes with `Failures: 0, Errors: 0`.
2. `mvn -DskipITs test jacoco:check@check` passes: bundle LINE ≥ 95 %, BRANCH ≥ 85 %.
3. `target/site/jacoco/index.html` and `target/site/jacoco/jacoco.xml` exist.
4. Every new test class is JUnit 4 style and Java 8 compatible.
5. Every test touching `HttpUtils` / `TokenUtils` / clients / facades calls `TestStateReset.resetAll()`
   in `@After` (directly or via `ClientTestSupport`).
6. No test opens a socket; no test waits on the 60 s retry sleep (whole suite runs in well under a minute of test time).
7. Only `inter/functests/**` is excluded from JaCoCo; `**/models/**` remain in the bundle.
8. Any new `.p12` fixture is a `keytool`-generated dummy documented in `src/test/resources/README-fixtures.md`.

## Forbidden Actions

* Migrating to JUnit 5 / Mockito 4+ / Mockito 5, or writing `org.junit.jupiter` tests.
* Raising `maven.compiler.source/target` above 1.8 or using post-Java-8 syntax in tests.
* Adding JaCoCo exclusions other than `inter/functests/**`, excluding model packages, or lowering the 95 / 85 thresholds.
* Committing a real Banco Inter certificate, real `clientId` / `clientSecret`, or any credential.
* Letting `httpClient.execute(...)` or `GetToken` reach a live endpoint (sandbox included).
* Sleeping for the real 60 s retry interval or raising `forkedProcessTimeoutInSeconds` to hide it.
* Editing production code merely to make it "more testable" without a functional reason.
* Modifying or deleting existing tests to make the gate pass.

## Advice and Pointers

* **Static mock argument matchers**: when stubbing `MockedStatic`, either use *all* raw values or *all*
  matchers. Mixing `config, url, anyString()` throws `InvalidUseOfMatchersException`; use
  `eq(config), eq(url), eq(scope), anyString()`.
* **`UnfinishedStubbingException`** with `mockStatic`: build return objects (e.g. mocked responses) into
  local variables *before* the `.thenReturn(...)` call; do not nest `mock(...)` inside `thenReturn`.
* **Lombok builders via reflection** (`ModelContractTester`): fetch the `builder()` method reflectively and
  `setAccessible(true)`; skip abstract classes; deduplicate inherited/shadowed fields by name.
* **JaCoCo and always-throwing calls**: JaCoCo is probe-based, so the invoke instruction of a call that
  always throws (`logAndThrowException(e)`, `Thread.sleep` under interruption) and the statement right
  after it are reported as missed even though they executed. In `HttpUtils` this leaves a handful of
  lines (`Thread.sleep`, the `return call(...)` after it, and the `logAndThrowException` invokes) as
  irreducible misses; they are already inside the 95 / 85 margin. Do not chase them with exclusions.
* **`JSON.simple` `ParseException.getMessage()` may be `null`** – compare the wrapped `SdkException`
  message / detail to the exception's values instead of asserting non-null.
* **Stack traces in the log** from `log.error(GENERIC_EXCEPTION_MESSAGE, e)` during malformed-JSON and
  serialisation-failure tests are expected output, not failures.
* **Maven offline**: `mvn -o` works once the plugins used by `test` + `jacoco:check` are cached;
  `clean` / `verify` may need the `maven-clean-plugin` / `maven-jar-plugin` fetched first.
* Logs: `InterSdk` creates a `logs/` directory in the working directory; tests may delete/recreate it.

## Examples

### `*Client` test (extends `ClientTestSupport`)

```java
public class BalanceClientTest extends ClientTestSupport {

    private static final String SCOPE = "extrato.read";
    private static final String URL = BASE + "/banking/v2/saldo";

    private final BalanceClient client = new BalanceClient();

    @Test
    public void shouldRetrieveBalanceForDate() throws Exception {
        onGet(URL + "?dataSaldo=2024-01-31", SCOPE, "{\"disponivel\":100.5}");

        Balance balance = client.retrieveBalance(config, "2024-01-31");

        assertEquals(new BigDecimal("100.5"), balance.getAvailable());
        verifyGet(URL + "?dataSaldo=2024-01-31", SCOPE, "Error retrieving balance");
    }

    @Test
    public void shouldWrapMalformedJson() {
        onGet(URL, SCOPE, NOT_JSON);
        assertWrapsParseFailure(() -> client.retrieveBalance(config, null));
    }
}
```

### Serialisation failure branch

```java
@Test
public void shouldWrapSerializationFailureOnCancel() {
    try (MockedConstruction<ObjectMapper> ignored = JsonFailure.failingSerialization()) {
        client.cancelBilling(config, "R1", "duplicate");
        fail();
    } catch (SdkException e) {
        assertEquals(CERTIFICATE_EXCEPTION_MESSAGE, e.getError().getTitle());
        assertEquals(JsonFailure.MESSAGE, e.getError().getDetail());
        http.verifyNoInteractions();
    }
}
```

### Facade delegation (lazy create + reuse)

```java
private void twice(Call call) throws SdkException {
    BillingSdk sdk = new BillingSdk(config);
    call.on(sdk);
    call.on(sdk);
}

@Test
public void shouldDelegateWebhookCalls() throws Exception {
    twice(s -> s.retrieveWebhook());
    twice(s -> s.deleteWebhook());

    assertEquals(2, webhooks.constructed().size());
    verify(webhooks.constructed().get(0), times(2)).retrieveWebhook(config);
    verify(webhooks.constructed().get(1), times(2)).deleteWebhook(config);
}
```

### 429 retry without the 60 s sleep

```java
@Test
public void callShouldEnterRetryBranchOn429WithoutSleepingSixtySeconds() throws Exception {
    Config rateLimited = TestFixtures.configBuilder().rateLimitControl(true).build();
    respondWith(response(429, "Too Many Requests", ""), response(200, "OK", "late"));
    Thread.currentThread().interrupt();
    try {
        HttpUtils.callGet(rateLimited, URL, SCOPE, MESSAGE);
        fail();
    } catch (RuntimeException e) {
        assertTrue(e.getCause() instanceof InterruptedException);
    } finally {
        assertFalse(Thread.interrupted());
    }
    verify(httpClient, times(1)).execute(any(HttpUriRequest.class));
}
```

### `SslUtils` with the real dummy fixture

```java
@Test
public void shouldThrowCertificateExpiredWhenCertificateIsExpired() {
    try {
        SslUtils.buildConnectionManager(TestFixtures.expiredCertPath(), DUMMY_CERT_PASSWORD);
        fail("expected CertificateExpiredException");
    } catch (CertificateExpiredException e) {
        assertEquals("Certificate expired", e.getError().getTitle());
    } catch (SdkException e) {
        fail("unexpected " + e);
    }
}
```
