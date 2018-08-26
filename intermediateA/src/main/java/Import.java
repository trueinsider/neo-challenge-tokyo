import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.MappedBytes;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;

public class Import {
    private static final byte[] GOVERNING_TOKEN;
    static {
        try {
            GOVERNING_TOKEN = Hex.decodeHex(
                    "c56f33fc6ecfcd0c225c4ab356fee59390af8560be0e930faebe74a6daff7c9b".toCharArray()
            );
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
        ArrayUtils.reverse(GOVERNING_TOKEN);
    }

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

        int maxHeight = Integer.valueOf(args[0]);
        byte[] addressFilter = null;
        if (args.length > 1) {
            addressFilter = toScriptHash(args[1]);
        }

        MappedBytes chainData = MappedBytes.mappedBytes(new File("chain.acc"), 2147483648L, 65536);
        chainData.readLimit(chainData.realCapacity());

        Map<CoinReference, Output> outputs = new HashMap<>();

        long count = chainData.readUnsignedInt();
        int height;
        for (height = 0; height < count; height++) {
            chainData.readSkip(76);
            height = chainData.readInt();
            chainData.readSkip(29);
            skipVarBytes(chainData); // InvocationScript
            skipVarBytes(chainData); // VerificationScript
            long transactionsCount = readVarInt(chainData);
            for (int i = 0; i < transactionsCount; i++) {
                long transactionStart = chainData.readPosition();

                int transactionType = chainData.readUnsignedByte();

                byte version = chainData.readByte();

                switch (transactionType) {
                    case 0x00: // MinerTransaction
                        chainData.readSkip(4);
                        break;
                    case 0x01: // IssueTransaction
                        break;
                    case 0x02: // ClaimTransaction
                        long claims = readVarInt(chainData);
                        chainData.readSkip(claims * 34);
                        break;
                    case 0x20: // EnrollmentTransaction
                        skipECPoint(chainData);
                        break;
                    case 0x40: // RegisterTransaction
                        chainData.readSkip(1);
                        skipVarBytes(chainData);
                        chainData.readSkip(9);
                        skipECPoint(chainData);
                        chainData.readSkip(20);
                        break;
                    case 0x80: // ContractTransaction
                        break;
                    case 0x90: // StateTransaction
                        long descriptors = readVarInt(chainData);
                        for (long j = 0; j < descriptors; j++) {
                            chainData.readByte();
                            skipVarBytes(chainData);
                            skipVarBytes(chainData);
                            skipVarBytes(chainData);
                        }
                        break;
                    case 0xd0: // PublishTransaction
                        skipVarBytes(chainData);
                        skipVarBytes(chainData);
                        chainData.readSkip(1);
                        if (version >= 1) {
                            chainData.readSkip(1);
                        }
                        skipVarBytes(chainData);
                        skipVarBytes(chainData);
                        skipVarBytes(chainData);
                        skipVarBytes(chainData);
                        skipVarBytes(chainData);
                        break;
                    case 0xd1: // InvocationTransaction
                        skipVarBytes(chainData);
                        if (version >= 1) {
                            chainData.readSkip(8);
                        }
                        break;
                }

                long attributes = readVarInt(chainData);
                for (int j = 0; j < attributes; j++) {
                    int usage = chainData.readUnsignedByte();
                    if (usage == 0x00 || usage == 0x30 || (usage >= 0xa1 && usage <= 0xaf))
                        chainData.readSkip(32);
                    else if (usage == 0x02 || usage == 0x03)
                        chainData.readSkip(32);
                    else if (usage == 0x20)
                        chainData.readSkip(20);
                    else if (usage == 0x81)
                        skipVarBytes(chainData);
                    else if (usage == 0x90 || usage >= 0xf0)
                        skipVarBytes(chainData);
                }

                int inputsCount = (int) readVarInt(chainData);
                long inputsPosition = chainData.readPosition();

                chainData.readSkip(inputsCount * 34);

                long outputsCount = readVarInt(chainData);
                long outputsPosition = chainData.readPosition();

                chainData.readSkip(outputsCount * 60);

                long transactionEnd = chainData.readPosition();

                chainData.readPosition(transactionStart);

                int transactionSize = (int) (transactionEnd - transactionStart);
                byte[] transactionData = new byte[transactionSize];
                chainData.read(transactionData);

                byte[] transactionHash = digest.digest(digest.digest(transactionData));

                transactionData = null;

                chainData.readPosition(outputsPosition);
                for (short j = 0; j < outputsCount; j++) {
                    byte[] asset = new byte[32];
                    chainData.read(asset);
                    long value = chainData.readLong();
                    byte[] address = new byte[20];
                    chainData.read(address);
                    if (height <= maxHeight && Arrays.equals(asset, GOVERNING_TOKEN)) {
                        outputs.put(new CoinReference(transactionHash, j), new Output(address, value));
                    }
                }

                chainData.readPosition(inputsPosition);
                for (int j = 0; j < inputsCount; j++) {
                    byte[] hash = new byte[32];
                    chainData.read(hash);
                    short index = chainData.readShort();
                    if (height <= maxHeight) {
                        CoinReference coinReference = new CoinReference(hash, index);
                        outputs.remove(coinReference);
                    }
                }

                chainData.readPosition(transactionEnd);

                long scriptsCount = readVarInt(chainData);
                for (long j = 0; j < scriptsCount; j++) {
                    skipVarBytes(chainData); // InvocationScript
                    skipVarBytes(chainData); // VerificationScript
                }
            }
        }

        Map<String, Long> aggregatedBalances = new HashMap<>();

        for (Output output : outputs.values()) {
            byte[] hash = output.address;
            if (addressFilter != null && !Arrays.equals(addressFilter, hash)) {
                continue;
            }

            String address = toAddress(hash);

            long value = output.value;

            if (aggregatedBalances.putIfAbsent(address, value) != null) {
                long oldValue = aggregatedBalances.get(address);
                long newValue = oldValue + value;
                if (newValue == 0) {
                    aggregatedBalances.remove(address);
                } else {
                    aggregatedBalances.put(address, newValue);
                }
            }
        }

        System.out.println("height: " + height);

        System.out.println("balances: " + outputs.size());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("balances.csv"))) {
            for (Map.Entry<String, Long> e : aggregatedBalances.entrySet()) {
                String address = e.getKey();
                long value = e.getValue();
                BigDecimal formattedValue = BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100_000_000), RoundingMode.UNNECESSARY);
                writer.write(address + "," + formattedValue + "\n");
            }
        }

        long end = System.currentTimeMillis();

        System.out.println("duration: " + (end - start) + " ms");
    }

    private static void skipECPoint(Bytes bytes) {
        byte type = bytes.readByte();
        switch (type) {
            case 0x02:
            case 0x03:
                bytes.readSkip(32);
                break;
            case 0x04:
            case 0x06:
            case 0x07:
                bytes.readSkip(64);
        }
    }

    private static void skipVarBytes(Bytes bytes) {
        bytes.readSkip(readVarInt(bytes));
    }

    private static long readVarInt(Bytes bytes) {
        int fb = bytes.readUnsignedByte();
        long value;
        if (fb == 0xFD)
            value = bytes.readUnsignedShort();
        else if (fb == 0xFE)
            value = bytes.readUnsignedInt();
        else if (fb == 0xFF)
            value = bytes.readLong();
        else
            value = fb;
        return value;
    }
}
