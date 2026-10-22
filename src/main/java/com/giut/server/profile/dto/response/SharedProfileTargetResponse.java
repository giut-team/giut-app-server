package com.giut.server.profile.dto.response;

/** 회원이 유효한 공유 링크에서 공개 상세 프로필로 이동할 때 사용하는 식별자. */
public record SharedProfileTargetResponse(Long userId) {
}
