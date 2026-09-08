package com.giut.server.dto.response;

import com.giut.server.entity.ProfileLink;

public record ProfileLinkResponse(
        Long id,
        ProfileLink.Type type,
        String typeName,
        String url,
        String title
) {
    public static ProfileLinkResponse from(ProfileLink link) {
        return new ProfileLinkResponse(
                link.getId(),
                link.getType(),
                link.getType().getDisplayName(),
                link.getUrl(),
                link.getTitle()
        );
    }
}
