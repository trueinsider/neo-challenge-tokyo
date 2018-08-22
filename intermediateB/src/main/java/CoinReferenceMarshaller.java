import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.util.ReadResolvable;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CoinReferenceMarshaller implements BytesWriter<CoinReference>, BytesReader<CoinReference>,
        ReadResolvable<CoinReferenceMarshaller> {
    static final CoinReferenceMarshaller INSTANCE = new CoinReferenceMarshaller();

    @NotNull
    @Override
    public CoinReference read(Bytes in, @Nullable CoinReference using) {
        byte[] hash = new byte[32];
        in.read(hash);
        short index = in.readShort();
        return new CoinReference(hash, index);
    }

    @Override
    public void write(Bytes out, @NotNull CoinReference toWrite) {
        out.write(toWrite.hash);
        out.writeShort(toWrite.index);

    }

    @NotNull
    @Override
    public CoinReferenceMarshaller readResolve() {
        return INSTANCE;
    }
}
