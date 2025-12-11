package me.zilid.chessplatform.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import me.zilid.chessplatform.model.dto.LoginRequest;
import me.zilid.chessplatform.model.dto.UserCreateRequest;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.dto.UserUpdateRequest;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/api")
public class UserController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    public UserController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse login(@Valid @RequestBody LoginRequest request, 
                             BindingResult result,
                             HttpServletRequest httpRequest) {
        if (result.hasErrors()) {
            throw new ValidationException(result.getAllErrors().toString());
        }

        // Authenticate the user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // Set the authentication in the security context
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authentication);

        // Store security context in HTTP session
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        // Get authenticated user details and return full user information
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return userService.getUserById(userPrincipal.getId());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserCreateRequest request, BindingResult result) {
        if (result.hasErrors()) {
            throw new ValidationException(result.getAllErrors().toString());
        }
        return userService.createUser(request);
    }

    @PutMapping("/users")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse updateUser(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                   @Valid @RequestBody UserUpdateRequest request,
                                   BindingResult result) {
        if (result.hasErrors()) {
            throw new ValidationException(result.getAllErrors().toString());
        }
        return userService.updateUser(userPrincipal.getId(), request);
    }

    @DeleteMapping("/users")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@AuthenticationPrincipal UserPrincipal userPrincipal,
                          HttpServletRequest httpRequest,
                          HttpServletResponse httpResponse) {
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
        return userService.getUser(search, pageable);
    }

}
