import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.MappedBytes;
import net.openhft.chronicle.core.values.LongValue;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.values.Values;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import shaded.org.apache.commons.codec.DecoderException;
import shaded.org.apache.commons.codec.binary.Hex;

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

    public static void main(String[] args) throws IOException {
        long start = System.currentTimeMillis();

        MappedBytes chainData = MappedBytes.mappedBytes(new File("chain.acc"), 2147483648L, 65536);
        chainData.readLimit(chainData.realCapacity());

        LongValue amount = Values.newHeapInstance(LongValue.class);
        amount.setValue(0L);
        ChronicleMap<BalanceReference, LongValue> balances = ChronicleMap
                .of(BalanceReference.class, LongValue.class)
                .name("balances")
                .constantKeySizeBySample(new BalanceReference(new byte[20], 0))
                .constantValueSizeBySample(amount)
                .keyMarshaller(BalanceReferenceMarshaller.INSTANCE)
                .entries(350_000)
                .createPersistedTo(new File("balances.db"));

        Map<CoinReference, Output> outputs = new HashMap<>();

        long count = chainData.readUnsignedInt();
        int height;
        for (height = 0; height < count; height++) {
            chainData.readSkip(109);
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
                    if (Arrays.equals(asset, GOVERNING_TOKEN)) {
                        outputs.put(new CoinReference(transactionHash, j), new Output(address, height, value));
                    }
                }

                chainData.readPosition(inputsPosition);
                for (int j = 0; j < inputsCount; j++) {
                    byte[] hash = new byte[32];
                    chainData.read(hash);
                    short index = chainData.readShort();
                    CoinReference coinReference = new CoinReference(hash, index);
                    outputs.remove(coinReference);
                }

                chainData.readPosition(transactionEnd);

                long scriptsCount = readVarInt(chainData);
                for (long j = 0; j < scriptsCount; j++) {
                    skipVarBytes(chainData); // InvocationScript
                    skipVarBytes(chainData); // VerificationScript
                }
            }
        }

        System.out.println("height: " + height);

        for (Output output : outputs.values()) {
            amount.setValue(output.value);
            balances.put(new BalanceReference(output.address, output.block), amount);
        }

        System.out.println("balances: " + balances.size());

        balances.close();

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
