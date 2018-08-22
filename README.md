# neo-challenge-tokyo

## intermediateA

* Blockchain data is parsed from `chain.acc` file
    * Memory mapped files is used to make import faster
    * Only transaction inputs/outputs is parsed, rest is skipped
* Optimized database storage is used. It's named `Chronicle-Map`, stores contents off-heap and implements `Map` interface.
* Unspent output data (sum of which is essentially a balance) is stored as key -> value: `(Address, Block Height) -> Amount`
* I iterate through all outputs and discard ones that has block height bigger than the one provided. Remaining outputs is aggregated by address and summed.

## intermediateB

* Blockchain data is parsed from `chain.acc` file
    * Memory mapped files is used to make import faster
    * Only transaction inputs/outputs is parsed, rest is skipped
* Optimized database storage is used. It's named `Chronicle-Map`, stores contents off-heap and implements `Map` interface.
* Transaction output data is stored as key -> value: `(Transaction ID, Output Index) -> Spent/Unspent`