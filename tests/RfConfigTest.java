package dev.inknfclab;

import java.util.Arrays;

public final class RfConfigTest {
    public static void main(String[] args) {
        byte[] original = fromHex("440032040028992A0028992A0028992A0028992A0020EE220020EE220020EE220020EE220028522B0028522B0028522B0028522B0020EE220020EE220020EE220020EE220028522B0028522B0028522B0028522B0020EE220020EE220020EE220020EE22B72AD52AB72AD52AB72AD532B72AD532072320230723202307232023072320236B2B842B6B2B842B6B2B842B6B2B842B072320230723202307232023072320236B2B842B6B2B842B6B2B842B6B2B842B07232023072320230723202307232023");
        RfConfig.validate(original);
        require(RfConfig.values(original).equals("5/5/5/5"));
        byte[] adjusted = RfConfig.adjust(original, 2);
        RfConfig.validate(adjusted);
        require(RfConfig.values(adjusted).equals("7/7/7/7"));
        require(RfConfig.values(original).equals("5/5/5/5"));
        require(Arrays.equals(original, RfConfig.adjust(original, 0)));
        require(RfConfig.values(RfConfig.adjust(original, -1)).equals("4/4/4/4"));
        for (int offset = 0; offset < original.length; offset++) {
            if ((offset >= 2 && offset < 8) || (offset >= 100 && offset < 104)) continue;
            require(original[offset] == adjusted[offset]);
        }
        for (int count = 0; count < 30; count++) adjusted = RfConfig.adjust(adjusted, 2);
        require(RfConfig.values(adjusted).equals("21/21/21/21"));
        for (int count = 0; count < 30; count++) adjusted = RfConfig.adjust(adjusted, -1);
        require(RfConfig.values(adjusted).equals("2/2/2/2"));
        byte[] response = new byte[199];
        response[0] = 1;
        response[1] = 2;
        response[2] = (byte) 196;
        System.arraycopy(original, 0, response, 3, original.length);
        require(Arrays.equals(original, RfConfig.parse(2, response)));
        reject(() -> RfConfig.parse(3, response));
        reject(() -> RfConfig.parse(1, response));
        reject(() -> RfConfig.parse(2, new byte[198]));
        reject(() -> RfConfig.adjust(original, 21));
        reject(() -> RfConfig.adjust(original, -2));
        original[20] ^= 1;
        reject(() -> RfConfig.adjust(original, 1));
        System.out.println("RfConfigTest passed");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    private static byte[] fromHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < bytes.length; index++)
            bytes[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        return bytes;
    }

    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected RF config rejection");
    }
}
