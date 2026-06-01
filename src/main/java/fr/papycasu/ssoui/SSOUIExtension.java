package fr.papycasu.ssoui;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.simple.SimpleAuthenticationProvider;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Main entry point for the Guacamole SSO UI extension.
 *
 * This extension does NOT perform any authentication itself; it purely injects
 * UI resources (HTML + JS) into the Guacamole login page whenever one or more
 * SSO authentication extensions (SAML, OpenID, CAS) are detected on the
 * classpath.  It also exposes a small REST endpoint so the frontend can query
 * which providers are active at runtime.
 *
 * Deployment:
 *   Copy the built JAR to GUACAMOLE_HOME/extensions/.
 *   Guacamole discovers it via the ServiceLoader registration in:
 *   META-INF/services/org.apache.guacamole.net.auth.AuthenticationProvider
 */
public class SSOUIExtension extends SimpleAuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(SSOUIExtension.class);

    /** Providers detected at startup — immutable after construction. */
    private final List<SSODetector.SSOProvider> loadedProviders;

    // ─────────────────────────────────────────────────────────────────────────
    // Extension lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    public SSOUIExtension() {
        loadedProviders = SSODetector.getLoadedProviders();

        if (loadedProviders.isEmpty()) {
            logger.info("[SSOUIExtension] No SSO extensions detected — "
                      + "SSO buttons will not be shown.");
        } else {
            String names = loadedProviders.stream()
                    .map(p -> p.label)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none");
            logger.info("[SSOUIExtension] Detected {} SSO provider(s): {}",
                    loadedProviders.size(), names);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Extension REST resource
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Object getResource() {
        return new SSOProvidersResource(this);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AuthenticationProvider identity
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String getIdentifier() {
        return "sso-ui";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pass-through auth/data — this extension never authenticates users itself
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Map<String, GuacamoleConfiguration> getAuthorizedConfigurations(Credentials credentials)
            throws GuacamoleException {
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors used by REST layer
    // ─────────────────────────────────────────────────────────────────────────

    public List<SSODetector.SSOProvider> getLoadedProviders() {
        return loadedProviders;
    }
}
