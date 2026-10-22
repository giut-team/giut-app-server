package com.giut.server.profile.service;

import com.giut.server.profile.dto.response.SeedDefaultProfileTagsResponse;
import com.giut.server.profile.dto.response.SeedDefaultProfileTagsResponse.TagTypeResult;
import com.giut.server.profile.entity.ProfileTag;
import com.giut.server.profile.repository.ProfileTagRepository;
import com.giut.server.profile.seed.DefaultProfileTagCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DefaultProfileTagService {

    private final DefaultSkillTagService defaultSkillTagService;
    private final ProfileTagRepository profileTagRepository;
    private final DefaultProfileTagCatalog catalog;

    @Transactional
    public SeedDefaultProfileTagsResponse seedDefaults(Long adminUserId) {
        // The existing skill service checks active ADMIN permission and required roles before writes.
        var skills = defaultSkillTagService.seedDefaults(adminUserId);
        var interests = seedTags(ProfileTag.TagType.INTEREST, catalog.interests());
        var experiences = seedTags(ProfileTag.TagType.EXPERIENCE, catalog.experiences());
        return new SeedDefaultProfileTagsResponse(skills.createdCount(), skills.existingCount(),
                skills.skillTagIds(), interests, experiences);
    }

    private TagTypeResult seedTags(ProfileTag.TagType type, List<String> names) {
        int createdCount = 0;
        List<Long> tagIds = new ArrayList<>();
        for (String name : names) {
            String normalizedName = name.trim().toLowerCase(Locale.ROOT);
            ProfileTag tag = profileTagRepository.findByTagTypeAndNormalizedName(type, normalizedName).orElse(null);
            if (tag == null) {
                tag = profileTagRepository.save(ProfileTag.create(type, name.trim(), normalizedName));
                createdCount++;
            }
            tagIds.add(tag.getId());
        }
        return new TagTypeResult(createdCount, names.size() - createdCount, List.copyOf(tagIds));
    }
}
