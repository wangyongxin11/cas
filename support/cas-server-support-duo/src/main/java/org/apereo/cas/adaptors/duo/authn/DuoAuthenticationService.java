package org.apereo.cas.adaptors.duo.authn;

import org.apache.commons.lang3.tuple.Pair;
import org.apereo.cas.authentication.Credential;

import java.io.Serializable;

/**
 * This is {@link DuoAuthenticationService}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
public interface DuoAuthenticationService extends Serializable {

    /**
     * Verify the authentication response from Duo.
     *
     * @param credential signed request token
     * @return authenticated user / verified response.
     * @throws Exception if response verification fails
     */
    Pair<Boolean, String> authenticate(Credential credential) throws Exception;

    /**
     * Ping provider.
     *
     * @return true /false.
     */
    boolean ping();

    /**
     * Gets api host.
     *
     * @return the api host
     */
    String getApiHost();

    /**
     * Sign request token.
     *
     * @param uid the uid
     * @return the signed token
     */
    String signRequestToken(String uid);
}
