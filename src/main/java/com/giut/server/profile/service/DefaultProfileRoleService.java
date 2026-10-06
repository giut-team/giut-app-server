package com.giut.server.profile.service;

import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.profile.dto.response.SeedDefaultProfileRolesResponse;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class DefaultProfileRoleService {

    private static final String SEED_RESOURCE = "db/seed/profile_roles.sql";
    private static final String INSERT_START = "INSERT INTO profile_roles ";

    private final UserRepository userRepository;
    private final ProfileRoleRepository profileRoleRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public SeedDefaultProfileRolesResponse seedDefaults(Long adminUserId) {
        userRepository.findById(adminUserId)
                .filter(user -> user.getRole() == User.Role.ADMIN && user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("관리자 권한이 필요합니다."));

        jdbcTemplate.update(loadDefaultRolesInsert());
        return new SeedDefaultProfileRolesResponse(profileRoleRepository.count());
    }

    private String loadDefaultRolesInsert() {
        try (InputStream input = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int insertStart = script.indexOf(INSERT_START);
            if (insertStart < 0) {
                throw new IllegalStateException("기본 역할 INSERT SQL을 찾을 수 없습니다.");
            }
            // The manual seed also contains CREATE TABLE; the API executes only its data upsert.
            return script.substring(insertStart).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("기본 역할 SQL을 읽을 수 없습니다.", exception);
        }
    }
}
