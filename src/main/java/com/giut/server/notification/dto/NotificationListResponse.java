package com.giut.server.notification.dto;

import java.util.List;

/** 최신순 커서 페이지. 다음 페이지는 {@code cursor=nextCursor}로 요청한다. 마지막 페이지면 nextCursor가 null이다. */
public record NotificationListResponse(List<NotificationResponse> notifications, Long nextCursor, boolean hasNext) {
}
