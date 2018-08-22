class Output {
    final byte[] address;
    final int block;
    final long value;

    Output(byte[] address, int block, long value) {
        this.address = address;
        this.block = block;
        this.value = value;
    }
}
