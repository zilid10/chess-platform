package me.zilid.chessplatform.service;

import me.zilid.chessplatform.model.converter.UserConverter;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.repository.UserRepo;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserPrincipalService implements UserDetailsService {
    private final UserRepo userRepo;
    private final UserConverter userConverter;

    public UserPrincipalService(UserRepo userRepo, UserConverter userConverter) {
        this.userRepo = userRepo;
        this.userConverter = userConverter;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not exist: " + username));
        return userConverter.toPrincipal(user);
    }

    @Transactional(readOnly = true)
    public UserPrincipal loadUserById(UUID id) throws UsernameNotFoundException {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not exist: " + id));
        return userConverter.toPrincipal(user);
    }
}

