package org.apereo.cas.services;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.AuthenticationHandler;
import org.apereo.cas.authentication.AuthenticationManager;
import org.apereo.cas.authentication.DefaultAuthenticationBuilder;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.configuration.model.support.mfa.MultifactorAuthenticationProperties;
import org.apereo.cas.ticket.registry.TicketRegistrySupport;
import org.apereo.cas.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This is {@link DefaultMultifactorAuthenticationProviderBypass}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public class DefaultMultifactorAuthenticationProviderBypass implements MultifactorAuthenticationProviderBypass {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMultifactorAuthenticationProviderBypass.class);
    private static final long serialVersionUID = 3720922341350004543L;

    private final MultifactorAuthenticationProperties.BaseProvider.Bypass bypass;

    private final TicketRegistrySupport ticketRegistrySupport;

    public DefaultMultifactorAuthenticationProviderBypass(final MultifactorAuthenticationProperties.BaseProvider.Bypass bypass,
                                                          final TicketRegistrySupport ticketRegistrySupport) {
        this.bypass = bypass;
        this.ticketRegistrySupport = ticketRegistrySupport;
    }

    @Override
    public boolean isAuthenticationRequestHonored(final Authentication authentication,
                                                  final RegisteredService registeredService,
                                                  final MultifactorAuthenticationProvider provider) {

        final Principal principal = authentication.getPrincipal();
        final boolean bypassByPrincipal = locateMatchingAttributeBasedOnPrincipalAttributes(bypass, principal);
        if (bypassByPrincipal) {
            LOGGER.debug("Bypass rules for principal {} indicate the request may be ignored", principal.getId());
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }

        final boolean bypassByAuthn = locateMatchingAttributeBasedOnAuthenticationAttributes(bypass, authentication);
        if (bypassByAuthn) {
            LOGGER.debug("Bypass rules for authentication {} indicate the request may be ignored", principal.getId());
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }

        final boolean bypassByAuthnMethod = locateMatchingAttributeValue(
                AuthenticationManager.AUTHENTICATION_METHOD_ATTRIBUTE,
                bypass.getAuthenticationMethodName(),
                authentication.getAttributes()
        );
        if (bypassByAuthnMethod) {
            LOGGER.debug("Bypass rules for authentication method {} indicate the request may be ignored", principal.getId());
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }

        final boolean bypassByHandlerName = locateMatchingAttributeValue(
                AuthenticationHandler.SUCCESSFUL_AUTHENTICATION_HANDLERS,
                bypass.getAuthenticationHandlerName(),
                authentication.getAttributes()
        );
        if (bypassByHandlerName) {
            LOGGER.debug("Bypass rules for authentication handlers {} indicate the request may be ignored", principal.getId());
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }

        final boolean bypassByCredType = locateMatchingCredentialType(authentication, bypass.getCredentialClassType());
        if (bypassByCredType) {
            LOGGER.debug("Bypass rules for credential types {} indicate the request may be ignored", principal.getId());
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }

        final boolean bypassByService =
                locateMatchingRegisteredServiceProperty(authentication, registeredService, AUTHENTICATION_ATTRIBUTE_BYPASS_MFA);
        if (bypassByService) {
            updateAuthenticationToRememberBypass(authentication, provider, principal);
            return false;
        }


        return true;
    }

    private void updateAuthenticationToRememberBypass(final Authentication authentication, final MultifactorAuthenticationProvider provider,
                                                      final Principal principal) {
        LOGGER.debug("Bypass rules for service {} indicate the request may be ignored", principal.getId());
        final Authentication newAuthn = DefaultAuthenticationBuilder.newInstance(authentication)
                .addAttribute(AUTHENTICATION_ATTRIBUTE_BYPASS_MFA, Boolean.TRUE)
                .addAttribute(AUTHENTICATION_ATTRIBUTE_BYPASS_MFA_PROVIDER, provider.getId())
                .build();
        LOGGER.debug("Updated authentication session to remember bypass for {} via {}", provider.getId(),
                AUTHENTICATION_ATTRIBUTE_BYPASS_MFA);
        authentication.update(newAuthn);
    }

    /**
     * Locate matching registered service property boolean.
     *
     * @param authentication    the authentication
     * @param registeredService the registered service
     * @param propertyName      the property name
     * @return the boolean
     */
    protected boolean locateMatchingRegisteredServiceProperty(final Authentication authentication,
                                                              final RegisteredService registeredService,
                                                              final String propertyName) {
        if (registeredService != null) {
            if (registeredService.getProperties().containsKey(propertyName)) {
                return registeredService.getProperties()
                        .get(propertyName)
                        .getValues()
                        .stream()
                        .filter(e -> StringUtils.equalsIgnoreCase(e, BooleanUtils.toStringYesNo(Boolean.TRUE))
                                || StringUtils.equalsIgnoreCase(e, BooleanUtils.toStringOnOff(Boolean.TRUE))
                                || StringUtils.equalsIgnoreCase(e, Boolean.TRUE.toString())).findAny()
                        .isPresent();

            }
        }
        return false;
    }

    /**
     * Locate matching credential type boolean.
     *
     * @param authentication      the authentication
     * @param credentialClassType the credential class type
     * @return the boolean
     */
    protected boolean locateMatchingCredentialType(final Authentication authentication, final String credentialClassType) {
        return StringUtils.isNotBlank(credentialClassType) && authentication.getCredentials().stream()
                .filter(e -> e.getCredentialClass().getName().matches(credentialClassType))
                .findAny()
                .isPresent();
    }

    /**
     * Skip bypass and support event based on authentication attributes.
     *
     * @param bypass the bypass
     * @param authn  the authn
     * @return the boolean
     */
    protected boolean locateMatchingAttributeBasedOnAuthenticationAttributes(
            final MultifactorAuthenticationProperties.BaseProvider.Bypass bypass, final Authentication authn) {
        return locateMatchingAttributeValue(bypass.getAuthenticationAttributeName(),
                bypass.getAuthenticationAttributeValue(), authn.getAttributes());
    }

    /**
     * Skip bypass and support event based on principal attributes.
     *
     * @param bypass    the bypass
     * @param principal the principal
     * @return the boolean
     */
    protected boolean locateMatchingAttributeBasedOnPrincipalAttributes(
            final MultifactorAuthenticationProperties.BaseProvider.Bypass bypass, final Principal principal) {
        return locateMatchingAttributeValue(bypass.getPrincipalAttributeName(),
                bypass.getAuthenticationAttributeValue(), principal.getAttributes());
    }

    /**
     * Evaluate attribute rules for bypass.
     *
     * @param attrName   the attr name
     * @param attrValue  the attr value
     * @param attributes the attributes
     * @return true a matching attribute name/value is found
     */
    protected boolean locateMatchingAttributeValue(final String attrName, final String attrValue,
                                                   final Map<String, Object> attributes) {
        boolean supports = false;
        if (StringUtils.isNotBlank(attrName)) {
            final Set<Map.Entry<String, Object>> names = attributes.entrySet().stream().filter(e ->
                    e.getKey().matches(attrName)
            ).collect(Collectors.toSet());

            supports = names.isEmpty();
            if (!names.isEmpty() && StringUtils.isNotBlank(attrValue)) {

                LOGGER.debug("Found {} attributes relevant for multifactor authentication bypass", names.size());
                final Set<Map.Entry<String, Object>> values = names.stream().filter(e -> {
                    final Set<Object> valuesCol = CollectionUtils.convertValueToCollection(e.getValue());
                    return valuesCol.stream()
                            .filter(v -> v.toString().matches(attrValue))
                            .findAny()
                            .isPresent();
                }).collect(Collectors.toSet());

                supports = values.isEmpty();

            }
        }
        return supports;
    }
}
