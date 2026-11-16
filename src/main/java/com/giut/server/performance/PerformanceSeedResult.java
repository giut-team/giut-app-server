package com.giut.server.performance;

public record PerformanceSeedResult(
        boolean created,
        Long measurementUserId,
        Long competitionId,
        int userCount,
        int teamCount,
        int teamMemberCount,
        int applicationCount,
        int chatRoomCount,
        long messageCount
) {

    public static PerformanceSeedResult alreadyExists(Long measurementUserId) {
        return new PerformanceSeedResult(
                false,
                measurementUserId,
                null,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }
}
