package com.giut.server.performance;

import com.giut.server.chat.entity.ChatMessage;
import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.entity.ChatRoomMember;
import com.giut.server.competition.entity.Competition;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamApplication;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.team.entity.TeamRecruitment;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PerformanceDataSeedService {

    public static final String MEASUREMENT_USER_EMAIL = "perf-main@giut.local";

    private final EntityManager entityManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public PerformanceSeedResult generate(PerformanceSeedProperties properties) {
        properties.validate();

        var existingMeasurementUser = userRepository.findByEmail(MEASUREMENT_USER_EMAIL);
        if (existingMeasurementUser.isPresent()) {
            return PerformanceSeedResult.alreadyExists(existingMeasurementUser.get().getId());
        }

        List<User> users = createUsers(properties);
        User measurementUser = users.getFirst();
        Competition competition = createCompetition(measurementUser);

        int teamMemberCount = createTeams(properties, users, competition);
        long messageCount = createPersonalChats(properties, users);

        flushAndClear();
        return new PerformanceSeedResult(
                true,
                measurementUser.getId(),
                competition.getId(),
                users.size(),
                properties.teamCount(),
                teamMemberCount,
                properties.teamCount() * properties.applicationsPerTeam(),
                properties.chatRoomCount(),
                messageCount
        );
    }

    private List<User> createUsers(PerformanceSeedProperties properties) {
        int userCount = properties.requiredUserCount();
        List<User> users = new ArrayList<>(userCount);

        User measurementUser = User.createAdmin(
                MEASUREMENT_USER_EMAIL,
                passwordEncoder.encode(properties.measurementPassword()),
                "성능측정사용자"
        );
        measurementUser.verifyUniversityEmail("perf-main@uos.ac.kr");
        entityManager.persist(measurementUser);
        users.add(measurementUser);

        for (int index = 1; index < userCount; index++) {
            User user = User.createOAuthUser(
                    "perf-user-%05d@giut.local".formatted(index),
                    "성능사용자%05d".formatted(index),
                    User.OAuthProvider.KAKAO,
                    "perf-provider-%05d".formatted(index)
            );
            user.verifyUniversityEmail("perf-user-%05d@uos.ac.kr".formatted(index));
            entityManager.persist(user);
            users.add(user);
            flushChunk(index, properties.batchSize());
        }
        flushAndClear();
        return users;
    }

    private Competition createCompetition(User measurementUser) {
        Competition competition = Competition.create(
                "[PERF] 팀 목록 성능 측정 공모전",
                Competition.Category.WEB_MOBILE_IT,
                "GIUT Performance Lab",
                "성능 측정용 사용자",
                "팀 및 채팅 API 기준 성능을 측정하기 위한 데이터입니다.",
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS),
                Competition.PublicationStatus.PUBLISHED,
                Competition.VerificationStatus.MANUALLY_VERIFIED,
                measurementUser
        );
        entityManager.persist(competition);
        entityManager.flush();
        return competition;
    }

    private int createTeams(
            PerformanceSeedProperties properties,
            List<User> users,
            Competition competition
    ) {
        int writtenEntities = 0;
        for (int teamIndex = 0; teamIndex < properties.teamCount(); teamIndex++) {
            User leader = users.get(1 + teamIndex);
            Team team = Team.create(
                    competition,
                    leader,
                    "[PERF] 성능 측정 팀 %05d".formatted(teamIndex + 1),
                    "팀 목록과 상세 조회 성능 측정을 위한 팀입니다.",
                    Team.ActivityMode.HYBRID,
                    (short) Math.max(properties.teamMembersPerTeam(), 5),
                    (short) 1,
                    Team.MeetingPlace.CAMPUS
            );
            entityManager.persist(team);

            entityManager.persist(TeamMember.createLeader(team.getId(), leader.getId()));
            entityManager.persist(TeamRecruitment.create(team.getId(), "BACKEND", (short) 2));
            entityManager.persist(TeamRecruitment.create(team.getId(), "FRONTEND", (short) 2));
            writtenEntities += 3;

            for (int memberIndex = 1; memberIndex < properties.teamMembersPerTeam(); memberIndex++) {
                User member = users.get(userIndex(users, teamIndex + memberIndex + 1));
                String roleCode = memberIndex % 2 == 0 ? "FRONTEND" : "BACKEND";
                entityManager.persist(TeamMember.createMember(team.getId(), member.getId(), roleCode));
                writtenEntities++;
                flushChunk(writtenEntities, properties.batchSize());
            }

            for (int applicationIndex = 0;
                 applicationIndex < properties.applicationsPerTeam();
                 applicationIndex++) {
                User applicant = users.get(userIndex(
                        users,
                        properties.teamCount() + teamIndex + applicationIndex + 1
                ));
                entityManager.persist(TeamApplication.create(
                        team.getId(),
                        applicant.getId(),
                        applicationIndex % 2 == 0 ? "BACKEND" : "FRONTEND",
                        "[PERF] 성능 측정용 팀 참가 신청입니다."
                ));
                writtenEntities++;
                flushChunk(writtenEntities, properties.batchSize());
            }
        }
        flushAndClear();
        return properties.teamCount() * properties.teamMembersPerTeam();
    }

    private long createPersonalChats(
            PerformanceSeedProperties properties,
            List<User> users
    ) {
        User measurementUser = users.getFirst();
        long writtenMessages = 0;
        int writtenEntities = 0;

        for (int roomIndex = 0; roomIndex < properties.chatRoomCount(); roomIndex++) {
            User partner = users.get(roomIndex + 1);
            ChatRoom room = ChatRoom.createPersonalRoom(
                    measurementUser.getId(),
                    partner.getId()
            );
            entityManager.persist(room);
            entityManager.persist(ChatRoomMember.join(room.getId(), measurementUser.getId()));
            entityManager.persist(ChatRoomMember.join(room.getId(), partner.getId()));
            writtenEntities += 3;

            for (int messageIndex = 0;
                 messageIndex < properties.messagesPerRoom();
                 messageIndex++) {
                Long senderId = messageIndex % 2 == 0
                        ? measurementUser.getId()
                        : partner.getId();
                String clientMessageId = deterministicMessageId(roomIndex, messageIndex);
                entityManager.persist(ChatMessage.createText(
                        room.getId(),
                        senderId,
                        clientMessageId,
                        "[PERF] 채팅방 %05d 메시지 %06d".formatted(
                                roomIndex + 1,
                                messageIndex + 1
                        )
                ));
                writtenMessages++;
                writtenEntities++;
                flushChunk(writtenEntities, properties.batchSize());
            }
        }
        flushAndClear();
        return writtenMessages;
    }

    private int userIndex(List<User> users, int candidate) {
        return 1 + Math.floorMod(candidate - 1, users.size() - 1);
    }

    private String deterministicMessageId(int roomIndex, int messageIndex) {
        String seed = "giut-perf-%d-%d".formatted(roomIndex, messageIndex);
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void flushChunk(int writtenEntities, int batchSize) {
        if (writtenEntities > 0 && writtenEntities % batchSize == 0) {
            flushAndClear();
        }
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
