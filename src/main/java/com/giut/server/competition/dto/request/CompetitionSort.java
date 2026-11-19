package com.giut.server.competition.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Sort;

/**
 * 공개 공모전 목록 정렬(앱 정렬 시트: 마감 임박 순·최근 등록 순·조회수 순·스크랩 순).
 * 동률이면 최신 등록(ID 내림차순)을 앞에 둬 페이지 사이에 순서가 흔들리지 않게 한다.
 *
 * <p>DEADLINE·LATEST는 기존 인덱스(publication_status, application_end_at / publication_status, id)를 쓴다.
 * VIEWS(view_count)는 인덱스가 없고, SCRAPS는 스크랩 수 컬럼이 없어 competition_scraps를 공모전별로 세는
 * 상관 서브쿼리로 정렬한다. 둘 다 정렬 비용이 공개 공모전 수에 비례한다.
 */
@Schema(description = "정렬 기준. DEADLINE: 마감 임박 순, LATEST: 최근 등록 순(기본), VIEWS: 조회수 순, SCRAPS: 스크랩 순")
public enum CompetitionSort {
    DEADLINE(Sort.by(Sort.Direction.ASC, "applicationEndAt").and(Sort.by(Sort.Direction.DESC, "id"))),
    LATEST(Sort.by(Sort.Direction.DESC, "id")),
    VIEWS(Sort.by(Sort.Direction.DESC, "viewCount").and(Sort.by(Sort.Direction.DESC, "id"))),
    /** 엔티티 컬럼이 아니라 스크랩 수 서브쿼리로 정렬하므로 Sort가 아니라 조회 조건(Specification)에서 정렬한다. */
    SCRAPS(Sort.unsorted());

    private final Sort sort;

    CompetitionSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }
}
