package dev.inknfclab;

public final class DeviceSupportTest {
    public static void main(String[] args) {
        for (int sdk = 26; sdk <= 40; sdk++) {
            for (String device : new String[]{"pudding", "dada", "haotian", "husky", "unknown", "", null}) {
                boolean expected = "pudding".equals(device) && sdk == 37;
                require(DeviceSupport.verifiedRootProfile(device, sdk) == expected);
                require(DeviceSupport.useRoot(device, sdk, true, false) == expected);
                require(!DeviceSupport.useRoot(device, sdk, false, false));
                require(!DeviceSupport.useRoot(device, sdk, true, true));
            }
        }
        require(DeviceSupport.summary("dada", 35, true).contains("尚待"));
        require(DeviceSupport.summary("pudding", 37, false).contains("无 NFC"));
        require(DeviceSupport.summary("unknown", 36, true).contains("不请求 Root"));
        System.out.println("Device support safety policy tests passed");
    }
    private static void require(boolean condition) { if (!condition) throw new AssertionError(); }
}
