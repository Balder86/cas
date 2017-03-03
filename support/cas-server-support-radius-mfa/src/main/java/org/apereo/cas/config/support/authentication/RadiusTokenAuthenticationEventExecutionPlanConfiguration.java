package org.apereo.cas.config.support.authentication;

import org.apereo.cas.adaptors.radius.JRadiusServerImpl;
import org.apereo.cas.adaptors.radius.RadiusClientFactory;
import org.apereo.cas.adaptors.radius.RadiusProtocol;
import org.apereo.cas.adaptors.radius.RadiusServer;
import org.apereo.cas.adaptors.radius.authentication.RadiusMultifactorAuthenticationProvider;
import org.apereo.cas.adaptors.radius.authentication.RadiusTokenAuthenticationHandler;
import org.apereo.cas.authentication.metadata.AuthenticationContextAttributeMetaDataPopulator;
import org.apereo.cas.authentication.AuthenticationEventExecutionPlan;
import org.apereo.cas.authentication.AuthenticationMetaDataPopulator;
import org.apereo.cas.authentication.principal.DefaultPrincipalFactory;
import org.apereo.cas.authentication.principal.PrincipalFactory;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.model.support.mfa.MultifactorAuthenticationProperties;
import org.apereo.cas.services.DefaultMultifactorAuthenticationProviderBypass;
import org.apereo.cas.services.MultifactorAuthenticationProvider;
import org.apereo.cas.services.MultifactorAuthenticationProviderBypass;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.ticket.registry.TicketRegistrySupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * This is {@link RadiusTokenAuthenticationEventExecutionPlanConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Configuration("radiusTokenAuthenticationEventExecutionPlanConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
public class RadiusTokenAuthenticationEventExecutionPlanConfiguration implements AuthenticationEventExecutionPlanConfigurer {
    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier("defaultTicketRegistrySupport")
    private TicketRegistrySupport ticketRegistrySupport;

    @Autowired
    @Qualifier("servicesManager")
    private ServicesManager servicesManager;

    @RefreshScope
    @Bean
    public MultifactorAuthenticationProvider radiusAuthenticationProvider() {
        final RadiusMultifactorAuthenticationProvider p = new RadiusMultifactorAuthenticationProvider(radiusTokenAuthenticationHandler());
        p.setBypassEvaluator(radiusBypassEvaluator());
        p.setGlobalFailureMode(casProperties.getAuthn().getMfa().getGlobalFailureMode());
        p.setOrder(casProperties.getAuthn().getMfa().getRadius().getRank());
        p.setId(casProperties.getAuthn().getMfa().getRadius().getId());
        return p;
    }

    @Bean
    @RefreshScope
    public MultifactorAuthenticationProviderBypass radiusBypassEvaluator() {
        return new DefaultMultifactorAuthenticationProviderBypass(
                casProperties.getAuthn().getMfa().getRadius().getBypass()
        );
    }

    @RefreshScope
    @Bean
    public List<RadiusServer> radiusTokenServers() {
        final List<RadiusServer> list = new ArrayList<>();
        final MultifactorAuthenticationProperties.Radius.Client client = casProperties.getAuthn().getMfa().getRadius().getClient();
        final MultifactorAuthenticationProperties.Radius.Server server = casProperties.getAuthn().getMfa().getRadius().getServer();

        final RadiusClientFactory factory = new RadiusClientFactory(client.getAccountingPort(), client.getAuthenticationPort(), client.getSocketTimeout(),
                client.getInetAddress(), client.getSharedSecret());

        final RadiusProtocol protocol = RadiusProtocol.valueOf(server.getProtocol());
        final JRadiusServerImpl impl = new JRadiusServerImpl(protocol, factory, server.getRetries(), server.getNasIpAddress(), server.getNasIpv6Address(),
                server.getNasPort(), server.getNasPortId(), server.getNasIdentifier(), server.getNasRealPort());

        list.add(impl);
        return list;
    }

    @ConditionalOnMissingBean(name = "radiusTokenPrincipalFactory")
    @Bean
    public PrincipalFactory radiusTokenPrincipalFactory() {
        return new DefaultPrincipalFactory();
    }

    @RefreshScope
    @Bean
    public RadiusTokenAuthenticationHandler radiusTokenAuthenticationHandler() {
        final MultifactorAuthenticationProperties.Radius radius = casProperties.getAuthn().getMfa().getRadius();
        final RadiusTokenAuthenticationHandler a = new RadiusTokenAuthenticationHandler(radius.getName(), radiusTokenServers(), radius.isFailoverOnException(),
                radius.isFailoverOnAuthenticationFailure());
        a.setPrincipalFactory(radiusTokenPrincipalFactory());
        a.setServicesManager(servicesManager);
        return a;
    }

    @Bean
    @RefreshScope
    public AuthenticationMetaDataPopulator radiusAuthenticationMetaDataPopulator() {
        final String attribute = casProperties.getAuthn().getMfa().getAuthenticationContextAttribute();
        return new AuthenticationContextAttributeMetaDataPopulator(attribute, radiusTokenAuthenticationHandler(), radiusAuthenticationProvider());
    }

    @Override
    public void configureAuthenticationExecutionPlan(final AuthenticationEventExecutionPlan plan) {
        plan.registerAuthenticationHandler(radiusTokenAuthenticationHandler());
        plan.registerMetadataPopulator(radiusAuthenticationMetaDataPopulator());
    }
}
