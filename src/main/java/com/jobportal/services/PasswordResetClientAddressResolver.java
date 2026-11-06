package com.jobportal.services;

import java.net.InetAddress;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/** Uses only the servlet container's resolved peer address; never reads X-Forwarded-For directly. */
@Component
public class PasswordResetClientAddressResolver {
    public String resolve(HttpServletRequest request) {
        String remoteAddress = request == null ? null : request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) return "";
        String address = remoteAddress.trim();
        boolean ipv4 = address.matches("\\d{1,3}(?:\\.\\d{1,3}){3}");
        boolean ipv6 = address.indexOf(':') >= 0 && address.matches("[0-9a-fA-F:.]+");
        if (!ipv4 && !ipv6) return "";
        try {
            byte[] bytes = InetAddress.getByName(address).getAddress();
            // Normalize IPv4-mapped IPv6 to the same key as the IPv4 form.
            if (bytes.length == 16 && isIpv4Mapped(bytes)) {
                bytes = java.util.Arrays.copyOfRange(bytes, 12, 16);
            }
            return HexFormat.of().formatHex(bytes);
        } catch (java.net.UnknownHostException ex) {
            return "";
        }
    }

    private boolean isIpv4Mapped(byte[] address) {
        for (int i = 0; i < 10; i++) if (address[i] != 0) return false;
        return (address[10] & 0xff) == 0xff && (address[11] & 0xff) == 0xff;
    }
}
