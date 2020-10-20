# Cachemiss-simulations

This is an implementation of Modified Parser of Java that takes code for matrix multiplication as input and automates the cache access to approximate number of cache misses. It is faster than simulating a cache in Java, as calculations involved are purely mathematical in nature with constant amount of space complexity and polynomial time complexity.
An example of input code is :
```Java
void testcase() {
    int cachePower = 8;
    int blockPower = 5;
    int setSize = 1;
    byte[][][] A = new byte [2][2][2];

    String cacheType = "DirectMapped";

    for (int k = 0; k < 128; k+=1){
        for(int i = 0; i<2; i+=1) {
            for(int j = 0; j<2; j+=1) {
                for(int a = 0; a<2; a+=1) {
                    A[i][j][a] = 0;
                }
            }
        }
    }
}
```
where cachePower = 8 implies cachesize of 2^8 B, BlockPower = 5 implies blocksize of 2^5 B and cachetype that can be DirectMapped, Fully Associative or Set Asscociative with associativity = setSize variable. 
# How to Run
Insert your input code of form described in example in UnitTestCases.Java. Run **bash run.sh UnitTestCases.Java**, the output will be stored in Results.obj. 
