package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.config.SecurityConfig;
import me.zilid.chessplatform.exception.GlobalExceptionHandler;
import me.zilid.chessplatform.model.dto.FriendRequestResponse;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.entity.FriendRequest;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.FriendService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FriendController.class)
@ContextConfiguration(classes = FriendControllerWebMvcTest.TestConfiguration.class)
class FriendControllerWebMvcTest {

    @SpringBootConfiguration
    @Import({FriendController.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class TestConfiguration {
    }

    private static final UUID USER_ID = UUID.fromString("02410898-174c-4cb5-b8c5-55fe3cc535b9");
    private static final UUID FRIEND_ID = UUID.fromString("917a66af-bc3f-4438-b52d-4d049122de0e");
    private static final UUID REQUEST_ID = UUID.fromString("1f3864d1-a23f-49e6-8e50-bf4bc85b281b");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final UserPrincipal PLAYER = new UserPrincipal(
            USER_ID, "player", "player@example.com", "password", true, List.of());
    private static final UserResponse FRIEND = new UserResponse(
            FRIEND_ID, "friend", "friend@example.com", "Chess fan", CREATED_AT, CREATED_AT);
    private static final UserResponse PLAYER_RESPONSE = new UserResponse(
            USER_ID, "player", "player@example.com", "", CREATED_AT, CREATED_AT);
    private static final FriendRequestResponse REQUEST = new FriendRequestResponse(
            REQUEST_ID, PLAYER_RESPONSE, FRIEND, FriendRequest.RequestStatus.PENDING, CREATED_AT, CREATED_AT);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FriendService friendService;

    @ParameterizedTest
    @MethodSource("friendRoutes")
    void everyFriendRouteRequiresAuthentication(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().isForbidden());

        verifyNoInteractions(friendService);
    }

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

    @Test
    void friendsPageUsesTheAuthenticatedUserAndRequestedPage() throws Exception {
        PageRequest page = PageRequest.of(1, 2);
        when(friendService.getFriends(USER_ID, page))
                .thenReturn(new PageImpl<>(List.of(FRIEND), page, 3));

        mvc.perform(get("/api/friends")
                        .param("page", "1")
                        .param("size", "2")
                        .with(user(PLAYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(FRIEND_ID.toString()))
                .andExpect(jsonPath("$.content[0].username").value("friend"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(1));

        verify(friendService).getFriends(USER_ID, page);
    }

    @Test
    void sentAndReceivedRequestsUseTheAuthenticatedUser() throws Exception {
        PageRequest page = PageRequest.of(0, 5);
        PageImpl<FriendRequestResponse> requests = new PageImpl<>(List.of(REQUEST), page, 1);
        when(friendService.getSentRequest(USER_ID, page)).thenReturn(requests);
        when(friendService.getReceivedRequest(USER_ID, page)).thenReturn(requests);

        mvc.perform(get("/api/friends/sent").param("size", "5").with(user(PLAYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].friendRequestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.content[0].recipient.id").value(FRIEND_ID.toString()))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
        mvc.perform(get("/api/friends/received").param("size", "5").with(user(PLAYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].friendRequestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.content[0].sender.id").value(USER_ID.toString()));

        verify(friendService).getSentRequest(USER_ID, page);
        verify(friendService).getReceivedRequest(USER_ID, page);
    }

    @Test
    void sendAndAcceptReturnCreatedRequestsForTheAuthenticatedUser() throws Exception {
        when(friendService.createFriendRequest(USER_ID, FRIEND_ID)).thenReturn(REQUEST);
        when(friendService.acceptFriendRequest(REQUEST_ID, USER_ID)).thenReturn(REQUEST);

        mvc.perform(post("/api/friends/send/{userId}", FRIEND_ID).with(user(PLAYER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.friendRequestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.recipient.id").value(FRIEND_ID.toString()));
        mvc.perform(post("/api/friends/accept/{friendRequestId}", REQUEST_ID).with(user(PLAYER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.friendRequestId").value(REQUEST_ID.toString()));

        verify(friendService).createFriendRequest(USER_ID, FRIEND_ID);
        verify(friendService).acceptFriendRequest(REQUEST_ID, USER_ID);
    }

    @Test
    void rejectionAndFriendRemovalReturnTheirDeclaredStatuses() throws Exception {
        mvc.perform(put("/api/friends/reject/{friendRequestId}", REQUEST_ID).with(user(PLAYER)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/friends/{userId}", FRIEND_ID).with(user(PLAYER)))
                .andExpect(status().isOk());

        verify(friendService).declineFriendRequest(REQUEST_ID, USER_ID);
        verify(friendService).deleteFriend(USER_ID, FRIEND_ID);
    }

    @Test
    void invalidFriendRequestUsesTheApplicationErrorResponse() throws Exception {
        when(friendService.createFriendRequest(USER_ID, FRIEND_ID))
                .thenThrow(new IllegalArgumentException("Users are already friends"));

        mvc.perform(post("/api/friends/send/{userId}", FRIEND_ID).with(user(PLAYER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Users are already friends"));
    }
}
