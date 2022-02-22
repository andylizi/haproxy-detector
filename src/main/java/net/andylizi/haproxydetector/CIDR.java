package net.andylizi.haproxydetector;

import org.apache.commons.validator.routines.InetAddressValidator;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CIDR {
    public static List<CIDR> parse(@NotNull String cidr) throws IllegalArgumentException, UnknownHostException {
        int idx = cidr.lastIndexOf('/');
        if (idx != -1) {
            String addrPart = cidr.substring(0, idx);
            if (addrPart.isEmpty()) {
                throw new IllegalArgumentException("invalid CIDR string \"" + cidr + "\"");
            }

            if (!InetAddressValidator.getInstance().isValid(addrPart)) {
                throw new IllegalArgumentException("CIDR must be consist of an valid IP address: " + addrPart);
            }

            try {
                InetAddress addr = InetAddress.getByName(addrPart);
                int prefix = Integer.parseInt(cidr.substring(idx + 1));
                return Collections.singletonList(new CIDR(addr, prefix));
            } catch (UnknownHostException | IllegalArgumentException | IndexOutOfBoundsException e) {
                throw new IllegalArgumentException("invalid CIDR string \"" + cidr + "\"", e);
            }
        } else {
            if (cidr.isEmpty()) throw new IllegalArgumentException("empty CIDR string");
            InetAddress[] addresses = InetAddress.getAllByName(cidr);
            if (addresses.length == 1) {
                return Collections.singletonList(new CIDR(addresses[0]));
            }
            return Stream.of(addresses).map(CIDR::new).collect(Collectors.toList());
        }
    }

    private final InetAddress addr;
    private final int prefix;

    private final BigInteger mask;
    private final BigInteger network;

    public CIDR(@NotNull InetAddress addr, int prefix) throws IllegalArgumentException {
        this.addr = Objects.requireNonNull(addr);
        this.prefix = prefix;

        int bytesLen = addr.getAddress().length;
        if (prefix < 0) throw new IllegalArgumentException("prefix must not be negative");
        if (prefix > bytesLen * Byte.SIZE) throw new IllegalArgumentException("invalid prefix length");

        byte[] buf = new byte[bytesLen];
        Arrays.fill(buf, (byte) 0xFF);
        this.mask = new BigInteger(1, buf).shiftRight(prefix).not();
        this.network = new BigInteger(1, addr.getAddress()).and(mask);
    }

    private CIDR(@NotNull InetAddress addr) {
        this(addr, addr.getAddress().length * Byte.SIZE);
    }

    CIDR(@NotNull String addr, int prefix) throws IllegalArgumentException, UnknownHostException {
        this(InetAddress.getByName(addr), prefix);
    }

    public InetAddress getAddress() {
        return addr;
    }

    public int getPrefix() {
        return prefix;
    }

    public boolean contains(String other) {
        try {
            return contains(InetAddress.getByName(other));
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public boolean contains(InetAddress other) {
        byte[] bytes = other.getAddress();
        if (bytes.length != addr.getAddress().length) return false;
        BigInteger target = new BigInteger(1, bytes);
        return network.equals(target.and(mask));
    }

    @Override
    public String toString() {
        return addr.getHostAddress() + "/" + prefix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CIDR cidr = (CIDR) o;
        return prefix == cidr.prefix && addr.equals(cidr.addr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addr, prefix);
    }
}
