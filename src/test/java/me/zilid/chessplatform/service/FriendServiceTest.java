package me.zilid.chessplatform.service;

import me.zilid.chessplatform.exception.FriendAlreadyExistsException;
import me.zilid.chessplatform.exception.UserNotFoundException;
import me.zilid.chessplatform.model.converter.FriendRequestConverter;
import me.zilid.chessplatform.model.converter.UserConverter;
import me.zilid.chessplatform.model.dto.FriendRequestResponse;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.FriendRequestRepo;
import me.zilid.chessplatform.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static me.zilid.chessplatform.model.entity.FriendRequest.RequestStatus.ACCEPTED;
import static me.zilid.chessplatform.model.entity.FriendRequest.RequestStatus.PENDING;
import static me.zilid.chessplatform.model.entity.FriendRequest.RequestStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FriendServiceTest {
    private FriendRequestRepo requestRepo;
    private UserRepo userRepo;
    private FriendRequestConverter requestConverter;
    private FriendService service;

    private final User alice = new User("alice@example.com", "alice", "hash", "");
    private final User bob = new User("bob@example.com", "bob", "hash", "");

    @BeforeEach
    void setUp() {
        requestRepo = mock(FriendRequestRepo.class);
        userRepo = mock(UserRepo.class);
        UserConverter userConverter = new UserConverter(mock(PasswordEncoder.class));
        requestConverter = new FriendRequestConverter(userConverter);
        service = new FriendService(requestRepo, userRepo, requestConverter, userConverter);
    }

    @Test
    void cannotSendRequestToSelf() {
        assertThatThrownBy(() -> service.createFriendRequest(alice.getId(), alice.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot send friend request to yourself");
        verifyNoInteractions(userRepo, requestRepo);
    }

    @Test
    void cannotSendRequestWhenSenderOrRecipientDoesNotExist() {
        assertThatThrownBy(() -> service.createFriendRequest(alice.getId(), bob.getId()))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Sender not found");

        when(userRepo.findById(alice.getId())).thenReturn(Optional.of(alice));
        assertThatThrownBy(() -> service.createFriendRequest(alice.getId(), bob.getId()))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Recipient not found");
        verifyNoInteractions(requestRepo);
    }

    @Test
    void cannotSendRequestToExistingFriend() {
        usersExist();
        when(requestRepo.existsFriendships(alice.getId(), bob.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.createFriendRequest(alice.getId(), bob.getId()))
                .isInstanceOf(FriendAlreadyExistsException.class)
                .hasMessage("Users are already friends");
        verify(requestRepo, never()).save(any());
    }

    @Test
    void cannotDuplicatePendingRequestInEitherDirection() {
        usersExist();
        FriendRequest existing = new FriendRequest(alice, bob, PENDING);
        when(requestRepo.findBySender_IdAndRecipient_IdAndStatus(alice.getId(), bob.getId(), PENDING))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createFriendRequest(alice.getId(), bob.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Friend request already sent");

        when(requestRepo.findBySender_IdAndRecipient_IdAndStatus(alice.getId(), bob.getId(), PENDING))
                .thenReturn(Optional.empty());
        when(requestRepo.findBySender_IdAndRecipient_IdAndStatus(bob.getId(), alice.getId(), PENDING))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createFriendRequest(alice.getId(), bob.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reverse friend request already sent");
        verify(requestRepo, never()).save(any());
    }

    @Test
    void createsPendingRequestWithTheResolvedUsers() {
        usersExist();
        when(requestRepo.save(any(FriendRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FriendRequestResponse response = service.createFriendRequest(alice.getId(), bob.getId());
        ArgumentCaptor<FriendRequest> request = ArgumentCaptor.forClass(FriendRequest.class);
        verify(requestRepo).save(request.capture());
        assertThat(request.getValue().getSender()).isSameAs(alice);
        assertThat(request.getValue().getRecipient()).isSameAs(bob);
        assertThat(request.getValue().getStatus()).isEqualTo(PENDING);
        assertThat(response.friendRequestId()).isEqualTo(request.getValue().getId());
        assertThat(response.sender().id()).isEqualTo(alice.getId());
        assertThat(response.recipient().id()).isEqualTo(bob.getId());
        assertThat(response.status()).isEqualTo(PENDING);
    }

    @Test
    void onlyRecipientCanAcceptAPendingRequest() {
        UUID requestId = UUID.randomUUID();
        assertThatThrownBy(() -> service.acceptFriendRequest(requestId, alice.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no pending friend requests found");
        verify(requestRepo, never()).addFriend(any(), any());
    }

    @Test
    void acceptingRequestCreatesFriendshipAndMarksItAccepted() {
        FriendRequest request = new FriendRequest(alice, bob, PENDING);
        when(requestRepo.findByIdAndRecipient_IdAndStatus(request.getId(), bob.getId(), PENDING))
                .thenReturn(Optional.of(request));

        FriendRequestResponse response = service.acceptFriendRequest(request.getId(), bob.getId());
        assertThat(request.getStatus()).isEqualTo(ACCEPTED);
        assertThat(response.status()).isEqualTo(ACCEPTED);
        verify(requestRepo).addFriend(alice.getId(), bob.getId());
    }

    @Test
    void decliningRequestMarksItRejectedWithoutCreatingFriendship() {
        FriendRequest request = new FriendRequest(alice, bob, PENDING);
        when(requestRepo.findByIdAndRecipient_IdAndStatus(request.getId(), bob.getId(), PENDING))
                .thenReturn(Optional.of(request));

        service.declineFriendRequest(request.getId(), bob.getId());

        assertThat(request.getStatus()).isEqualTo(REJECTED);
        verify(requestRepo, never()).addFriend(any(), any());
    }

    @Test
    void deletingFriendshipRemovesBothDirections() {
        alice.addFriend(bob);
        bob.addFriend(alice);
        usersExist();

        service.deleteFriend(alice.getId(), bob.getId());

        assertThat(alice.getFriends()).doesNotContain(bob);
        assertThat(bob.getFriends()).doesNotContain(alice);
    }

    @Test
    void cannotDeleteFriendshipThatDoesNotExist() {
        usersExist();

        assertThatThrownBy(() -> service.deleteFriend(alice.getId(), bob.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("friendship does not exist");
    }

    private void usersExist() {
        when(userRepo.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(userRepo.findById(bob.getId())).thenReturn(Optional.of(bob));
    }
}
