package com.giut.server.team.dto.request;

import com.giut.server.team.entity.TeamApplication.MeetingTimeSlot;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.HashSet;

@Schema(description = "팀 참가 신청 요청")
public record ApplyTeamRequest(
        @Schema(description = "지원 분야 코드", example = "BACKEND_DEVELOPER")
        @NotBlank(message = "지원 분야는 필수입니다.")
        String roleCode,

        @Schema(description = "지원 메시지", example = "백엔드 개발로 참여하고 싶습니다.", nullable = true)
        @Size(max = 500, message = "지원 메시지는 500자 이하여야 합니다.")
        String message,

        @Schema(description = "지원서 질문별 답변 목록", example = "[{\"questionId\":1,\"answer\":\"서울시 공공데이터를 다뤄본 경험이 있습니다.\"}]")
        @NotNull(message = "지원서 답변 목록은 필수입니다. 질문이 없다면 빈 배열을 입력하세요.")
        List<@Valid TeamApplicationAnswerRequest> answers,

        @Schema(description = "주간 투자 가능 시간. 20은 20시간 이상을 의미합니다. 구버전 요청은 생략할 수 있습니다.",
                example = "20", nullable = true)
        @Min(value = 1, message = "주간 투자 가능 시간은 1 이상이어야 합니다.")
        @Max(value = 20, message = "주간 투자 가능 시간은 20 이하여야 합니다.")
        Integer weeklyAvailableHours,

        @Schema(description = "회의 가능 시간대. 생략하거나 빈 배열로 보낼 수 있습니다. 1개 이상 선택하면 weeklyAvailableHours도 입력해야 합니다.",
                example = "[\"MORNING\",\"EVENING\"]")
        @Size(max = 3, message = "회의 가능 시간대는 최대 3개까지 선택할 수 있습니다.")
        List<@NotNull(message = "회의 가능 시간대에 빈 값을 포함할 수 없습니다.") MeetingTimeSlot> meetingTimeSlots
) {
    @AssertTrue(message = "회의 가능 시간대를 선택하면 주간 투자 가능 시간도 입력해야 합니다.")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isMeetingTimeSlotsWithWeeklyHours() {
        return meetingTimeSlots == null || meetingTimeSlots.isEmpty() || weeklyAvailableHours != null;
    }

    @AssertTrue(message = "회의 가능 시간대는 한 번씩만 선택할 수 있습니다.")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isMeetingTimeSlotsUnique() {
        return meetingTimeSlots == null || new HashSet<>(meetingTimeSlots).size() == meetingTimeSlots.size();
    }
}
