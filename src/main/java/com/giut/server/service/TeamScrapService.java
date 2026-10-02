package com.giut.server.service;

import com.giut.server.dto.team.response.TeamScrapResponse;
import com.giut.server.entity.Team;
import com.giut.server.entity.TeamScrap;
import com.giut.server.entity.User;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.TeamRepository;
import com.giut.server.repository.TeamScrapRepository;
import com.giut.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamScrapService {

    private final TeamScrapRepository teamScrapRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Transactional
    public TeamScrapResponse scrap(Long userId, Long teamId) {
        User user = userRepository.findById(userId)
                .filter(found -> found.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("팀을 찾을 수 없습니다."));
        if (!teamScrapRepository.existsByUser_IdAndTeam_Id(userId, teamId)) {
            teamScrapRepository.save(TeamScrap.create(user, team));
        }
        return new TeamScrapResponse(teamId, true);
    }

    @Transactional
    public TeamScrapResponse removeScrap(Long userId, Long teamId) {
        teamScrapRepository.deleteByUser_IdAndTeam_Id(userId, teamId);
        return new TeamScrapResponse(teamId, false);
    }
}
