package com.giut.server.profile.service;

import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.profile.dto.response.SeedDefaultSkillTagsResponse;
import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.profile.seed.DefaultSkillTagCatalog;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefaultSkillTagService {

    private final UserRepository userRepository;
    private final ProfileRoleRepository profileRoleRepository;
    private final ProfileOptionService profileOptionService;
    private final DefaultSkillTagCatalog defaultSkillTagCatalog;

    @Transactional
    public SeedDefaultSkillTagsResponse seedDefaults(Long adminUserId) {
        userRepository.findById(adminUserId)
                .filter(user -> user.getRole() == User.Role.ADMIN && user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("관리자 권한이 필요합니다."));

        var skills = defaultSkillTagCatalog.skills();
        Set<String> requiredCodes = skills.stream()
                .flatMap(skill -> skill.relatedRoleCodes().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> existingCodes = profileRoleRepository.findAllByCodeIn(requiredCodes).stream()
                .map(ProfileRole::getCode)
                .collect(Collectors.toSet());
        requiredCodes.removeAll(existingCodes);
        if (!requiredCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "기본 역할을 먼저 등록해주세요. POST /api/admin/profile/roles/defaults; 누락된 역할: "
                            + String.join(", ", requiredCodes)
            );
        }

        int createdCount = 0;
        int existingCount = 0;
        List<Long> skillTagIds = new ArrayList<>();
        for (var skill : skills) {
            // Reuse case-insensitive name matching and additive role linking.
            var result = profileOptionService.createSkill(skill);
            if (result.created()) {
                createdCount++;
            } else {
                existingCount++;
            }
            skillTagIds.add(result.skill().id());
        }
        return new SeedDefaultSkillTagsResponse(createdCount, existingCount, List.copyOf(skillTagIds));
    }
}

