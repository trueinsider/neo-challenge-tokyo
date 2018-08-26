import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import net.openhft.chronicle.core.values.BooleanValue;
import net.openhft.chronicle.map.ChronicleMap;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;

public class UTXO {
    public static void main(String[] args) throws IOException, DecoderException {
        long start = System.currentTimeMillis();

        ChronicleMap<CoinReference, BooleanValue> utxo = ChronicleMap
                .of(CoinReference.class, BooleanValue.class)
                .createPersistedTo(new File("utxo.db"));

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("utxo.txt"))) {
            List<String> lines = Files.readAllLines(Paths.get("utxo.csv"));
            for (String line : lines) {
                String[] split = line.split(",");
                String transactionId = split[0].trim().substring(2);
                short index = Short.valueOf(split[1].trim());

                byte[] transactionHash = Hex.decodeHex(transactionId.toCharArray());
                ArrayUtils.reverse(transactionHash);

                CoinReference coinReference = new CoinReference(transactionHash, index);

                writer.write(!utxo.containsKey(coinReference) ? "Spent\n" : "Unspent\n");
            }
        }

        long end = System.currentTimeMillis();

        System.out.println("duration: " + (end - start) + " ms");
    }
}
