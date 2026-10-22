package com.giut.server.competition.seed;

import com.giut.server.competition.dto.request.CompetitionUrlRequest;
import com.giut.server.competition.dto.request.UpsertCompetitionRequest;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionUrl;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/** Fixed sample snapshot researched on 2026-10-05. Sources and date assumptions are in docs/admin-sample-competitions.md. */
@Component
public class SampleCompetitionCatalog {

    private static final List<UpsertCompetitionRequest> COMPETITIONS = List.of(
            sample(
                    "국부펀드 KIC, AI 에이전트 공모전",
                    Competition.Category.WEB_MOBILE_IT,
                    "한국투자공사(KIC)",
                    "누구나 참가 가능. 개인 또는 최대 5인 팀.",
                    "투자·금융 업무에 사용할 AI 에이전트를 기획하고 MVP를 구현하는 공모전입니다.",
                    "2026-10-01T00:00:00+09:00", "2026-11-12T10:00:00+09:00",
                    "https://daker.ai/public/hackathons/kic-ai-agent-competition"
            ),
            sample(
                    "2026 금융 AI Challenge: 상상을 넘어 실제로, AI로 움직이는 금융의 미래",
                    Competition.Category.WEB_MOBILE_IT,
                    "금융보안원",
                    "전 국민. 개인 또는 최대 4인 팀.",
                    "금융 문제를 AI로 해결할 서비스 아이디어와 웹 기반 MVP를 제안하는 공모전입니다.",
                    "2026-07-13T00:00:00+09:00", "2026-09-07T10:00:00+09:00",
                    "https://daker.ai/public/hackathons/2026-finance-ai-challenge"
            ),
            sample(
                    "항만 문제해결을 위한 AI 기술 공모전",
                    Competition.Category.SCIENCE_ENGINEERING,
                    "해양수산부 / 울산항만공사 / 한국정보산업연합회",
                    "국내외 고등·대학(원) 재학생 또는 졸업 후 3년 이내 미취업자.",
                    "선박 정보를 활용한 매칭 모델을 만들어 항만 업무의 문제를 해결하는 AI 공모전입니다.",
                    "2026-10-12T00:00:00+09:00", "2026-11-01T23:59:59+09:00",
                    "https://dacon.io/competitions/official/236771/overview/description"
            ),
            sample(
                    "Aimers 9기 : 투구 제구 성공 확률 예측 AI 온라인 해커톤",
                    Competition.Category.GAME_SOFTWARE,
                    "LG AI 연구원",
                    "LG Aimers 9기 교육생. 상세 참여 조건은 원문 참고.",
                    "야구 경기 데이터를 분석하여 투구의 제구 성공 가능성을 예측하는 AI 모델을 개발하는 해커톤입니다.",
                    "2026-08-05T00:00:00+09:00", "2026-09-01T23:59:59+09:00",
                    "https://dacon.io/competitions/official/236743/overview/description"
            ),
            sample(
                    "2026 인하 인공지능 챌린지",
                    Competition.Category.SCIENCE_ENGINEERING,
                    "인하대학교 인공지능융합연구센터 등",
                    "AI·로봇에 관심 있는 인하대학교 학부생·대학원생. 2~5인 팀, 휴학생 제외.",
                    "로봇의 이미지와 행동 순서를 입력으로 받아 이후 움직임을 영상으로 생성하는 월드 모델 개발 대회입니다.",
                    "2026-07-16T10:00:00+09:00", "2026-08-17T23:59:00+09:00",
                    "https://dacon.io/competitions/official/236736/overview/description"
            ),
            sample(
                    "제3회 풍력발전량 예측 AI 경진대회 - BARAM 2026",
                    Competition.Category.SCIENCE_ENGINEERING,
                    "한국동서발전 / GS E&R / 태백가덕산풍력발전",
                    "국내외 대학생·대학원생, 졸업예정자·취업준비생. 제1·2회 수상자 제외.",
                    "기상예보와 발전 데이터를 이용하여 풍력발전량을 예측하고 예측 결과의 활용 가능성을 평가하는 대회입니다.",
                    "2026-06-12T00:00:00+09:00", "2026-08-14T23:59:59+09:00",
                    "https://dacon.io/competitions/official/236727/overview/description"
            ),
            sample(
                    "2026 AI·SW중심대학 디지털 경진대회 : SW부문",
                    Competition.Category.WEB_MOBILE_IT,
                    "정보통신기획평가원 / AI·SW중심대학협의회",
                    "AI·SW중심대학 재학생·휴학생. 전공 무관, 졸업생 제외.",
                    "생활 속 문제를 해결할 AI 에이전트 기반 소프트웨어를 개발하는 대학생 경진대회입니다.",
                    "2026-05-26T00:00:00+09:00", "2026-06-08T23:59:59+09:00",
                    "https://dacon.io/competitions/official/236693/overview/description"
            ),
            sample(
                    "2026 AI·SW중심대학 디지털 경진대회 : AI부문",
                    Competition.Category.GAME_SOFTWARE,
                    "정보통신기획평가원 / AI·SW중심대학협의회",
                    "AI·SW중심대학 재학생·휴학생. 전공 무관, 졸업생 제외.",
                    "AI 에이전트의 작업 맥락을 분석해 다음 행동을 예측하는 의사결정 모델 개발 대회입니다.",
                    "2026-05-26T00:00:00+09:00", "2026-06-08T23:59:59+09:00",
                    "https://dacon.io/competitions/official/236694/overview/description"
            ),
            sample(
                    "2026 성균관대학교 멀티모달 AI Bias 챌린지",
                    Competition.Category.GAME_SOFTWARE,
                    "성균관대학교 지능형멀티미디어연구센터 / 딥페이크연구센터 / 실감미디어공학과",
                    "AI·LLM에 관심 있는 대학(원) 재학생·휴학생. 졸업유예생 제외.",
                    "이미지와 텍스트 질의응답에서 근거를 바탕으로 판단하고 편향을 줄이는 멀티모달 AI 모델 개발 대회입니다.",
                    "2026-06-01T00:00:00+09:00", "2026-06-29T23:59:59+09:00",
                    "https://dacon.io/competitions/official/236722/overview/description"
            ),
            sample(
                    "제 5회 ETRI 휴먼이해 인공지능 논문경진대회",
                    Competition.Category.PAPER_REPORT,
                    "한국전자통신연구원(ETRI)",
                    "일반 성인 및 국내 대학 재학·휴학·졸업 증빙이 가능한 참가자. 상세 조건은 원문 참고.",
                    "라이프로그를 분석해 수면·감정·스트레스 관련 지표를 예측하고 연구 결과를 논문으로 제출하는 대회입니다.",
                    "2026-04-13T00:00:00+09:00", "2026-06-26T23:59:59+09:00",
                    "https://dacon.io/competitions/official/236690/overview/description"
            )
    );

    public List<UpsertCompetitionRequest> competitions() {
        return COMPETITIONS;
    }

    private static UpsertCompetitionRequest sample(
            String title, Competition.Category category, String hostOrganization,
            String targetParticipants, String summary, String startAt, String endAt, String sourceUrl
    ) {
        return new UpsertCompetitionRequest(
                title, category, hostOrganization, targetParticipants,
                "[테스트 데이터] " + summary + " 실제 공모전 공고를 참고한 샘플입니다. 참가 조건과 최신 일정은 출처에서 확인하세요.",
                OffsetDateTime.parse(startAt).toInstant(),
                OffsetDateTime.parse(endAt).toInstant(),
                Competition.PublicationStatus.PUBLISHED,
                List.of(new CompetitionUrlRequest(CompetitionUrl.Type.SOURCE_ORIGINAL, sourceUrl, true))
        );
    }
}

