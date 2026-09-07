# Test fixtures

All `.p12` files in this directory are **dummy, self-signed PKCS12 keystores generated with `keytool`**
solely for unit tests of `inter.sdk.commons.utils.SslUtils`. They are **not** Banco Inter certificates
and cannot authenticate against any real endpoint.

| File | Password | Purpose |
|------|----------|---------|
| `dummy-test-cert.p12` | `changeit` | Valid for ~100 years; happy path for `SslUtils.buildConnectionManager` |
| `dummy-expired-cert.p12` | `changeit` | Already expired; drives `CertificateExpiredException` |

Regenerate with:

```
keytool -genkeypair -alias dummy-test-cert -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore dummy-test-cert.p12 -storepass changeit -keypass changeit \
  -dname "CN=Dummy Test Fixture, O=NOT-A-REAL-BANCO-INTER-CERT, C=BR" -validity 36500
keytool -genkeypair -alias dummy-expired-cert -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore dummy-expired-cert.p12 -storepass changeit -keypass changeit \
  -dname "CN=Dummy Expired Fixture, O=NOT-A-REAL-BANCO-INTER-CERT, C=BR" -startdate -400d -validity 1
```
