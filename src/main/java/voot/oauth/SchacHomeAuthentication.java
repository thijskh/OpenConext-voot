package voot.oauth;

import java.util.Collection;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

@SuppressWarnings("serial")
public class SchacHomeAuthentication extends UsernamePasswordAuthenticationToken {

  private final String schacHomeOrganization;

  public SchacHomeAuthentication(String schacHomeOrganization, Object principal, Object credentials, Collection<? extends GrantedAuthority> authorities) {
    super(principal, credentials, authorities);
    this.schacHomeOrganization = schacHomeOrganization;
  }

  public String getSchacHomeAuthentication() {
    return schacHomeOrganization;
  }

}
