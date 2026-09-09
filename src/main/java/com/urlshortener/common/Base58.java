package com.urlshortener.common;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Base58（比特币字母表）：去除易混淆字符 0、O、I、l，短码对人友好。
 */
public final class Base58 {

    public static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private static final BigInteger BASE = BigInteger.valueOf(58);
    private static final Pattern CODE_PATTERN = Pattern.compile("[1-9A-HJ-NP-Za-km-z]{4,16}");

    private Base58() {
    }

    public static String encode(byte[] input) {
        BigInteger number = new BigInteger(1, input);
        StringBuilder sb = new StringBuilder();
        while (number.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] div = number.divideAndRemainder(BASE);
            sb.append(ALPHABET.charAt(div[1].intValue()));
            number = div[0];
        }
        for (byte b : input) {
            if (b == 0) {
                sb.append(ALPHABET.charAt(0));
            } else {
                break;
            }
        }
        return sb.reverse().toString();
    }

    public static boolean isValidCode(String code) {
        return code != null && CODE_PATTERN.matcher(code).matches();
    }
}
