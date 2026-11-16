package com.giut.server.performance;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.performance.seed")
public record PerformanceSeedProperties(
        boolean enabled,
        String confirmation,
        String measurementPassword,
        int teamCount,
        int teamMembersPerTeam,
        int applicationsPerTeam,
        int chatRoomCount,
        int messagesPerRoom,
        int batchSize
) {

    public static final String REQUIRED_CONFIRMATION = "CREATE_PERFORMANCE_DATA";

    public void validate() {
        if (!enabled) {
            throw new IllegalStateException("성능 측정 데이터 생성이 활성화되지 않았습니다.");
        }
        if (!REQUIRED_CONFIRMATION.equals(confirmation)) {
            throw new IllegalStateException(
                    "성능 측정 데이터를 생성하려면 확인 문구를 정확히 입력해야 합니다."
            );
        }
        if (measurementPassword == null || measurementPassword.isBlank()) {
            throw new IllegalStateException("성능 측정 계정 비밀번호는 필수입니다.");
        }
        requireRange("team-count", teamCount, 1, 10_000);
        requireRange("team-members-per-team", teamMembersPerTeam, 1, 50);
        requireRange("applications-per-team", applicationsPerTeam, 0, 100);
        requireRange("chat-room-count", chatRoomCount, 1, 10_000);
        requireRange("messages-per-room", messagesPerRoom, 1, 100_000);
        requireRange("batch-size", batchSize, 1, 5_000);

        long totalMessages = (long) chatRoomCount * messagesPerRoom;
        if (totalMessages > 2_000_000L) {
            throw new IllegalArgumentException("생성할 채팅 메시지는 최대 2,000,000개입니다.");
        }
    }

    public int requiredUserCount() {
        int usersForTeams = teamCount + teamMembersPerTeam + applicationsPerTeam + 1;
        int usersForChat = chatRoomCount + 1;
        return Math.max(usersForTeams, usersForChat);
    }

    private static void requireRange(String property, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    property + " 값은 " + min + " 이상 " + max + " 이하여야 합니다."
            );
        }
    }
}
