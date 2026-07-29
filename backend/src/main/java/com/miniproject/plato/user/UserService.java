package com.miniproject.plato.user;

import java.util.UUID;

public interface UserService {

    User findById(UUID id);

    User findByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user);
}
