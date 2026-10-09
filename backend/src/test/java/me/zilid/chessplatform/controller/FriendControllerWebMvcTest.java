package me.zilid.chessplatform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import me.zilid.chessplatform.exception.FriendAlreadyExistsException;
import me.zilid.chessplatform.model.dto.FriendRequestResponse;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.security.SecurityConfig;
import me.zilid.chessplatform.security.UserPrincipal;
import me.zilid.chessplatform.service.FriendService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(FriendController.class)
@Import(SecurityConfig.class)
@MockitoBean(types = SimpMessagingTemplate.class) // needed by the STOMP-only WebSocketExceptionHandler advice
class FriendControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("02410898-174c-4cb5-b8c5-55fe3cc535b9");
    private static final UUID FRIEND_ID = UUID.fromString("917a66af-bc3f-4438-b52d-4d049122de0e");
    private static final UUID REQUEST_ID = UUID.fromString("1f3864d1-a23f-49e6-8e50-bf4bc85b281b");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final UserPrincipal PLAYER =
            new UserPrincipal(USER_ID, "player", "player@example.com", "password", true, List.of());
    private static final UserResponse FRIEND =
            new UserResponse(FRIEND_ID, "friend", "friend@example.com", "Chess fan", CREATED_AT, CREATED_AT);
    private static final UserResponse PLAYER_RESPONSE =
            new UserResponse(USER_ID, "player", "player@example.com", "", CREATED_AT, CREATED_AT);
    private static final FriendRequestResponse REQUEST = new FriendRequestResponse(
            REQUEST_ID, PLAYER_RESPONSE, FRIEND, FriendRequest.RequestStatus.PENDING, CREATED_AT, CREATED_AT);

    @Autowired
    private MockMvcTester mvcTester;

    @MockitoBean
    private FriendService friendService;

    private static Stream<MockHttpServletRequestBuilder> friendRoutes() {
        return Stream.of(
                get("/api/friends"),
                get("/api/friends/received"),
                get("/api/friends/sent"),
                delete("/api/friends/{userId}", FRIEND_ID),
                post("/api/friends/send/{userId}", FRIEND_ID),
                post("/api/friends/accept/{friendRequestId}", REQUEST_ID),
                put("/api/friends/reject/{friendRequestId}", REQUEST_ID));
    }

    @ParameterizedTest
    @MethodSource("friendRoutes")
    void everyFriendRouteRequiresAuthentication(MockHttpServletRequestBuilder request) {
        assertThat(mvcTester.perform(request)).hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(friendService);
    }

    @Test
    void friendsPageUsesTheAuthenticatedUserAndRequestedPage() {
        PageRequest page = PageRequest.of(1, 2);
        when(friendService.getFriends(USER_ID, page)).thenReturn(new PageImpl<>(List.of(FRIEND), page, 3));

        MvcTestResult result = mvcTester
                .get()
                .uri("/api/friends")
                .param("page", "1")
                .param("size", "2")
                .with(user(PLAYER))
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content[0].id").isEqualTo(FRIEND_ID.toString());
        assertThat(result).bodyJson().extractingPath("$.content[0].username").isEqualTo("friend");
        assertThat(result).bodyJson().extractingPath("$.page.totalElements").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.page.number").isEqualTo(1);

        verify(friendService).getFriends(USER_ID, page);
    }

    @Test
    void sentAndReceivedRequestsUseTheAuthenticatedUser() {
        PageRequest page = PageRequest.of(0, 5);
        PageImpl<FriendRequestResponse> requests = new PageImpl<>(List.of(REQUEST), page, 1);
        when(friendService.getSentRequest(USER_ID, page)).thenReturn(requests);
        when(friendService.getReceivedRequest(USER_ID, page)).thenReturn(requests);

        MvcTestResult result = mvcTester
                .get()
                .uri("/api/friends/sent")
                .param("size", "5")
                .with(user(PLAYER))
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].friendRequestId")
                .isEqualTo(REQUEST_ID.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].recipient.id")
                .isEqualTo(FRIEND_ID.toString());
        assertThat(result).bodyJson().extractingPath("$.content[0].status").isEqualTo("PENDING");

        result = mvcTester
                .get()
                .uri("/api/friends/received")
                .param("size", "5")
                .with(user(PLAYER))
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].friendRequestId")
                .isEqualTo(REQUEST_ID.toString());
        assertThat(result).bodyJson().extractingPath("$.content[0].sender.id").isEqualTo(USER_ID.toString());

        verify(friendService).getSentRequest(USER_ID, page);
        verify(friendService).getReceivedRequest(USER_ID, page);
    }

    @Test
    void sendAndAcceptReturnCreatedRequestsForTheAuthenticatedUser() {
        when(friendService.createFriendRequest(USER_ID, FRIEND_ID)).thenReturn(REQUEST);
        when(friendService.acceptFriendRequest(REQUEST_ID, USER_ID)).thenReturn(REQUEST);

        MvcTestResult result = mvcTester
                .post()
                .uri("/api/friends/send/{userId}", FRIEND_ID)
                .with(user(PLAYER))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.friendRequestId").isEqualTo(REQUEST_ID.toString());
        assertThat(result).bodyJson().extractingPath("$.recipient.id").isEqualTo(FRIEND_ID.toString());

        result = mvcTester
                .post()
                .uri("/api/friends/accept/{friendRequestId}", REQUEST_ID)
                .with(user(PLAYER))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.friendRequestId").isEqualTo(REQUEST_ID.toString());

        verify(friendService).createFriendRequest(USER_ID, FRIEND_ID);
        verify(friendService).acceptFriendRequest(REQUEST_ID, USER_ID);
    }

    @Test
    void rejectionAndFriendRemovalReturnTheirDeclaredStatuses() {
        assertThat(mvcTester
                        .put()
                        .uri("/api/friends/reject/{friendRequestId}", REQUEST_ID)
                        .with(user(PLAYER)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvcTester.delete().uri("/api/friends/{userId}", FRIEND_ID).with(user(PLAYER)))
                .hasStatusOk();

        verify(friendService).declineFriendRequest(REQUEST_ID, USER_ID);
        verify(friendService).deleteFriend(USER_ID, FRIEND_ID);
    }

    @Test
    void invalidFriendRequestUsesTheApplicationErrorResponse() {
        when(friendService.createFriendRequest(USER_ID, FRIEND_ID))
                .thenThrow(new FriendAlreadyExistsException("Users are already friends"));

        MvcTestResult result = mvcTester
                .post()
                .uri("/api/friends/send/{userId}", FRIEND_ID)
                .with(user(PLAYER))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Users are already friends");
    }
}
