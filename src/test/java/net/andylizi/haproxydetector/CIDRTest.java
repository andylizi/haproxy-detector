package net.andylizi.haproxydetector;

import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CIDRTest {
    @Test
    void parse() throws UnknownHostException  {
        Function<CIDR, List<CIDR>> f = Collections::singletonList;
        assertEquals(f.apply(new CIDR("127.0.0.0", 8)), CIDR.parse("127.0.0.0/8"));
        assertEquals(f.apply(new CIDR("224.0.0.0", 4)), CIDR.parse("224.0.0.0/4"));
        assertEquals(f.apply(new CIDR("192.168.0.0", 24)), CIDR.parse("192.168.0.0/24"));
        assertEquals(f.apply(new CIDR("2001:db8::", 112)), CIDR.parse("2001:db8::/112"));
        assertEquals(f.apply(new CIDR("2001:db8::ffff", 128)), CIDR.parse("2001:db8::ffff"));
    }

    @Test
    void parseDomain() throws UnknownHostException {
        List<CIDR> list = CIDR.parse("www.cloudflare.com");
        assertTrue(list.size() > 1);
        assertTrue(list.stream().map(CIDR::getAddress).anyMatch(Inet4Address.class::isInstance));
        assertTrue(list.stream().map(CIDR::getAddress).anyMatch(Inet6Address.class::isInstance));
    }

    @Test
    void parseInvalid() {
        assertThrows(IllegalArgumentException.class, () -> CIDR.parse(""));
        assertThrows(IllegalArgumentException.class, () -> CIDR.parse("/"));
        assertThrows(IllegalArgumentException.class, () -> CIDR.parse("q/"));
        assertThrows(IllegalArgumentException.class, () -> CIDR.parse("/3"));
        assertThrows(IllegalArgumentException.class, () -> CIDR.parse("127.0.0.1/"));
        assertThrows(IllegalArgumentException.class, () -> CIDR.parse("127.0.0.1/3a"));
        assertThrows(IllegalArgumentException.class, () -> CIDR.parse("127.0.0.1/42"));
        assertThrows(IllegalArgumentException.class, () -> CIDR.parse("2001:db8::/666"));
        assertThrows(IllegalArgumentException.class, () -> CIDR.parse("example.com/8"));
        assertThrows(IllegalArgumentException.class, () -> CIDR.parse("12:34:56:78/8"));
        assertThrows(UnknownHostException.class, () -> CIDR.parse("%"));
    }

    @Test
    void contains() throws UnknownHostException {
        assertTrue(CIDR.parse("127.0.0.0/8").get(0).contains("127.0.0.0"));
        assertTrue(CIDR.parse("127.0.0.0/8").get(0).contains("127.1.2.3"));
        assertFalse(CIDR.parse("127.0.0.0/8").get(0).contains("192.168.0.1"));
        assertTrue(CIDR.parse("127.0.0.2").get(0).contains("127.0.0.2"));
        assertFalse(CIDR.parse("127.0.0.2").get(0).contains("127.0.0.1"));
        assertTrue(CIDR.parse("2001:db8::/112").get(0).contains("2001:db8::42"));
        assertFalse(CIDR.parse("2001:db8::/112").get(0).contains("8.8.8.8"));
    }
}
