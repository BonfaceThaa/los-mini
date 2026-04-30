package com.credvenn.lm.security;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    List<Permission> findAllByCodeIn(Collection<String> codes);

    List<Permission> findAllByOrderByCodeAsc();
}
