package fr.papycasu.ssoui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JAX-RS resource exposed at:
 *   GET /api/ext/sso-ui/providers
 *
 * Returns a JSON array describing each loaded SSO provider so the AngularJS
 * login-page patch can render the correct buttons dynamically, without any
 * hard-coded provider list in the frontend.
 *
 * Example response:
 * [
 *   { "id": "SAML",   "label": "SAML",          "loginPath": "/api/ext/saml/login"   },
 *   { "id": "OPENID", "label": "OpenID Connect", "loginPath": "/api/ext/openid/login" }
 * ]
 */
@Path("/")
public class SSOProvidersResource {

    private static final Logger logger = LoggerFactory.getLogger(SSOProvidersResource.class);

    private final SSOUIExtension extension;

    public SSOProvidersResource(SSOUIExtension extension) {
        this.extension = extension;
    }

    // -------------------------------------------------------------------------
    // DTO — serialised to JSON by Jackson (already on Guacamole's classpath)
    // -------------------------------------------------------------------------

    public static class ProviderDTO {
        public final String id;
        public final String label;
        public final String loginPath;

        public ProviderDTO(String id, String label, String loginPath) {
            this.id        = id;
            this.label     = label;
            this.loginPath = loginPath;
        }
    }

    // -------------------------------------------------------------------------
    // Endpoint
    // -------------------------------------------------------------------------

    /**
     * Returns the list of SSO providers that were detected when the extension
     * started. The list is empty — not a 404 — when no SSO extensions are
     * installed, so the frontend can safely call this endpoint unconditionally.
     */
    private List<ProviderDTO> buildProviders() {
        List<SSODetector.SSOProvider> providers = extension.getLoadedProviders();

        logger.debug("[SSOProvidersResource] Returning {} provider(s)", providers.size());

        return providers.stream()
                .map(p -> new ProviderDTO(p.name(), p.label, p.loginPath))
                .collect(Collectors.toList());
    }

    /**
     * Primary endpoint for provider discovery.
     */
    @GET
    @Path("providers")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProviderDTO> getProviders() {
        return buildProviders();
    }

    /**
     * Compatibility endpoint for environments that mount extension resources
     * directly at the extension root.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProviderDTO> getProvidersAtRoot() {
        return buildProviders();
    }
}
