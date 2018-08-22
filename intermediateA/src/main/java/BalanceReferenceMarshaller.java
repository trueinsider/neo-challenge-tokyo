import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.util.ReadResolvable;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BalanceReferenceMarshaller implements BytesWriter<BalanceReference>, BytesReader<BalanceReference>,
        ReadResolvable<BalanceReferenceMarshaller> {
    static final BalanceReferenceMarshaller INSTANCE = new BalanceReferenceMarshaller();

    @NotNull
    @Override
    public BalanceReference read(Bytes in, @Nullable BalanceReference using) {
        byte[] hash = new byte[20];
        in.read(hash);
        int block = in.readInt();
        return new BalanceReference(hash, block);
    }

    @Override
    public void write(Bytes out, @NotNull BalanceReference toWrite) {
        out.write(toWrite.hash);
        out.writeInt(toWrite.block);

    }

    @NotNull
    @Override
    public BalanceReferenceMarshaller readResolve() {
        return INSTANCE;
    }
}
