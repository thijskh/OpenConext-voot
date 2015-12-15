package voot.provider;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Optional;

import com.google.common.base.MoreObjects;

import voot.valueobject.Group;
import voot.valueobject.Member;

public interface Provider {

  /**
   * Tells us if it is worthwhile calling this client when returning all groups for an user
   *
   * @param schacHomeOrganization the end-user's schacHomeOrg
   * @return true if this Provider can return groups for the specified schacHomeOrganization
   */
  boolean shouldBeQueriedForMemberships(String schacHomeOrganization);

  /**
   * Tells us if it is worthwhile calling this client when returning a single specified group for an user
   *
   * @param groupId the group being requested (must be fully qualified persistent name or the unqualified Institution name)
   * @return true if this Provider can return groups for the specified schacHomeOrganization
   */
  boolean shouldBeQueriedForGroup(String groupId);

  /**
   *
   * @return true if this Provider is external (e.g. not Grouper)
   */
  boolean isExternalGroupProvider();

  /**
   * Does this provider support the retrieval of members of a group.
   */
  boolean supportsGettingMembersOfGroup();

  /**
   *
   * @param uid the fully qualified uid
   */
  List<Group> getGroupMemberships(String uid) ;

  /**
   *
   * @param uid the fully qualified uid
   * @param groupId the fully qualified uid groupId
   * @return the Group membership info if the user is indeed a member of the group, the empty Optional otherwise.
   */
  Optional<Group> getGroupMembership(String uid, String groupId);

  List<Member> getMembersOfGroup(String groupId);

  class Configuration {

    public final GroupProviderType type;
    public final String url;
    public final Credentials credentials;
    public final Integer timeOutMillis;
    public final String schacHomeOrganization;
    public final String name;
    public final boolean supportsGetMembers;

    private Configuration(GroupProviderType type, String url, Credentials credentials, Integer timeOutMillis, String schacHomeOrganization, String name, boolean supportsGetMembers) {
      this.type = type;
      this.url = url;
      this.credentials = credentials;
      this.timeOutMillis = timeOutMillis;
      this.schacHomeOrganization = schacHomeOrganization;
      this.name = name;
      this.supportsGetMembers = supportsGetMembers;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(Configuration.class)
          .add("url", url)
          .add("name", name)
          .add("timeOutMillis", timeOutMillis)
          .add("schacHomeOrganization", schacHomeOrganization).toString();
    }

    public static ConfigurationBuilder builder() {
      return new ConfigurationBuilder();
    }

    public static class ConfigurationBuilder {
      private GroupProviderType type;
      private String url;
      private Credentials credentials;
      private Integer timeOutMillis;
      private String schacHomeOrganization;
      private String name;
      private boolean supportsGetMembers;

      public ConfigurationBuilder setType(GroupProviderType type) {
        this.type = type;
        return this;
      }

      public ConfigurationBuilder setUrl(String url) {
        this.url = url;
        return this;
      }

      public ConfigurationBuilder setCredentials(String username, String password) {
        this.setCredentials(new Credentials(username, password));
        return this;
      }

      public ConfigurationBuilder setCredentials(Credentials credentials) {
        this.credentials = credentials;
        return this;
      }

      public ConfigurationBuilder setTimeOutMillis(Integer timeOutMillis) {
        this.timeOutMillis = timeOutMillis;
        return this;
      }

      public ConfigurationBuilder setSchacHomeOrganization(String schacHomeOrganization) {
        this.schacHomeOrganization = schacHomeOrganization;
        return this;
      }

      public ConfigurationBuilder setName(String name) {
        this.name = name;
        return this;
      }

      public ConfigurationBuilder setSupportsGetMembers(boolean supportsGetMembers) {
        this.supportsGetMembers = supportsGetMembers;
        return this;
      }

      public Configuration build() {
        return new Configuration(
            checkNotNull(type), checkNotNull(url), checkNotNull(credentials),
            checkNotNull(timeOutMillis), checkNotNull(schacHomeOrganization),
            checkNotNull(name), checkNotNull(supportsGetMembers));
      }
    }

    public static class Credentials {
      final String username;
      final String password;

      public Credentials(String username, String password) {
        this.username = username;
        this.password = password;
      }
    }

  }
}
