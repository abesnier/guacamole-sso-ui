/**
 * sso-login.js
 * ─────────────────────────────────────────────────────────────────────────────
 * AngularJS controller + CSS injected into the Guacamole login page by the
 * guacamole-sso-ui extension.
 *
 * Queries GET /api/ext/sso-ui/providers on load; renders buttons only when the
 * server returns at least one provider. Redirects the browser to the provider's
 * loginPath when a button is clicked (each SSO extension registers its own
 * initiation endpoint on the Guacamole server).
 */

/* ─── Styles ─────────────────────────────────────────────────────────────── */

(function injectStyles() {
    var css = `
        /* ── Divider ──────────────────────────────────────────────────────── */
        .sso-divider {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            margin: 1.5rem 0 1.25rem;
            color: #333;
            font-size: 0.8rem;
            letter-spacing: 0.05em;
            text-transform: uppercase;
        }
        .sso-divider__line {
            flex: 1;
            height: 1px;
            background: currentColor;
            opacity: 0.3;
        }
        .sso-divider__text {
            white-space: nowrap;
        }

        /* ── Button container ─────────────────────────────────────────────── */
        .sso-buttons {
            display: flex;
            flex-direction: column;
            gap: 0.6rem;
            margin-bottom: 0.5rem;
            margin-top: 0.75rem;
        }

        /*
         * Let Guacamole's native button rules define color, border, height,
         * and hover behavior so SSO buttons match the Login button.
         */
        .sso-btn {
            display: block;
            width: 100%;
            margin: 0;
        }
        .sso-btn:focus-visible {
            outline: 2px solid #4d9fec;
            outline-offset: 2px;
        }

        /* ── Error message ────────────────────────────────────────────────── */
        .sso-error {
            font-size: 0.8rem;
            color: #e07070;
            text-align: center;
            margin: 0.25rem 0 0;
        }
    `;

    var style = document.createElement('style');
    style.textContent = css;
    document.head.appendChild(style);
}());


/* ─── AngularJS controller ───────────────────────────────────────────────── */

angular.module('guacSsoUi', []);
angular.module('index').requires.push('guacSsoUi');

angular.module('guacSsoUi').controller('SSOLoginController', [
    '$http', '$window',
    function SSOLoginController($http, $window) {
        var vm = this;

        vm.providers = [];   // populated after the API call
        vm.error     = false;

        var contextPath = ($window.location.pathname || '/').replace(/\/$/, '');
        if (contextPath === '')
            contextPath = '/';

        function toAbsolute(path) {
            if (contextPath === '/')
                return '/' + path;
            return contextPath + '/' + path;
        }

        function setLoadError(err) {
            var suffix = '';
            if (err && err.status)
                suffix = ' (HTTP ' + err.status + ')';
            vm.error = 'SSO providers could not be loaded' + suffix + '.';
        }

        function fetchProvidersWithFallback() {
            return $http.get(toAbsolute('api/ext/sso-ui/providers'))
                ['catch'](function () {
                    // Compatibility fallback: extension root endpoint
                    return $http.get(toAbsolute('api/ext/sso-ui'));
                });
        }

        // ── Fetch the list of loaded SSO providers from our REST endpoint ──
        fetchProvidersWithFallback()
            .then(function (response) {
                vm.providers = response.data || [];
            })
            .catch(function (err) {
                console.error('[sso-ui] Failed to load SSO providers:', err);
                setLoadError(err);
            });

        /**
         * Redirect the browser to the SSO provider's login initiation URL.
         * Each Guacamole SSO extension registers its own endpoint
         * (e.g. /api/ext/saml/login) which then handles the redirect to the
         * IdP.
         *
         * We forward the current page URL as the "redirect" parameter so the
         * SSO extension can send the user back to the right place after auth.
         *
         * @param {Object} provider  One entry from vm.providers
         */
        vm.login = function (provider) {
            var loginPath = provider.loginPath || '';

            // Keep extension paths relative to Guacamole's servlet context.
            if (loginPath.indexOf('/api/') === 0)
                loginPath = loginPath.substring(1);

            var returnUrl = encodeURIComponent($window.location.href);
            $window.location.href = toAbsolute(loginPath) + '?redirect=' + returnUrl;
        };
    }
]);
