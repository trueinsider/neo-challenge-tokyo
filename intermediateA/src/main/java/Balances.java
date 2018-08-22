import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.openhft.chronicle.core.values.LongValue;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.values.Values;

public class Balances {
    private static final MessageDigest digest;
    static {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(ALPHABET.length());

    private static byte[] base58Decode(char[] input) {
        BigInteger bi = BigInteger.ZERO;
        for (int i = input.length - 1; i >= 0; i--) {
            BigInteger index = BigInteger.valueOf((long) ALPHABET.indexOf(input[i]));
            bi = bi.add(index.multiply(BASE.pow(input.length - 1 - i)));
        }
        byte[] bytes = bi.toByteArray();
        //ArrayUtils.reverse(bytes);
        boolean stripSignByte = bytes.length > 1 && bytes[0] == 0 && bytes[1] < 0;
        int leadingZeros = 0;
        for (int i = 0; i < input.length && input[i] == ALPHABET.charAt(0); i++) {
            leadingZeros++;
        }
        byte[] tmp = new byte[bytes.length - (stripSignByte ? 1 : 0) + leadingZeros];
        System.arraycopy(bytes, stripSignByte ? 1 : 0, tmp, leadingZeros, tmp.length - leadingZeros);
        return tmp;
    }

    private static String base58Encode(byte[] input) {
        BigInteger value = new BigInteger(1, input);
        StringBuilder sb = new StringBuilder();
        while (value.compareTo(BASE) >= 0) {
            BigInteger mod = value.mod(BASE);
            sb.insert(0, ALPHABET.charAt(mod.intValue()));
            value = value.divide(BASE);
        }
        sb.insert(0, ALPHABET.charAt(value.intValue()));
        for (byte b : input) {
            if (b == 0)
                sb.insert(0, ALPHABET.charAt(0));
            else
                break;
        }
        return sb.toString();
    }

    private static byte[] base58CheckDecode(String input) {
        byte[] buffer = base58Decode(input.toCharArray());
        byte[] result = new byte[buffer.length - 4];
        System.arraycopy(buffer, 0, result, 0, result.length);
        return result;
    }

    private static String base58CheckEncode(byte[] data) {
        byte[] checksum = digest.digest(digest.digest(data));
        byte[] buffer = new byte[data.length + 4];
        System.arraycopy(data, 0, buffer, 0, data.length);
        System.arraycopy(checksum, 0, buffer, data.length, 4);
        return base58Encode(buffer);
    }

    private static String toAddress(byte[] scriptHash) {
        byte[] data = new byte[21];
        data[0] = 23;
        System.arraycopy(scriptHash, 0, data, 1, 20);
        return base58CheckEncode(data);
    }

    private static byte[] toScriptHash(String address) {
        byte[] data = base58CheckDecode(address);
        byte[] result = new byte[data.length - 1];
        System.arraycopy(data, 1, result, 0, result.length);
        return result;
    }

    public static void main(String[] args) throws IOException {
        long start = System.currentTimeMillis();

        int maxBlock = Integer.valueOf(args[0]);
        byte[] addressFilter = null;
        if (args.length > 1) {
            addressFilter = toScriptHash(args[1]);
        }

        LongValue amount = Values.newHeapInstance(LongValue.class);
        amount.setValue(0L);
        ChronicleMap<BalanceReference, LongValue> balances = ChronicleMap
                .of(BalanceReference.class, LongValue.class)
                .constantKeySizeBySample(new BalanceReference(new byte[20], 0))
                .constantValueSizeBySample(amount)
                .keyMarshaller(BalanceReferenceMarshaller.INSTANCE)
                .recoverPersistedTo(new File("balances.db"), true);

        Map<String, Long> aggregatedBalances = new HashMap<>();
        for (Entry<BalanceReference, LongValue> e : balances.entrySet()) {
            int block = e.getKey().block;
            if (block > maxBlock) {
                continue;
            }

            byte[] hash = e.getKey().hash;
            if (addressFilter != null && !Arrays.equals(addressFilter, hash)) {
                continue;
            }

            String address = toAddress(hash);

            long value = e.getValue().getValue();

            if (aggregatedBalances.computeIfPresent(address, (ignored, oldValue) -> oldValue + value) == null) {
                aggregatedBalances.putIfAbsent(address, value);
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("balances.csv"))) {
            for (Entry<String, Long> e : aggregatedBalances.entrySet()) {
                String address = e.getKey();
                long value = e.getValue();
                BigDecimal formattedValue = BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100_000_000), RoundingMode.UNNECESSARY);
                writer.write(address + "," + formattedValue + "\n");
            }
        }

        long end = System.currentTimeMillis();

        System.out.println("duration: " + (end - start) + " ms");
    }
}
