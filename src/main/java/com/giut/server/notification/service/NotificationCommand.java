package com.giut.server.notification.service;

import com.giut.server.notification.entity.Notification;

import java.util.Map;

/**
 * 다른 도메인이 알림을 만들 때 넘기는 값. notification 패키지는 공모전·팀 같은 원인 도메인을 모른다.
 *
 * @param actionPath 앱이 딥링크로 바꿔 이동할 경로(예: /competitions/12). 없으면 null.
 * @param payload    앱에 함께 넘길 작은 식별자 묶음(푸시 data와 알림함 응답에 들어간다). 푸시 전체 4KB 제한 안에서 작게 유지한다.
 * @param dedupKey   같은 사건으로 같은 회원에게 알림이 두 번 생기지 않게 하는 전역 고유 키.
 */
public record NotificationCommand(Long userId, Notification.Type type, String title, String body, String actionPath,
                                  Map<String, Object> payload, String dedupKey) {
}
