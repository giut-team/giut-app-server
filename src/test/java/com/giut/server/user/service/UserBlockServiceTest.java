package com.giut.server.user.service;

import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.user.entity.User;
import com.giut.server.user.entity.UserBlock;
import com.giut.server.user.repository.UserBlockRepository;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {
    @Mock private UserBlockRepository blocks;
    @Mock private UserRepository users;
    @InjectMocks private UserBlockService service;

    @Test
    void locksInAscendingOrderButStoresTheAuthenticatedDirection() {
        User actor = user(9L), target = user(3L);
        when(users.findByIdForUpdate(9L)).thenReturn(Optional.of(actor));
        when(users.findByIdForUpdate(3L)).thenReturn(Optional.of(target));

        assertThat(service.block(9L, 3L).blocked()).isTrue();

        var order = inOrder(users, blocks);
        order.verify(users).findByIdForUpdate(3L);
        order.verify(users).findByIdForUpdate(9L);
        order.verify(blocks).existsByBlocker_IdAndBlockedUser_Id(9L, 3L);
        var captured = ArgumentCaptor.forClass(UserBlock.class);
        verify(blocks).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getBlocker()).isSameAs(actor);
        assertThat(captured.getValue().getBlockedUser()).isSameAs(target);
    }

    @Test
    void duplicateRequestDoesNotCreateAnotherBlockAndSelfBlockDoesNotQueryDatabase() {
        when(users.findByIdForUpdate(3L)).thenReturn(Optional.of(user(3L)));
        when(users.findByIdForUpdate(9L)).thenReturn(Optional.of(user(9L)));
        when(blocks.existsByBlocker_IdAndBlockedUser_Id(9L, 3L)).thenReturn(true);

        service.block(9L, 3L);
        verify(blocks, never()).saveAndFlush(any());
        clearInvocations((Object) users, (Object) blocks);
        assertThatThrownBy(() -> service.block(9L, 9L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(users, blocks);
    }

    @Test
    void visibilityUsesEitherDirectionButAnonymousAndSelfAccessHaveNoBlockQuery() {
        when(blocks.existsBetween(9L, 3L)).thenReturn(true);
        when(blocks.existsBetween(3L, 9L)).thenReturn(true);
        assertThatThrownBy(() -> service.requireProfilesVisible(9L, 3L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.requireProfilesVisible(3L, 9L)).isInstanceOf(ResourceNotFoundException.class);
        clearInvocations((Object) blocks);
        service.requireProfilesVisible(null, 3L);
        service.requireProfilesVisible(3L, 3L);
        verifyNoInteractions(blocks, users);
    }

    @Test
    void statusExposesOnlyMyDirectionAndUnblockDoesNotRemoveReverseRelationship() {
        User actor = user(9L), inactiveTarget = user(3L);
        ReflectionTestUtils.setField(inactiveTarget, "status", User.Status.DELETED);
        when(users.findById(9L)).thenReturn(Optional.of(actor));
        when(users.findByIdForUpdate(3L)).thenReturn(Optional.of(inactiveTarget));
        when(users.findByIdForUpdate(9L)).thenReturn(Optional.of(actor));

        assertThat(service.getStatus(9L, 3L).blocked()).isFalse();
        assertThat(service.unblock(9L, 3L).blocked()).isFalse();
        verify(blocks).deleteByBlocker_IdAndBlockedUser_Id(9L, 3L);
        verify(blocks, never()).deleteByBlocker_IdAndBlockedUser_Id(3L, 9L);
        verify(blocks, never()).existsBetween(any(), any());
    }

    private User user(Long id) {
        User user = User.createOAuthUser("user" + id + "@example.com", "회원", User.OAuthProvider.KAKAO, "user-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
