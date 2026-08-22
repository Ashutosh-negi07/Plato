package com.miniproject.plato.security;

import com.miniproject.plato.modules.user.User;
import com.miniproject.plato.modules.user.UserRepository;
import com.miniproject.plato.modules.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implements Spring Security's UserDetailsService.
 * Spring calls loadUserByUsername() to look up a user when validating
 * credentials.
 *
 * <p>
 * "Username" in Spring Security terminology = email in Plato.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user by email address.
     *
     * <p>
     * Spring Security calls this during JWT filter processing to build
     * the Authentication object placed in the SecurityContext.
     *
     * param email the email address (Spring Security calls the param "username")
     * return a UserDetails object Spring Security understands
     * throws UsernameNotFoundException if no user with that email exists
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("UserDetailsService: no user found for email '{}'", email);
                    return new UsernameNotFoundException("User not found: " + email);
                });

        // Map our UserRole enum to a Spring Security GrantedAuthority.
        // Spring Security expects the format "ROLE_<NAME>" for hasRole() checks.
        // e.g. UserRole.OWNER → "ROLE_OWNER"
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        // Build a Spring Security User object (different from our entity User).
        // We pass the account state flags so Spring can enforce them automatically.
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(authority))
                .disabled(user.getStatus() == UserStatus.SUSPENDED)
                .accountLocked(user.getStatus() == UserStatus.DELETED)
                .build();
    }
}
