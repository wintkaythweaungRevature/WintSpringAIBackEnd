package com.example.service;

import com.example.entity.User;
import com.example.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User appUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        String role = (appUser.getRole() != null && !appUser.getRole().isBlank())
                ? appUser.getRole() : "ROLE_USER";
        return new org.springframework.security.core.userdetails.User(
                appUser.getEmail(),
                appUser.getPassword() != null ? appUser.getPassword() : "",
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
    }
}
