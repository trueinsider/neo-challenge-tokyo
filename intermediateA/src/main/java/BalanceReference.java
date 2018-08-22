import java.util.Arrays;
import java.util.Objects;

public class BalanceReference {
    final byte[] hash;
    final int block;

    public BalanceReference(byte[] hash, int block) {
        this.hash = hash;
        this.block = block;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BalanceReference that = (BalanceReference) o;
        return block == that.block &&
                Arrays.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(block);
        result = 31 * result + Arrays.hashCode(hash);
        return result;
    }
}
