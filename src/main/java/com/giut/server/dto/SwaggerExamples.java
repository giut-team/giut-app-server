package com.giut.server.dto;

/** Swagger 응답 예시. 목록은 데이터가 있는 상태를 보여주되 실제 조회 결과는 비어 있을 수 있다. */
public final class SwaggerExamples {

    private SwaggerExamples() {
    }

    public static final String PORTFOLIO_ITEM = """
            {"id":1,"imageUrl":"https://cdn.giut.com/portfolio/data-contest.png","title":"서울시 데이터 공모전 발표","caption":"데이터 분석과 발표를 담당했어요.","projectStartDate":"2025-09-01","projectEndDate":"2025-12-31","teamSize":5,"roles":[{"code":"DATA_ANALYST","name":"데이터 분석"}],"markdownContent":"## 프로젝트 소개","skillTags":[{"id":1,"type":"SKILL","name":"Python"}],"showcaseOrder":1,"representative":true}
            """;

    public static final String PORTFOLIO_LIST = """
            {"portfolioItems":[{"id":1,"imageUrl":"https://cdn.giut.com/portfolio/data-contest.png","title":"서울시 데이터 공모전 발표","caption":"데이터 분석과 발표를 담당했어요.","projectStartDate":"2025-09-01","projectEndDate":"2025-12-31","teamSize":5,"roles":[{"code":"DATA_ANALYST","name":"데이터 분석"}],"markdownContent":"## 프로젝트 소개","skillTags":[{"id":1,"type":"SKILL","name":"Python"}],"showcaseOrder":1,"representative":true}]}
            """;

    public static final String PORTFOLIO_SHOWCASE = """
            {"portfolioItems":[{"id":1,"imageUrl":"https://cdn.giut.com/portfolio/data-contest.png","title":"서울시 데이터 공모전 발표","caption":"데이터 분석과 발표를 담당했어요.","projectStartDate":"2025-09-01","projectEndDate":"2025-12-31","teamSize":5,"roles":[{"code":"DATA_ANALYST","name":"데이터 분석"}],"markdownContent":"## 프로젝트 소개","skillTags":[{"id":1,"type":"SKILL","name":"Python"}],"showcaseOrder":1,"representative":true}],"representativePortfolioItemId":1}
            """;

    public static final String ACTIVITY_HISTORY = """
            {"id":1,"category":"AWARD","categoryName":"수상","title":"서울시 데이터 활용 공모전 우수상","organization":"서울특별시","startMonth":"2025-03","endMonth":"2025-06"}
            """;

    public static final String ACTIVITY_HISTORY_LIST = """
            {"activityHistories":[{"id":1,"category":"AWARD","categoryName":"수상","title":"서울시 데이터 활용 공모전 우수상","organization":"서울특별시","startMonth":"2025-03","endMonth":"2025-06"}]}
            """;

    public static final String COMPETITION_LIST = """
            [{"id":1,"title":"서울시 데이터 분석 공모전","category":"WEB_MOBILE_IT","categoryName":"웹/모바일/IT","hostOrganization":"서울특별시","summary":"공공 데이터 활용 아이디어 공모전","applicationStartAt":"2026-09-15T00:00:00Z","applicationEndAt":"2026-10-06T14:59:59Z","recruitmentStatus":"OPEN","recruitmentStatusName":"모집 중","viewCount":42,"scrapCount":8,"primaryUrl":"https://example.com/recruitment"}]
            """;

    public static final String COMPETITION_DETAIL = """
            {"id":1,"title":"서울시 데이터 분석 공모전","category":"WEB_MOBILE_IT","categoryName":"웹/모바일/IT","hostOrganization":"서울특별시","targetParticipants":"전국 대학생","summary":"공공 데이터 활용 아이디어 공모전","applicationStartAt":"2026-09-15T00:00:00Z","applicationEndAt":"2026-10-06T14:59:59Z","recruitmentStatus":"OPEN","recruitmentStatusName":"모집 중","viewCount":43,"scrapCount":8,"scrapped":true,"urls":[{"id":1,"type":"RECRUITMENT","url":"https://example.com/recruitment","primary":true}]}
            """;

    public static final String ADMIN_COMPETITION = """
            {"id":1,"title":"서울시 데이터 분석 공모전","category":"WEB_MOBILE_IT","hostOrganization":"서울특별시","targetParticipants":"전국 대학생","summary":"공공 데이터 활용 아이디어 공모전","applicationStartAt":"2026-09-15T00:00:00Z","applicationEndAt":"2026-10-06T14:59:59Z","publicationStatus":"DRAFT","verificationStatus":"MANUALLY_VERIFIED","verifiedAt":"2026-10-02T06:00:00Z","urls":[{"id":1,"type":"RECRUITMENT","url":"https://example.com/recruitment","primary":true}]}
            """;

    public static final String ADMIN_COMPETITION_PUBLISHED = """
            {"id":1,"title":"서울시 데이터 분석 공모전","category":"WEB_MOBILE_IT","hostOrganization":"서울특별시","targetParticipants":"전국 대학생","summary":"공공 데이터 활용 아이디어 공모전","applicationStartAt":"2026-09-15T00:00:00Z","applicationEndAt":"2026-10-06T14:59:59Z","publicationStatus":"PUBLISHED","verificationStatus":"MANUALLY_VERIFIED","verifiedAt":"2026-10-02T06:00:00Z","urls":[{"id":1,"type":"RECRUITMENT","url":"https://example.com/recruitment","primary":true}]}
            """;

    public static final String PROFILE_REPORT_LIST = """
            {"reports":[{"id":1,"reporterUserId":3,"reportedProfileUserId":12,"reason":"FALSE_INFORMATION_IMPERSONATION","status":"PENDING","createdAt":"2026-10-02T06:00:00Z"}],"page":0,"size":20,"totalElements":1,"totalPages":1}
            """;

    public static final String SHARED_PROFILE = """
            {"nickname":"김민재","universityVerified":true,"profileImageUrl":"https://cdn.giut.com/profiles/12.png","activityStatus":"LOOKING_FOR_TEAM","activityStatusName":"팀 찾는 중","primaryRoles":[{"code":"DEVELOPMENT","name":"개발"}],"departmentName":"컴퓨터과학부","grade":3,"bio":"백엔드 프로젝트에 관심이 있습니다.","skills":[{"id":1,"type":"SKILL","name":"Python"}]}
            """;
}
