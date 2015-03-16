package voot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.authentication.BearerTokenExtractor;
import org.springframework.security.oauth2.provider.authentication.TokenExtractor;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import voot.oauth.SchacHomeAwareUserAuthenticationConverter;
import voot.provider.*;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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
    final List<Map<String, Object>> externalGroupProviders = config.get("externalGroupProviders");

    final List<Provider> groupClients = externalGroupProviders.stream().map(entryMap -> {
      final String type = (String) entryMap.get("type");
      final String url = (String) entryMap.get("url");
      final String schacHomeOrganization = (String) entryMap.get("schacHomeOrganization");
      final Integer timeoutMillis = (Integer) entryMap.get("timeoutMillis");

      final Map<String, Object> rawCredentials = (Map<String, Object>) entryMap.get("credentials");
      String username = (String) rawCredentials.get("username");
      String secret = (String) rawCredentials.get("secret");

      GroupProviderType groupProviderType = GroupProviderType.valueOf(type.toUpperCase());

      final Provider.Configuration configuration = new Provider.Configuration(groupProviderType, url, new Provider.Configuration.Credentials(username, secret), timeoutMillis, schacHomeOrganization);
      switch (groupProviderType) {
        case VOOT2:
          return new Voot2Client(configuration);
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
  protected static class ResourceServerConfiguration extends ResourceServerConfigurerAdapter {

    @Value("${vootservice.oauthResourceId}")
    private String resourceId;

    @Value("${oauth.checkToken.endpoint.url}")
    private String checkTokenEndpointUrl;

    @Value("${oauth.checkToken.clientId}")
    private String checkTokenClientId;

    @Value("${oauth.checkToken.secret}")
    private String checkTokenSecret;

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) {
      resources.resourceId(resourceId).tokenServices(tokenServices()).tokenExtractor(tokenExtractor());
    }

    private RemoteTokenServices tokenServices() {
      final RemoteTokenServices remoteTokenServices = new RemoteTokenServices();
      remoteTokenServices.setCheckTokenEndpointUrl(checkTokenEndpointUrl);
      remoteTokenServices.setClientId(checkTokenClientId);
      remoteTokenServices.setClientSecret(checkTokenSecret);

      final DefaultAccessTokenConverter accessTokenConverter = new DefaultAccessTokenConverter();
      accessTokenConverter.setUserTokenConverter(new SchacHomeAwareUserAuthenticationConverter());
      remoteTokenServices.setAccessTokenConverter(accessTokenConverter);

      return remoteTokenServices;
    }

    /*
     * Voot service: explicitly deny other means of supplying oauth token than "bearer"
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
        .antMatchers("/**").access("#oauth2.hasScope('read')");
    }

  }
}
