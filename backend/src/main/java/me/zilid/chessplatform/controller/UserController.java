package me.zilid.chessplatform.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import me.zilid.chessplatform.model.dto.*;
import me.zilid.chessplatform.security.UserPrincipal;
import me.zilid.chessplatform.service.RatingService;
import me.zilid.chessplatform.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final RatingService ratingService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public UserController(UserService userService, RatingService ratingService,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository) {
        this.userService = userService;
        this.ratingService = ratingService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {

        logger.info("Login attempt for user: {}", request.username());

        // Authenticate the user
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );

        // Set the authentication in the security context
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Store security context in HTTP session
        securityContextRepository.saveContext(
                context,
                httpRequest,
                httpResponse
        );

        // Get authenticated user details and return full user information
        if (!(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            throw new IllegalStateException("Unexpected principal type");
        }
        logger.info("User {} logged in successfully", request.username());
        return userService.getUserById(userPrincipal.getId());
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        logger.debug("Fetching current user info for: {}", userPrincipal.getUsername());
        return userService.getUserById(userPrincipal.getId());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserCreateRequest request) {
        logger.info("Creating new user: {}", request.username());
        return userService.createUser(request);
    }

    @PutMapping("/users")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse updateUser(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                   @Valid @RequestBody UserUpdateRequest request) {
        logger.info("Updating user: {}", userPrincipal.getUsername());
        return userService.updateUser(userPrincipal.getId(), request);
    }

    @DeleteMapping("/users")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@AuthenticationPrincipal UserPrincipal userPrincipal,
                           HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) {
        logger.info("Deleting user: {}", userPrincipal.getUsername());
        userService.deleteUser(userPrincipal.getId());

        // Clear the session after deleting the user
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // Clear the security context
        SecurityContextHolder.clearContext();

        // Delete the JSESSIONID cookie
        Cookie cookie = new Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0); // Delete the cookie immediately
        httpResponse.addCookie(cookie);
    }

    @GetMapping("/users")
    @ResponseStatus(HttpStatus.OK)
    public Page<UserResponse> getUser(@RequestParam("search") String search, Pageable pageable) {
        logger.debug("Searching users with query: {}", search);
        return userService.getUser(search, pageable);
    }

    @GetMapping("/users/{userId}/ratings")
    @ResponseStatus(HttpStatus.OK)
    public List<PlayerRatingResponse> getRatings(@PathVariable("userId") UUID userId) {
        logger.debug("Fetching ratings for user: {}", userId);
        return ratingService.getRatings(userId);
    }

}
