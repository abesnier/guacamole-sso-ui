# guacamole-sso-ui

An Apache Guacamole extension that injects SSO login buttons into the login
page whenever one or more of the official SSO authentication extensions are
detected at runtime.

## Supported SSO extensions

| Extension JAR                        | Button label   | Login endpoint          |
|--------------------------------------|----------------|-------------------------|
| `guacamole-auth-sso-saml-*.jar`      | SAML           | `/api/ext/saml/login`   |
| `guacamole-auth-sso-openid-*.jar`    | OpenID Connect | `/api/ext/openid/login` |
| `guacamole-auth-sso-cas-*.jar`       | CAS            | `/api/ext/cas/login`    |

If none of the above are installed, this extension is completely silent — it
loads successfully but renders nothing.

---

## Project structure

```
guacamole-sso-ui/
├── pom.xml
└── src/main/
   ├── java/fr/papycasu/ssoui/
    │   ├── SSOUIExtension.java        ← AuthenticationProvider entry point
    │   ├── SSODetector.java           ← Classpath detection of SSO extensions
    │   └── SSOProvidersResource.java  ← GET /api/ext/sso-ui/providers
    └── resources/
        ├── META-INF/services/
        │   └── org.apache.guacamole.net.auth.AuthenticationProvider
      └── fr/papycasu/ssoui/templates/
            ├── sso-login-patch.html   ← HTML injected below the login form
            └── sso-login.js           ← AngularJS controller + CSS
```

---

## How it works

1. **Detection** — `SSODetector` iterates over the three known SSO provider
   sentinel classes and calls `Class.forName()` for each. If the class is on
   the classpath, the corresponding extension JAR is installed.

2. **REST endpoint** — `SSOProvidersResource` exposes
   `GET /api/ext/sso-ui/providers` which returns a JSON array of the detected
   providers.  The frontend calls this endpoint at page load so it never needs
   a hard-coded list of providers.

3. **UI patch** — `sso-login-patch.html` (an AngularJS template) is injected
   below the default login form via Guacamole's HTML patch mechanism.
   The `SSOLoginController` fetches the providers list and renders one button
   per provider using `ng-repeat`.  Nothing is rendered when the array is empty.

4. **Login redirect** — clicking a button calls `vm.login(provider)`, which
   redirects the browser to the provider's login initiation URL
   (e.g. `/api/ext/saml/login`).  The current page URL is forwarded as a
   `redirect` parameter so the SSO extension can return the user after auth.

---

## Build

### Prerequisites
- Java 11+
- Maven 3.6+
- The target Guacamole server version must match `guacamole.version` in `pom.xml`
   (default: **1.6.0**).

```bash
# Clone / enter the project
cd guacamole-sso-ui

# Build
mvn clean package

# Output JAR
ls target/guacamole-sso-ui-1.0.0.jar
```

---

## Deployment

1. Copy the JAR to your Guacamole extensions directory:

```bash
cp target/guacamole-sso-ui-1.0.0.jar /etc/guacamole/extensions/
```

2. Make sure at least one SSO extension JAR is also present in the same
   directory, e.g.:

```bash
ls /etc/guacamole/extensions/
# guacamole-auth-sso-saml-1.6.0.jar
# guacamole-sso-ui-1.0.0.jar
```

This extension shouyld be loaded AFTER the SSO extensions. Make sure this is set in your guacamole.properties, e.g.:

```bash
extension-priority=*,openid,sso-ui
```

3. Restart Guacamole (`guacd` + the web application):

```bash
systemctl restart tomcat9   # or your application server
```

4. Open the login page — you should see the SSO button(s) below the form.

---

## Optional configuration (`guacamole.properties`)

```properties
# Override the divider / button label (optional)
sso-ui-button-label: Sign in with Corporate SSO
```

---

## Extending to new SSO providers

Add a new entry to the `SSOProvider` enum in `SSODetector.java`:

```java
MY_PROVIDER(
    "My Provider",                                         // button label
    "com.example.auth.myprovider.MyAuthenticationProvider", // sentinel class
    "/api/ext/myprovider/login"                            // login endpoint
),
```

No other changes are required — the detection, REST endpoint, and UI all adapt
automatically.

---

## Compatibility

Targeted at Guacamole **1.6.0+**. For newer versions, keep
`guacamole.version` in `pom.xml` aligned with your server and verify that the
SSO provider sentinel class names still match the installed extension JARs.
