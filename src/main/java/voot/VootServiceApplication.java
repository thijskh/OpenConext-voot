package voot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.authentication.BearerTokenExtractor;
import org.springframework.security.oauth2.provider.authentication.TokenExtractor;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import voot.authz.AuthzResourceServerTokenServices;
import voot.authz.AuthzSchacHomeAwareUserAuthenticationConverter;
import voot.oauth.CachedRemoteTokenServices;
import voot.oauth.CompositeDecisionResourceServerTokenServices;
import voot.oauth.DecisionResourceServerTokenServices;
import voot.oidc.OidcRemoteTokenServices;
import voot.provider.*;

import javax.servlet.http.HttpServletRequest;

import static org.springframework.util.StringUtils.trimTrailingCharacter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@SpringBootApplication()
public class VootServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(VootServiceApplication.class, args);
  }

  @Autowired
  private ResourceLoader resourceLoader;

  @Bean
  @Autowired
  public ExternalGroupsService externalGroupsService(
    @Value("${externalProviders.config.path}") final String configFileLocation) throws IOException {

    Yaml yaml = new Yaml(new SafeConstructor());

    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, Object>>> config = (Map<String, List<Map<String, Object>>>) yaml.load(resourceLoader.getResource(configFileLocation).getInputStream());
    List<Map<String, Object>> externalGroupProviders = config.get("externalGroupProviders");

    List<Provider> groupClients = externalGroupProviders.stream().map(entryMap -> {
      String type = (String) entryMap.get("type");
      GroupProviderType groupProviderType = GroupProviderType.valueOf(type.toUpperCase());
      @SuppressWarnings("unchecked")
      Map<String, Object> rawCredentials = (Map<String, Object>) entryMap.get("credentials");

      Provider.Configuration configuration = Provider.Configuration.builder()
          .setType(groupProviderType)
          .setUrl(trimTrailingCharacter((String) entryMap.get("url"), '/'))
          .setCredentials((String) rawCredentials.get("username"), (String) rawCredentials.get("secret"))
          .setTimeOutMillis((Integer) entryMap.get("timeoutMillis"))
          .setSchacHomeOrganization((String) entryMap.get("schacHomeOrganization"))
          .setName((String) entryMap.get("name"))
          .setSupportsGetMembers(Optional.ofNullable((Boolean) entryMap.get("supportsGetMembers")).orElse(false))
          .build();

      switch (groupProviderType) {
        case VOOT2:
          return new Voot2Provider(configuration);
        case OPEN_SOCIAL:
          return new OpenSocialClient(configuration);
        case GROUPER:
          return new GrouperSoapClient(configuration);
        default:
          throw new IllegalArgumentException("Unknown external provider-type: " + type);
      }
    }).collect(Collectors.toList());

    return new ExternalGroupsService(groupClients);
  }

  @Configuration
  @EnableResourceServer
  @EnableWebSecurity
  protected static class ResourceServerConfiguration extends ResourceServerConfigurerAdapter {

    @Value("${authz.checkToken.endpoint.url}")
    private String authzCheckTokenEndpointUrl;

    @Value("${authz.checkToken.clientId}")
    private String authzCheckTokenClientId;

    @Value("${authz.checkToken.secret}")
    private String authzCheckTokenSecret;

    @Value("${oidc.checkToken.endpoint.url}")
    private String oidcCheckTokenEndpointUrl;

    @Value("${oidc.checkToken.clientId}")
    private String oidcCheckTokenClientId;

    @Value("${oidc.checkToken.secret}")
    private String oidcCheckTokenSecret;

    @Value("${checkToken.cache}")
    private boolean checkTokenCache;

    @Value("${checkToken.cache.duration.milliSeconds}")
    private int checkTokenCacheDurationMilliseconds;

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) {
      resources.resourceId("groups").tokenServices(resourceServerTokenServices()).tokenExtractor(tokenExtractor());
    }

    private DecisionResourceServerTokenServices resourceServerTokenServices() {
      CompositeDecisionResourceServerTokenServices tokenServices = new CompositeDecisionResourceServerTokenServices(
        Arrays.asList(oidcResourceServerTokenServices(), authzResourceServerTokenServices())
      );
      return checkTokenCache ?
        new CachedRemoteTokenServices(tokenServices, checkTokenCacheDurationMilliseconds, checkTokenCacheDurationMilliseconds) :
        tokenServices;
    }

    private DecisionResourceServerTokenServices oidcResourceServerTokenServices() {
      return new OidcRemoteTokenServices(oidcCheckTokenEndpointUrl, oidcCheckTokenClientId, oidcCheckTokenSecret);
    }

    private DecisionResourceServerTokenServices authzResourceServerTokenServices() {
      final DefaultAccessTokenConverter accessTokenConverter = new DefaultAccessTokenConverter();
      accessTokenConverter.setUserTokenConverter(new AuthzSchacHomeAwareUserAuthenticationConverter());
      return new AuthzResourceServerTokenServices(authzCheckTokenClientId, authzCheckTokenSecret, authzCheckTokenEndpointUrl, accessTokenConverter);
    }

    /*
     * Voot Service: explicitly deny other means of supplying oauth token than "bearer"
     */
    private TokenExtractor tokenExtractor() {
      return new BearerTokenExtractor() {
        protected String extractToken(HttpServletRequest request) {
          // only check the header...
          return extractHeaderToken(request);
        }
      };
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
      http
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.NEVER)
        .and()
        .authorizeRequests()
        .antMatchers("/me/**", "groups/**", "internal/**").access("#oauth2.hasScope('groups')")
        .antMatchers("/public/**", "/health/**", "/info/**").permitAll()
        .antMatchers("/**").hasRole("USER");

    }

  }
}
