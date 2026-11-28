package com.giut.server.profile.service;

import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.profile.dto.request.PortfolioShowcaseRequest;
import com.giut.server.profile.dto.request.UpsertPortfolioItemRequest;
import com.giut.server.profile.entity.PortfolioItem;
import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.profile.repository.PortfolioItemRepository;
import com.giut.server.profile.repository.PortfolioItemRoleRepository;
import com.giut.server.profile.repository.PortfolioItemSkillTagRepository;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import com.giut.server.user.service.UserWithdrawalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 같은 사용자의 포트폴리오를 바꾸는 두 요청이 겹칠 때, 먼저 시작한 요청이 커밋할 때까지 뒤 요청이 기다리는지 검증한다.
 * 먼저 시작한 요청은 커밋 직전에 멈춰 두고, 그동안 뒤 요청이 끝나지 않는 것으로 사용자 잠금 대기를 확인한다.
 * H2와 MySQL 하위 클래스가 같은 시나리오를 실행한다.
 */
abstract class PortfolioItemConcurrencyScenarios {

    private static final long BLOCKED_CHECK_MILLIS = 300;
    private static final long TIMEOUT_SECONDS = 5;

    @Autowired private PortfolioItemService service;
    @Autowired private PortfolioItemRepository items;
    @Autowired private PortfolioItemRoleRepository itemRoles;
    @Autowired private PortfolioItemSkillTagRepository itemSkillTags;
    @Autowired private ProfileRoleRepository roles;
    @Autowired private UserRepository users;
    @Autowired private UserWithdrawalService withdrawals;
    @Autowired private PlatformTransactionManager transactions;

    private User owner;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = users.saveAndFlush(User.createOAuthUser(
                "portfolio-lock@example.com", "포트폴리오", User.OAuthProvider.KAKAO, "portfolio-lock"));
        roles.saveAndFlush(role("BACKEND_DEVELOPER"));
    }

    @AfterEach
    void cleanUp() {
        itemRoles.deleteAllInBatch();
        itemSkillTags.deleteAllInBatch();
        items.deleteAllInBatch();
        roles.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void concurrentRepresentativeSelectionsLeaveOneRepresentative() throws Exception {
        PortfolioItem first = item(1);
        PortfolioItem second = item(2);

        runOverlapping(
                () -> service.setRepresentativePortfolio(owner.getId(), first.getId()),
                () -> service.setRepresentativePortfolio(owner.getId(), second.getId())
        );

        assertThat(items.findAll()).filteredOn(PortfolioItem::isRepresentative)
                .extracting(PortfolioItem::getId)
                .containsExactly(second.getId());
    }

    @Test
    void reorderDuringCreationKeepsShowcaseOrdersUnique() throws Exception {
        PortfolioItem first = item(1);
        PortfolioItem second = item(2);

        runOverlapping(
                () -> service.createPortfolioItem(owner.getId(), createRequest()),
                () -> service.changePortfolioShowcase(owner.getId(),
                        new PortfolioShowcaseRequest(List.of(second.getId(), first.getId())))
        );

        assertThat(showcaseOrders()).containsExactly(1, 2);
        assertThat(items.findById(second.getId()).orElseThrow().getShowcaseOrder()).isEqualTo(1);
        assertThat(items.findById(first.getId()).orElseThrow().getShowcaseOrder()).isEqualTo(2);
    }

    @Test
    void contentUpdateDoesNotRestoreAnItemHiddenByAConcurrentReorder() throws Exception {
        PortfolioItem hidden = item(1);
        PortfolioItem kept = item(2);

        runOverlapping(
                () -> service.changePortfolioShowcase(owner.getId(),
                        new PortfolioShowcaseRequest(List.of(kept.getId()))),
                () -> service.updatePortfolioItem(owner.getId(), hidden.getId(), createRequest())
        );

        assertThat(items.findById(hidden.getId()).orElseThrow().getShowcaseOrder()).isNull();
        assertThat(items.findById(kept.getId()).orElseThrow().getShowcaseOrder()).isEqualTo(1);
    }

    @Test
    void changeWaitingBehindWithdrawalIsRejectedAndKeepsThePortfolio() throws Exception {
        PortfolioItem item = item(1);

        Future<Object> update = runOverlappingAndReturnSecond(
                () -> {
                    withdrawals.withdraw(owner.getId());
                    return null;
                },
                () -> service.updatePortfolioItem(owner.getId(), item.getId(), createRequest())
        );

        assertThatThrownBy(() -> update.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ForbiddenException.class);
        PortfolioItem kept = items.findById(item.getId()).orElseThrow();
        assertThat(kept.getTitle()).isEqualTo("프로젝트 1");
        assertThat(kept.getShowcaseOrder()).isEqualTo(1);
        assertThat(users.findById(owner.getId()).orElseThrow().getStatus()).isEqualTo(User.Status.DELETED);
    }

    @Test
    void reorderAfterConcurrentDeletionIsRejectedAsBadRequest() throws Exception {
        PortfolioItem deleted = item(1);
        PortfolioItem second = item(2);
        PortfolioItem third = item(3);

        Future<Object> reorder = runOverlappingAndReturnSecond(
                () -> {
                    service.deletePortfolioItem(owner.getId(), deleted.getId());
                    return null;
                },
                () -> service.changePortfolioShowcase(owner.getId(), new PortfolioShowcaseRequest(
                        List.of(third.getId(), second.getId(), deleted.getId())))
        );

        assertThatThrownBy(() -> reorder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(items.findById(deleted.getId())).isEmpty();
        assertThat(showcaseOrders()).containsExactly(1, 2);
    }

    private void runOverlapping(Callable<?> firstAction, Callable<?> secondAction) throws Exception {
        runOverlappingAndReturnSecond(firstAction, secondAction).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * firstAction을 트랜잭션 안에서 실행하고 커밋 직전에 멈춘 채 secondAction을 시작한다.
     * secondAction이 잠금 대기 없이 먼저 끝나면 실패하고, 확인이 끝나면 firstAction을 커밋한다.
     */
    private <T> Future<T> runOverlappingAndReturnSecond(Callable<?> firstAction, Callable<T> secondAction)
            throws Exception {
        var firstReady = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                try {
                    firstAction.call();
                    firstReady.countDown();
                    if (!releaseFirst.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("먼저 시작한 요청이 풀려나지 않았습니다.");
                    }
                    return null;
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(firstReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<T> second = executor.submit(secondAction);
            assertThatThrownBy(() -> second.get(BLOCKED_CHECK_MILLIS, TimeUnit.MILLISECONDS))
                    .as("뒤 요청은 먼저 시작한 요청이 커밋할 때까지 사용자 잠금을 기다려야 한다")
                    .isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();
            first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            try {
                second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException ignored) {
                // 실패 결과는 호출한 시나리오가 직접 검증한다.
            }
            return second;
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private PortfolioItem item(int showcaseOrder) {
        PortfolioItem item = PortfolioItem.create(owner, "https://example.com/image.png", "프로젝트 " + showcaseOrder,
                "설명", null, null, null, "## 소개");
        item.changeShowcaseOrder(showcaseOrder);
        return items.saveAndFlush(item);
    }

    private List<Integer> showcaseOrders() {
        return items.findAll().stream()
                .map(PortfolioItem::getShowcaseOrder)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    private static UpsertPortfolioItemRequest createRequest() {
        return new UpsertPortfolioItemRequest("https://example.com/new.png", "새 프로젝트", "설명",
                null, null, 3, List.of("BACKEND_DEVELOPER"), "## 새 소개", List.of());
    }

    private static ProfileRole role(String code) {
        ProfileRole role = BeanUtils.instantiateClass(ProfileRole.class);
        ReflectionTestUtils.setField(role, "code", code);
        ReflectionTestUtils.setField(role, "name", code);
        ReflectionTestUtils.setField(role, "primaryRole", ProfileRole.PrimaryRole.DEVELOPMENT);
        ReflectionTestUtils.setField(role, "displayOrder", 1);
        return role;
    }
}
