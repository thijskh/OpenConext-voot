package voot.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import voot.util.UrnUtils;
import voot.valueobject.Group;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public abstract class AbstractProvider implements Provider {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractProvider.class);

  /*
   * We can't share the RestTemplate among Providers as we tie the BasicCredentialsProvider with the configured user / password
   * and those are Provider specific
   */
  protected final RestTemplate restTemplate;
  protected final Configuration configuration;
  protected final String groupIdPrefix;

  /*
   * ObjectMapper is thread-safe (http://wiki.fasterxml.com/JacksonFAQ)
   */
  protected static final ObjectMapper objectMapper = new ObjectMapper().
    enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY).
    setSerializationInclusion(JsonInclude.Include.NON_NULL);


  public AbstractProvider(Configuration configuration) {
    this.configuration = configuration;
    try {
      this.restTemplate = new RestTemplate(getRequestFactory());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
    this.groupIdPrefix = String.format("urn:collab:group:%s:", configuration.schacHomeOrganization);
    LOG.debug("Initializing {} {}", getClass(), configuration);
  }

  @Override
  public boolean isExternalGroupProvider() {
    return !configuration.type.equals(GroupProviderType.GROUPER);
  }

  @Override
  public boolean shouldBeQueriedForGroup(String groupId) {
    Matcher matcher = UrnUtils.GROUP_PATTERN.matcher(groupId);
    /*
     * For unqualified group names we do NOT query External Group Providers. This is a design decision discussed with SURFnet
     */
    return matcher.matches() && matcher.group(1).equals(configuration.schacHomeOrganization);
  }

  @Override
  public boolean shouldBeQueriedForMembers(String groupId) {
    return !isExternalGroupProvider();
  }

  protected <T> T parseJson(String json, Class<T> t) {
    try {
      return objectMapper.readValue(json, t);
    } catch (IOException e) {
      throw new RuntimeException("Error parsing Json", e);
    }
  }

  private ClientHttpRequestFactory getRequestFactory() throws MalformedURLException {
    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().evictExpiredConnections().evictIdleConnections(10l, TimeUnit.SECONDS);
    BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
    basicCredentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(configuration.credentials.username, configuration.credentials.password));
    httpClientBuilder.setDefaultCredentialsProvider(basicCredentialsProvider);
    httpClientBuilder.setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(configuration.timeOutMillis).setConnectTimeout(configuration.timeOutMillis).setSocketTimeout(configuration.timeOutMillis).build());

    CloseableHttpClient httpClient = httpClientBuilder.build();
    return new PreemptiveAuthenticationHttpComponentsClientHttpRequestFactory(httpClient, configuration.url);
  }

  @Override
  public String toString() {
    return String.format("Provider with configuration: %s", this.configuration);
  }
}
