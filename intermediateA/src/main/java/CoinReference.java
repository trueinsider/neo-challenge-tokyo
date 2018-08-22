import java.util.Arrays;
import java.util.Objects;

public class CoinReference {
    public final byte[] hash;
    public final short index;

    public CoinReference(byte[] hash, short index) {
        this.hash = hash;
        this.index = index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoinReference that = (CoinReference) o;
        return index == that.index &&
                Arrays.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(index);
        result = 31 * result + Arrays.hashCode(hash);
        return result;
    }
}
