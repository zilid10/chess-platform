package me.zilid.chessplatform.service;

import me.zilid.chessplatform.model.converter.UserConverter;
import me.zilid.chessplatform.model.dto.UserCreateRequest;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.dto.UserUpdateRequest;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceTest {
    private UserRepo userRepo;
    private PasswordEncoder passwordEncoder;
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepo.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserService(userRepo, new UserConverter(passwordEncoder));
    }

    @Test
    void creatingUserStoresEncodedPasswordAndReturnsProfile() {
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.createUser(
                new UserCreateRequest("alice", "alice@example.com", "secret123", "Chess fan"));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(user.capture());
        assertThat(user.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(response.id()).isEqualTo(user.getValue().getId());
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.about()).isEqualTo("Chess fan");
    }

    @Test
    void partialUpdatePreservesUnspecifiedFieldsAndCanClearAbout() {
        User user = new User("alice@example.com", "alice", "existing-hash", "Old bio");
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));

        UserResponse response = service.updateUser(user.getId(), new UserUpdateRequest(null, null, null, ""));

        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.about()).isEmpty();
        assertThat(user.getPasswordHash()).isEqualTo("existing-hash");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void changingPasswordEncodesTheNewValue() {
        User user = new User("alice@example.com", "alice", "existing-hash", "");
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-secret")).thenReturn("new-hash");

        service.updateUser(user.getId(), new UserUpdateRequest(null, null, "new-secret", null));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(passwordEncoder).encode("new-secret");
    }

    @Test
    void missingUserCannotBeUpdated() {
        UUID missingId = UUID.randomUUID();

        assertThatThrownBy(() -> service.updateUser(missingId, new UserUpdateRequest("newName", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found!");
        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}
