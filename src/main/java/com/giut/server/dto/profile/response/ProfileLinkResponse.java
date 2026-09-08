package com.giut.server.dto.profile.response;

import com.giut.server.entity.ProfileLink;

public record ProfileLinkResponse(
        ProfileLink.Type type,
        String typeName,
        String url,
        String title
) {
    public static ProfileLinkResponse from(ProfileLink link) {
        return new ProfileLinkResponse(
                link.type(),
                link.type().getDisplayName(),
                link.url(),
                link.title()
        );
    }
}
