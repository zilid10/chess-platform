package me.zilid.chessplatform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class UserPrincipalServiceTest {
    private final User user = new User("alice@example.com", "alice", "encoded-password", "");
    private UserRepo userRepo;
    private UserPrincipalService service;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepo.class);
        service = new UserPrincipalService(userRepo);
    }

    @Test
    void usernameLookupUsesStoredIdentityAndCredentials() {
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));

        UserPrincipal principal = (UserPrincipal) service.loadUserByUsername("alice");

        assertThat(principal.getId()).isEqualTo(user.getId());
        assertThat(principal.getUsername()).isEqualTo(user.getUsername());
        assertThat(principal.getEmail()).isEqualTo(user.getEmail());
        assertThat(principal.getPassword()).isEqualTo(user.getPasswordHash());
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getAuthorities()).isEmpty();
    }

    @Test
    void missingUserProducesSecurityLookupException() {
        assertThatThrownBy(() -> service.loadUserByUsername("missing")).isInstanceOf(UsernameNotFoundException.class);
    }
}
