package com.jaoow.helmetstore.service.user;


import com.jaoow.helmetstore.dto.user.UserRegisterRequest;
import com.jaoow.helmetstore.dto.user.UserResponse;
import com.jaoow.helmetstore.exception.EmailAlreadyInUseException;
import com.jaoow.helmetstore.exception.ResourceNotFoundException;
import com.jaoow.helmetstore.model.balance.Account;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.user.Role;
import com.jaoow.helmetstore.model.user.User;
import com.jaoow.helmetstore.repository.user.RoleRepository;
import com.jaoow.helmetstore.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final ModelMapper modelMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        Set<GrantedAuthority> grantedAuthorities = new HashSet<>();
        for (Role role : user.getRoles()) {
            grantedAuthorities.add(new SimpleGrantedAuthority(role.getName()));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(), grantedAuthorities
        );
    }

    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    public Role findRoleByName(String roleName) {
        return roleRepository.findByName(roleName).orElseThrow(() -> new ResourceNotFoundException("Role not found with name: " + roleName));
    }

    public UserResponse self(Principal principal) {
        User user = findUserByEmail(principal.getName());
        return modelMapper.map(user, UserResponse.class);
    }

    public UserResponse register(UserRegisterRequest userRegisterRequest) {
        if (userRepository.existsByEmail(userRegisterRequest.getEmail())) {
            throw new EmailAlreadyInUseException();
        }

        User user = new User();
        user.setName(userRegisterRequest.getName());
        user.setEmail(userRegisterRequest.getEmail());
        user.setPassword(passwordEncoder.encode(userRegisterRequest.getPassword()));

        Inventory inventory = new Inventory();
        user.setInventory(inventory);

        Role role = findRoleByName("ROLE_USER");
        user.setRoles(new HashSet<>(Set.of(role)));

        User finalUser = user;
        Set<Account> accounts = Arrays.stream(AccountType.values())
                .map(accountType -> Account.builder()
                        .type(accountType)
                        .user(finalUser)
                        .build())
                .collect(Collectors.toSet());

        user.setAccounts(accounts);

        user = userRepository.save(user);
        return modelMapper.map(user, UserResponse.class);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void assignRoleToUser(String userEmail, String roleName) {
        User user = findUserByEmail(userEmail);
        Role role = findRoleByName(roleName);

        user.getRoles().add(role);
        userRepository.save(user);
    }
}
