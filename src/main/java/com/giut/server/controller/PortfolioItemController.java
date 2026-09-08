package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.profile.request.PortfolioItemOrderRequest;
import com.giut.server.dto.profile.request.PortfolioItemRequest;
import com.giut.server.dto.profile.response.PortfolioItemListResponse;
import com.giut.server.dto.profile.response.PortfolioItemResponse;
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
    @Operation(summary = "내 포트폴리오 목록 조회", description = "등록한 포트폴리오를 노출 순서대로 조회합니다.")
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
    @Operation(summary = "포트폴리오 항목 추가", description = "활동 사진 URL, 제목, 카드용 캡션, 상세 내용을 등록합니다. 한 프로필에는 최대 6개까지 등록할 수 있습니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PortfolioItemResponse.class), examples = @ExampleObject(value = "{\"id\":1,\"imageUrl\":\"https://cdn.giut.com/portfolio/data-contest.png\",\"title\":\"서울시 데이터 공모전 발표\",\"caption\":\"데이터 정책부터 발표까지 맡았어요.\",\"content\":\"문제 정의와 데이터 분석, 발표 자료 제작을 담당했습니다.\",\"displayOrder\":1}"))),
            @ApiResponse(responseCode = "400", description = "요청값 오류 또는 최대 개수 초과", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"IllegalStateException : 포트폴리오는 최대 6개까지 등록할 수 있습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<PortfolioItemResponse> createPortfolioItem(
            Authentication authentication,
            @Valid @RequestBody PortfolioItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(portfolioItemService.createPortfolioItem(currentUserId(authentication), request));
    }

    @PutMapping("/{portfolioItemId}")
    @Operation(summary = "포트폴리오 항목 수정", description = "내 포트폴리오 항목의 사진, 제목, 캡션, 상세 내용을 전체 수정합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PortfolioItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청값 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "404", description = "항목을 찾을 수 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<PortfolioItemResponse> updatePortfolioItem(
            Authentication authentication,
            @PathVariable Long portfolioItemId,
            @Valid @RequestBody PortfolioItemRequest request
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

    @PutMapping("/order")
    @Operation(summary = "포트폴리오 노출 순서 변경", description = "현재 내 포트폴리오 ID 전체를 원하는 순서로 전달합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "순서 변경 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PortfolioItemListResponse.class))),
            @ApiResponse(responseCode = "400", description = "누락·중복·다른 사용자의 항목이 포함된 요청", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "404", description = "항목을 찾을 수 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<PortfolioItemListResponse> changePortfolioItemOrder(
            Authentication authentication,
            @Valid @RequestBody PortfolioItemOrderRequest request
    ) {
        return ResponseEntity.ok(portfolioItemService.changePortfolioItemOrder(currentUserId(authentication), request));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
