package fr.papycasu.ssoui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.stream.Stream;

/**
 * Detects which Guacamole SSO authentication extensions are present on the
 * server at runtime.
 *
 * Guacamole loads extensions with isolated classloaders, so Class.forName()
 * cannot reliably detect classes from sibling extension JARs. We therefore
 * combine classpath checks with scanning extension JAR filenames on disk.
 *
 * Supported SSO extensions:
 *   - guacamole-auth-sso-saml
 *   - guacamole-auth-sso-openid
 *   - guacamole-auth-sso-cas
 */
public class SSODetector {

    private static final Logger logger = LoggerFactory.getLogger(SSODetector.class);

    /**
    * Each entry describes a known SSO provider: its human-readable label,
    * a sentinel class for optional classpath detection, one or more filename
    * hints used for extension JAR detection on disk, and the relative URL
    * path that initiates the SSO login flow for that provider.
     */
    public enum SSOProvider {

        SAML(
            "SAML",
            "org.apache.guacamole.auth.saml.SAMLAuthenticationProvider",
            new String[] { "guacamole-auth-sso-saml" },
            "api/ext/saml/login"
        ),

        OPENID(
            "OpenID Connect",
            "org.apache.guacamole.auth.openid.OpenIDAuthenticationProvider",
            new String[] { "guacamole-auth-sso-openid" },
            "api/ext/openid/login"
        ),

        CAS(
            "CAS",
            "org.apache.guacamole.auth.cas.CASAuthenticationProvider",
            new String[] { "guacamole-auth-sso-cas" },
            "api/ext/cas/login"
        );

        /** Human-readable label shown on the login button. */
        public final String label;

        /** Fully-qualified class that exists only when this extension is loaded. */
        public final String sentinelClass;

        /** Lowercase substrings expected in extension JAR filenames. */
        public final String[] artifactHints;

        /** Server-relative URL that starts the SSO login flow. */
        public final String loginPath;

        SSOProvider(String label, String sentinelClass, String[] artifactHints, String loginPath) {
            this.label       = label;
            this.sentinelClass = sentinelClass;
            this.artifactHints = artifactHints;
            this.loginPath   = loginPath;
        }
    }

    /**
    * Returns an unmodifiable list of every SSO provider whose extension JAR
    * appears to be installed. The list is empty (never null) when no
    * supported SSO extensions are installed.
     *
     * @return loaded SSO providers, in declaration order
     */
    public static List<SSOProvider> getLoadedProviders() {
        List<SSOProvider> loaded = new ArrayList<>();
        List<Path> extensionDirs = getExtensionDirectories();

        for (SSOProvider provider : SSOProvider.values()) {
            if (isProviderDetected(provider, extensionDirs)) {
                loaded.add(provider);
            }
        }

        return Collections.unmodifiableList(loaded);
    }

    /**
     * Convenience method — returns true when at least one SSO extension is loaded.
     *
     * @return true if any SSO extension is present
     */
    public static boolean anySSOLoaded() {
        return !getLoadedProviders().isEmpty();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isProviderDetected(SSOProvider provider, List<Path> extensionDirs) {

        // First try classpath detection (works if classloader visibility allows it)
        if (isClassPresent(provider.sentinelClass)) {
            logger.info("[SSODetector] Detected {} via classpath: {}",
                    provider.label, provider.sentinelClass);
            return true;
        }

        // Fallback: detect by extension JAR filenames on disk
        for (Path dir : extensionDirs) {
            if (containsMatchingExtensionJar(dir, provider.artifactHints)) {
                logger.info("[SSODetector] Detected {} via extension directory: {}",
                        provider.label, dir);
                return true;
            }
        }

        logger.debug("[SSODetector] {} not detected (classpath + extension-dir scan)",
                provider.label);
        return false;
    }

    private static boolean containsMatchingExtensionJar(Path extensionDir, String[] artifactHints) {
        if (extensionDir == null || !Files.isDirectory(extensionDir)) {
            return false;
        }

        try (Stream<Path> paths = Files.walk(extensionDir, 3)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                    .filter(name -> name.endsWith(".jar"))
                    .anyMatch(name -> containsAnyHint(name, artifactHints));
        } catch (IOException e) {
            logger.debug("[SSODetector] Could not scan extension directory {}: {}",
                    extensionDir, e.getMessage());
            return false;
        }
    }

    private static boolean containsAnyHint(String text, String[] hints) {
        for (String hint : hints) {
            if (text.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> getExtensionDirectories() {
        Set<Path> dirs = new LinkedHashSet<>();

        String envGuacHome = System.getenv("GUACAMOLE_HOME");
        if (envGuacHome != null && !envGuacHome.isEmpty()) {
            dirs.add(Paths.get(envGuacHome, "extensions"));
        }

        String propGuacHome = System.getProperty("guacamole.home");
        if (propGuacHome != null && !propGuacHome.isEmpty()) {
            dirs.add(Paths.get(propGuacHome, "extensions"));
        }

        // Common defaults
        dirs.add(Paths.get("/etc/guacamole/extensions"));
        dirs.add(Paths.get("/opt/guacamole/extensions"));

        return new ArrayList<>(dirs);
    }

    // Utility class — no instances
    private SSODetector() {}
}
