package com.giut.server.profile.service;

import com.giut.server.profile.dto.response.MyProfileResponse;
import com.giut.server.profile.entity.ActivityHistory;
import com.giut.server.profile.entity.PortfolioItem;
import com.giut.server.profile.entity.PortfolioItemRole;
import com.giut.server.profile.entity.PortfolioItemSkillTag;
import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.profile.entity.ProfileRoleSkillTag;
import com.giut.server.profile.entity.ProfileTag;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.entity.UserProfileRole;
import com.giut.server.profile.entity.UserProfileTag;
import com.giut.server.profile.repository.ActivityHistoryRepository;
import com.giut.server.profile.repository.PortfolioItemRepository;
import com.giut.server.profile.repository.PortfolioItemRoleRepository;
import com.giut.server.profile.repository.PortfolioItemSkillTagRepository;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.profile.repository.ProfileRoleSkillTagRepository;
import com.giut.server.profile.repository.ProfileTagRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.profile.repository.UserProfileRoleRepository;
import com.giut.server.profile.repository.UserProfileTagRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 측정 전용: UserProfileService의 단일 프로필 조회(getMyProfile)가 포트폴리오/활동 이력 수에
 * 비례해 쿼리 수가 늘어나는 N+1 패턴이 없는지 Hibernate 통계로 실측한다. H2(create-drop) 격리
 * DB 기준이며 네트워크 왕복 시간은 재현하지 않으므로, 실제 지연은 쿼리 수(=RDS 왕복 횟수 추정치)로
 * 판단하고 절대 ms 값은 참고만 한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_profile_query_count;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "DISCORD_ALERT_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@Transactional
class UserProfileQueryCountMeasurementTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserProfileRoleRepository userProfileRoleRepository;
    @Autowired private UserProfileTagRepository userProfileTagRepository;
    @Autowired private ProfileRoleRepository profileRoleRepository;
    @Autowired private ProfileTagRepository profileTagRepository;
    @Autowired private ProfileRoleSkillTagRepository profileRoleSkillTagRepository;
    @Autowired private PortfolioItemRepository portfolioItemRepository;
    @Autowired private PortfolioItemSkillTagRepository portfolioItemSkillTagRepository;
    @Autowired private PortfolioItemRoleRepository portfolioItemRoleRepository;
    @Autowired private ActivityHistoryRepository activityHistoryRepository;
    @Autowired private UserProfileService userProfileService;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void myProfileQueryCountStaysFlatAsPortfolioAndActivityHistoryCountsGrow() {
        ProfileRole role1 = saveRole("BACKEND_DEVELOPER", ProfileRole.PrimaryRole.DEVELOPMENT);
        ProfileRole role2 = saveRole("FRONTEND_DEVELOPER", ProfileRole.PrimaryRole.DEVELOPMENT);
        ProfileTag skillTag1 = profileTagRepository.save(ProfileTag.create(ProfileTag.TagType.SKILL, "Java", "java"));
        ProfileTag skillTag2 = profileTagRepository.save(ProfileTag.create(ProfileTag.TagType.SKILL, "Spring", "spring"));
        profileRoleSkillTagRepository.save(ProfileRoleSkillTag.create(role1, skillTag1));
        profileRoleSkillTagRepository.save(ProfileRoleSkillTag.create(role1, skillTag2));

        long fewItemsCount = measureQueryCountForProfileWithPortfolioItems(
                "small-profile-user", role1, role2, skillTag1, skillTag2, 2);
        long manyItemsCount = measureQueryCountForProfileWithPortfolioItems(
                "large-profile-user", role1, role2, skillTag1, skillTag2, 20);

        System.out.println(
                "[query-count] getMyProfile: 2 portfolio items/2 activity histories = " + fewItemsCount
                        + " prepared statements, 20 portfolio items/20 activity histories = " + manyItemsCount
                        + " prepared statements");

        // 핵심 검증: 항목 수가 10배(2 -> 20) 늘어도 쿼리 수는 그대로여야 한다(배치 조회 유지 = N+1 없음).
        assertThat(manyItemsCount).isEqualTo(fewItemsCount);
    }

    private long measureQueryCountForProfileWithPortfolioItems(
            String emailPrefix, ProfileRole role1, ProfileRole role2,
            ProfileTag skillTag1, ProfileTag skillTag2, int portfolioItemCount
    ) {
        User user = userRepository.saveAndFlush(User.createOAuthUser(
                emailPrefix + "@example.com", emailPrefix, User.OAuthProvider.KAKAO, emailPrefix + "-provider"));
        UserProfile profile = userProfileRepository.saveAndFlush(UserProfile.create(
                user, UserProfile.DepartmentType.COMPUTER_SCIENCE, UserProfile.ActivityStatus.LOOKING_FOR_TEAM,
                (short) 3, null, null, "소개", true, "[\"DEVELOPMENT\"]"));
        userProfileRoleRepository.saveAll(List.of(
                UserProfileRole.create(profile, role1), UserProfileRole.create(profile, role2)));
        userProfileTagRepository.saveAll(List.of(
                UserProfileTag.create(profile, skillTag1), UserProfileTag.create(profile, skillTag2)));

        List<PortfolioItem> items = portfolioItemRepository.saveAll(
                java.util.stream.IntStream.range(0, portfolioItemCount)
                        .mapToObj(index -> PortfolioItem.create(
                                user, "https://example.com/image.png", "포트폴리오 " + index, "설명",
                                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), 3, "내용"))
                        .toList());
        for (PortfolioItem item : items) {
            portfolioItemSkillTagRepository.saveAll(List.of(
                    PortfolioItemSkillTag.create(item, skillTag1), PortfolioItemSkillTag.create(item, skillTag2)));
            portfolioItemRoleRepository.saveAll(List.of(
                    PortfolioItemRole.create(item, role1, 0), PortfolioItemRole.create(item, role2, 1)));
        }
        activityHistoryRepository.saveAll(
                java.util.stream.IntStream.range(0, portfolioItemCount)
                        .mapToObj(index -> ActivityHistory.create(
                                user, ActivityHistory.Category.EXTERNAL_ACTIVITY, "활동 " + index, "주최 기관",
                                YearMonth.of(2025, 1), YearMonth.of(2025, 2)))
                        .toList());

        entityManager.flush();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        MyProfileResponse response = userProfileService.getMyProfile(user.getId());

        assertThat(response.profile().portfolioItems()).hasSize(portfolioItemCount);
        return statistics.getPrepareStatementCount();
    }

    private ProfileRole saveRole(String code, ProfileRole.PrimaryRole primaryRole) {
        ProfileRole role = BeanUtils.instantiateClass(ProfileRole.class);
        ReflectionTestUtils.setField(role, "code", code);
        ReflectionTestUtils.setField(role, "name", code);
        ReflectionTestUtils.setField(role, "primaryRole", primaryRole);
        ReflectionTestUtils.setField(role, "displayOrder", 1);
        return profileRoleRepository.saveAndFlush(role);
    }
}
