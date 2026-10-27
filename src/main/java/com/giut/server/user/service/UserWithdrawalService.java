package com.giut.server.user.service;

import com.giut.server.global.exception.ConflictException;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.team.entity.Team;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    @Transactional
    public void withdraw(Long userId) {
        // Team creation locks this same user before inserting a team.
        User user = userRepository.findByIdForUpdate(userId)
                .filter(found -> found.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("탈퇴할 수 없는 계정입니다."));
        if (user.getRole() == User.Role.ADMIN) {
            throw new ForbiddenException("관리자 계정은 회원 탈퇴 API를 사용할 수 없습니다.");
        }
        if (teamRepository.existsByLeader_IdAndStatusNot(userId, Team.Status.ARCHIVED)) {
            throw new ConflictException("팀장인 팀을 먼저 보관 처리한 후 탈퇴해 주세요.");
        }
        user.withdraw();
    }
}
