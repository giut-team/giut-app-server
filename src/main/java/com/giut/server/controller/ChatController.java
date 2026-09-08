package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.chat.request.CreatePersonalChatRoomRequest;
import com.giut.server.dto.chat.request.SendChatMessageRequest;
import com.giut.server.dto.chat.response.ChatMessageListResponse;
import com.giut.server.dto.chat.response.ChatMessageResponse;
import com.giut.server.dto.chat.response.ChatRoomResponse;
import com.giut.server.service.ChatService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "채팅 메시지 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-rooms")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/personal")
    @Operation(
            summary = "개인 채팅방 생성",
            description = "현재 로그인한 사용자와 상대 사용자 사이의 개인 채팅방을 생성합니다. 이미 활성화된 개인 채팅방이 있으면 기존 채팅방을 반환합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "개인 채팅방 생성 또는 기존 채팅방 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChatRoomResponse.class),
                            examples = @ExampleObject(value = "{\"chatRoomId\":5,\"type\":\"PERSONAL\",\"status\":\"ACTIVE\",\"targetUserId\":15,\"created\":true,\"createdAt\":\"2026-09-08T11:10:00Z\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "자기 자신과의 채팅방 생성 요청",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"자기 자신과는 개인 채팅방을 만들 수 없습니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자 또는 상대 사용자를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 상대 사용자를 찾을 수 없습니다.\",\"code\":404}"))
            )
    })
    public ResponseEntity<ChatRoomResponse> createPersonalChatRoom(
            Authentication authentication,
            @Valid @RequestBody CreatePersonalChatRoomRequest request
    ) {
        Long userId = Long.valueOf(authentication.getName());
        ChatRoomResponse response = chatService.createPersonalChatRoom(userId, request);
        HttpStatus status = response.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/{chatRoomId}/messages")
    @Operation(
            summary = "채팅 메시지 전송",
            description = "채팅방 참여자가 텍스트 메시지를 전송합니다. MVP 기준으로는 REST 저장만 처리하며, 실시간 전송은 이후 WebSocket 단계에서 추가합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "메시지 전송 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChatMessageResponse.class),
                            examples = @ExampleObject(value = "{\"messageId\":1,\"chatRoomId\":5,\"senderId\":12,\"content\":\"안녕하세요. 팀 채팅 테스트입니다.\",\"messageType\":\"TEXT\",\"status\":\"ACTIVE\",\"createdAt\":\"2026-09-08T11:00:00Z\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청값 또는 채팅방 참여자가 아닌 사용자",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"채팅방 참여자만 메시지를 이용할 수 있습니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "채팅방을 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 채팅방을 찾을 수 없습니다.\",\"code\":404}"))
            )
    })
    public ResponseEntity<ChatMessageResponse> sendMessage(
            Authentication authentication,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody SendChatMessageRequest request
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.sendMessage(userId, chatRoomId, request));
    }

    @GetMapping("/{chatRoomId}/messages")
    @Operation(
            summary = "채팅 메시지 목록 조회",
            description = "채팅방 참여자가 메시지 목록을 조회합니다. 조회 성공 시 마지막 메시지를 읽은 메시지로 기록합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "메시지 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChatMessageListResponse.class),
                            examples = @ExampleObject(value = "{\"chatRoomId\":5,\"page\":0,\"size\":30,\"hasNext\":false,\"messages\":[{\"messageId\":1,\"chatRoomId\":5,\"senderId\":12,\"content\":\"안녕하세요. 팀 채팅 테스트입니다.\",\"messageType\":\"TEXT\",\"status\":\"ACTIVE\",\"createdAt\":\"2026-09-08T11:00:00Z\"}]}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "채팅방 참여자가 아닌 사용자",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"채팅방 참여자만 메시지를 이용할 수 있습니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "채팅방을 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 채팅방을 찾을 수 없습니다.\",\"code\":404}"))
            )
    })
    public ResponseEntity<ChatMessageListResponse> getMessages(
            Authentication authentication,
            @PathVariable Long chatRoomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(chatService.getMessages(userId, chatRoomId, page, size));
    }
}
