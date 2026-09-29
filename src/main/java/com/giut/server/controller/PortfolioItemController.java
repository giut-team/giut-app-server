package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.profile.request.PortfolioShowcaseRequest;
import com.giut.server.dto.profile.response.PortfolioItemListResponse;
import com.giut.server.dto.profile.response.PortfolioShowcaseResponse;
import com.giut.server.dto.profile.common.PortfolioItemDto;
import com.giut.server.dto.profile.response.RepresentativePortfolioResponse;
import com.giut.server.service.PortfolioItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Portfolio", description = "내 프로필 포트폴리오 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/portfolio-items")
public class PortfolioItemController {

    private final PortfolioItemService portfolioItemService;

    @GetMapping
    @Operation(summary = "내 포트폴리오 전체 목록 조회", description = "공개 여부와 관계없이 등록한 모든 포트폴리오를 최신 등록순으로 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PortfolioItemListResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<PortfolioItemListResponse> getMyPortfolioItems(Authentication authentication) {
        return ResponseEntity.ok(portfolioItemService.getMyPortfolioItems(currentUserId(authentication)));
    }

    @PostMapping
    @Operation(summary = "포트폴리오 항목 추가", description = "대표 사진 한 장, 프로젝트 기간·팀 인원·기술 스택 및 Markdown 상세 내용을 등록합니다. 새 항목은 기본적으로 공개 프로필에 숨김 처리됩니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PortfolioItemDto.class), examples = @ExampleObject(value = "{\"id\":1,\"imageUrl\":\"https://cdn.giut.com/portfolio/esg-campaign.png\",\"title\":\"ESG 캠페인 팀 회의\",\"caption\":\"일정 정리와 데이터 집계를 담당했어요.\",\"projectStartDate\":\"2025-09-01\",\"projectEndDate\":\"2025-12-31\",\"teamSize\":5,\"markdownContent\":\"## 프로젝트 소개\\n\\n분리배출 캠페인의 일정과 산출물을 정리했습니다.\",\"skillTags\":[{\"id\":1,\"type\":\"SKILL\",\"name\":\"Python\"}],\"showcaseOrder\":null,\"representative\":false}"))),
            @ApiResponse(responseCode = "400", description = "요청값 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<PortfolioItemDto> createPortfolioItem(
            Authentication authentication,
            @Valid @RequestBody PortfolioItemDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(portfolioItemService.createPortfolioItem(currentUserId(authentication), request));
    }

    @PutMapping("/{portfolioItemId}")
    @Operation(summary = "포트폴리오 항목 수정", description = "내 포트폴리오 항목의 대표 사진, 프로젝트 정보, 기술 스택, Markdown 상세 내용을 전체 수정합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PortfolioItemDto.class))),
            @ApiResponse(responseCode = "400", description = "요청값 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "404", description = "항목을 찾을 수 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<PortfolioItemDto> updatePortfolioItem(
            Authentication authentication,
            @PathVariable Long portfolioItemId,
            @Valid @RequestBody PortfolioItemDto request
    ) {
        return ResponseEntity.ok(portfolioItemService.updatePortfolioItem(
                currentUserId(authentication), portfolioItemId, request
        ));
    }

    @DeleteMapping("/{portfolioItemId}")
    @Operation(summary = "포트폴리오 항목 삭제", description = "내 포트폴리오 항목을 삭제하고 남은 항목의 노출 순서를 다시 정리합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "404", description = "항목을 찾을 수 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<Void> deletePortfolioItem(
            Authentication authentication,
            @PathVariable Long portfolioItemId
    ) {
        portfolioItemService.deletePortfolioItem(currentUserId(authentication), portfolioItemId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/showcase")
    @Operation(summary = "공개 포트폴리오 목록 설정", description = "공개 프로필에 노출할 포트폴리오를 최대 6개까지 설정하고 전달한 순서대로 배치합니다. 목록에서 제외된 항목은 삭제되지 않고 숨김 처리됩니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공개 포트폴리오 목록 설정 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PortfolioShowcaseResponse.class))),
            @ApiResponse(responseCode = "400", description = "6개 초과, 중복 ID 또는 내 포트폴리오가 아닌 항목 포함", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<PortfolioShowcaseResponse> changePortfolioShowcase(
            Authentication authentication,
            @Valid @RequestBody PortfolioShowcaseRequest request
    ) {
        return ResponseEntity.ok(portfolioItemService.changePortfolioShowcase(
                currentUserId(authentication),
                request
        ));
    }

    @PatchMapping("/{portfolioItemId}/representative")
    @Operation(summary = "대표 포트폴리오 선택", description = "공개 프로필에 노출 중인 포트폴리오 하나를 대표로 지정합니다. 기존 대표 포트폴리오는 자동 해제됩니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대표 포트폴리오 지정 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RepresentativePortfolioResponse.class))),
            @ApiResponse(responseCode = "400", description = "숨김 포트폴리오를 대표로 지정하려는 요청", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "404", description = "내 포트폴리오 항목을 찾을 수 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<RepresentativePortfolioResponse> setRepresentativePortfolio(
            Authentication authentication,
            @PathVariable Long portfolioItemId
    ) {
        return ResponseEntity.ok(portfolioItemService.setRepresentativePortfolio(
                currentUserId(authentication),
                portfolioItemId
        ));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
