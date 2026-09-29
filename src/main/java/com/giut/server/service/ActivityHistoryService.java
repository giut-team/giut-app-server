package com.giut.server.service;

import com.giut.server.dto.profile.common.ActivityHistoryDto;
import com.giut.server.dto.profile.response.ActivityHistoryListResponse;
import com.giut.server.entity.ActivityHistory;
import com.giut.server.entity.User;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.ActivityHistoryRepository;
import com.giut.server.repository.UserRepository;
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
