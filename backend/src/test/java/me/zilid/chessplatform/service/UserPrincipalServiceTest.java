package me.zilid.chessplatform.service;

import me.zilid.chessplatform.model.converter.UserConverter;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPrincipalServiceTest {
    private UserRepo userRepo;
    private UserPrincipalService service;

    private final User user = new User("alice@example.com", "alice", "encoded-password", "");

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepo.class);
        service = new UserPrincipalService(userRepo, new UserConverter(mock(PasswordEncoder.class)));
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
    void idLookupUsesStoredIdentity() {
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));

        UserPrincipal principal = service.loadUserById(user.getId());

        assertThat(principal.getId()).isEqualTo(user.getId());
        assertThat(principal.getUsername()).isEqualTo("alice");
    }

    @Test
    void missingUserProducesSecurityLookupException() {
        UUID missingId = UUID.randomUUID();

        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> service.loadUserById(missingId))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
