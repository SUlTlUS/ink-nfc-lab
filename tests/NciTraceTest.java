package dev.inknfclab;

public final class NciTraceTest {
    public static void main(String[] args) {
        NciTrace parser = new NciTrace();
        require(parser.accept("D/StNfcHal: (#01DA4) LL Rx 6f 02 08 31 20 00 0a ") == null);
        String packet = parser.accept("D/StNfcHal: (#01DA4) LL rx 01 02 03 04 ");
        require(packet.contains("RAW=6F02083120000A01020304"));
        require(packet.contains("未解码"));
        require(parser.accept("D/StNfcHal: (#01DA5) UL Rx 61 03 00") == null);
        require(parser.accept("D/StNfcHal: (#01DA6) LL Rx 61 03 00").contains("发现目标"));
        require(parser.accept("D/StNfcHal: (#01DA7) LL Tx 21 03 00").contains("主机→NFCC"));
        require(parser.accept("D/StNfcHal: (#01DA8) LL Rx 61 05 00").contains("接口激活"));
        require(parser.accept("D/StNfcHal: (#01DA9) LL Rx 61 07 01 02").contains("射频场状态"));
        require(parser.accept("D/StNfcHal: (#01DA9) LL Rx 60 08 01 02").contains("接口错误"));
        require(parser.accept("D/StNfcHal: (#01DAA) LL Rx 00 00 02 90 00").contains("NCI DATA"));
        require(parser.accept("D/StNfcHal: (#01DAB) LL Rx 61 03 00 01").contains("长度异常"));
        System.out.println("NCI trace tests passed");
    }
    private static void require(boolean condition) { if (!condition) throw new AssertionError(); }
}
