package com.giut.server.competition.util;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

@Component
public class PublicCompetitionSource {

    public void requirePublicAddress(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal") || !host.contains(".") && !host.contains(":")) {
            throw invalidSource();
        }

        // The extractor must independently enforce its egress policy for each destination it actually contacts.
        try {
            if (Arrays.stream(InetAddress.getAllByName(host)).anyMatch(address -> !isPublicAddress(address))) {
                throw invalidSource();
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("공고 URL의 호스트를 확인할 수 없습니다.");
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && (third == 0 || third == 2))
                    && !(first == 198 && (second == 18 || second == 19 || second == 51 && third == 100))
                    && !(first == 203 && second == 0 && third == 113);
        }
        return address instanceof Inet6Address && (bytes[0] & 0xe0) == 0x20
                && !(bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == (byte) 0xb8);
    }

    private IllegalArgumentException invalidSource() {
        return new IllegalArgumentException("공개 인터넷 공고 URL만 추출할 수 있습니다.");
    }
}
