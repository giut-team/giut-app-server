package com.giut.server.profile.service;

import com.giut.server.profile.dto.common.ActivityHistoryDto;
import com.giut.server.profile.dto.response.ActivityHistoryListResponse;
import com.giut.server.profile.entity.ActivityHistory;
import com.giut.server.user.entity.User;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.profile.repository.ActivityHistoryRepository;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityHistoryService {

    private final ActivityHistoryRepository activityHistoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ActivityHistoryListResponse getMyActivityHistories(Long userId) {
        return toListResponse(activityHistoryRepository
                .findAllByUser_IdOrderByStartMonthDescEndMonthDescIdDesc(userId));
    }

    @Transactional
    public ActivityHistoryDto createActivityHistory(Long userId, ActivityHistoryDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        return ActivityHistoryDto.from(createForProfile(user, List.of(request)).getFirst());
    }

    List<ActivityHistory> createForProfile(User user, List<ActivityHistoryDto> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return activityHistoryRepository.saveAll(requests.stream()
                .map(request -> {
                    validatePeriod(request);
                    return ActivityHistory.create(
                            user,
                            request.category(),
                            request.title(),
                            request.organization(),
                            request.startMonth(),
                            request.endMonth()
                    );
                })
                .toList());
    }

    // 요청 DTO의 id는 읽기 전용이라(ActivityHistoryDto 참고) 어떤 항목이 기존 몇 번 행인지 알 수 없다.
    // 그래서 개별 수정이 아니라 전체 삭제 후 재생성으로 프로필 저장 시 목록을 통째로 교체한다.
    void replaceForProfile(User user, List<ActivityHistoryDto> requests) {
        if (requests == null) {
            throw new IllegalArgumentException("활동 이력 목록은 필수입니다.");
        }
        requests.forEach(this::validatePeriod);
        // deleteByUser_Id는 derived delete라 실제 DELETE가 다음 flush까지 지연된다. createForProfile의 insert는
        // IDENTITY 전략이라 즉시 나가므로, 여기서 명시적으로 flush하지 않으면 DB에는 insert가 delete보다 먼저 반영된다.
        activityHistoryRepository.deleteByUser_Id(user.getId());
        activityHistoryRepository.flush();
        createForProfile(user, requests);
    }

    @Transactional
    public ActivityHistoryDto updateActivityHistory(
            Long userId,
            Long activityHistoryId,
            ActivityHistoryDto request
    ) {
        ActivityHistory activityHistory = findActivityHistory(userId, activityHistoryId);
        validatePeriod(request);
        activityHistory.update(
                request.category(),
                request.title(),
                request.organization(),
                request.startMonth(),
                request.endMonth()
        );
        return ActivityHistoryDto.from(activityHistory);
    }

    @Transactional
    public void deleteActivityHistory(Long userId, Long activityHistoryId) {
        activityHistoryRepository.delete(findActivityHistory(userId, activityHistoryId));
    }

    private ActivityHistory findActivityHistory(Long userId, Long activityHistoryId) {
        return activityHistoryRepository.findByIdAndUser_Id(activityHistoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("활동 이력을 찾을 수 없습니다."));
    }

    private void validatePeriod(ActivityHistoryDto request) {
        if (request.endMonth().isBefore(request.startMonth())) {
            throw new IllegalArgumentException("활동 종료월은 시작월보다 빠를 수 없습니다.");
        }
    }

    private ActivityHistoryListResponse toListResponse(List<ActivityHistory> activityHistories) {
        return new ActivityHistoryListResponse(activityHistories.stream()
                .map(ActivityHistoryDto::from)
                .toList());
    }
}
