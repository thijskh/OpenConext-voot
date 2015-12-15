package voot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import voot.provider.AbstractProvider;
import voot.provider.GroupProviderType;
import voot.provider.Provider;
import voot.valueobject.Group;
import voot.valueobject.Member;
import voot.valueobject.Membership;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

public class MockProvider extends AbstractProvider {

  private static final Logger LOG = LoggerFactory.getLogger(MockProvider.class);
  public static final String SCHAC_HOME_ORGANIZATION = "example.org";

  public enum SimulationMode {Success, Timeout, Error }

  private final Long timeoutMillis;
  private final SimulationMode simulationMode;

  public MockProvider(Long timeoutMillis, SimulationMode simulationMode, GroupProviderType type) {
    super(
      Provider.Configuration.builder()
        .setType(type)
        .setUrl("https://localhost/some/path")
        .setCredentials("user", "password")
        .setTimeOutMillis(2000)
        .setSchacHomeOrganization(SCHAC_HOME_ORGANIZATION)
        .setName("example").build()
    );
    this.timeoutMillis = timeoutMillis;
    this.simulationMode = simulationMode;
  }

  @Override
  public boolean shouldBeQueriedForMemberships(String schacHomeOrganization) {
    return true;
  }

  @Override
  public boolean shouldBeQueriedForGroup(String groupId) {
    return true;
  }

  @Override
  public List<Group> getGroupMemberships(String uid) {
    switch (this.simulationMode) {
      case Timeout:
        try {
          Thread.sleep(timeoutMillis);
        } catch (InterruptedException e) {
        }
        LOG.debug("timed out");
        return Collections.emptyList();
      case Error:
        throw new RuntimeException("failed!");
      default: // Success
        LOG.debug("got result");
        return Collections.singletonList(defaultGroup("id"));
    }

  }

  @Override
  public Optional<Group> getGroupMembership(String uid, String groupId) {
    return Optional.of(defaultGroup(groupId));
  }

  private Group defaultGroup(String id) {
    return new Group(id, "came from" + this.toString(), "description", SCHAC_HOME_ORGANIZATION, Membership.defaultMembership);
  }

  @Override
  public boolean supportsGettingMembersOfGroup() {
    return false;
  }

  @Override
  public List<Member> getMembersOfGroup(String groupId) {
    return ImmutableList.of(new Member("id", "name"));
  }
}
