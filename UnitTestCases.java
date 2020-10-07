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

// A -> 1


void testcase() {
    int cachePower = 8;
    int blockPower = 2;
    int setSize = 1;
    byte[][][] A = new byte [2][2][2];

    String cacheType = "DirectMapped";

    for (int k = 0; k < 128; k+=1){
        for(int i = 0; i<2; i+=1) {
            for(int j = 0; j<2; j+=1) {
                for(int a = 0; a<2; a+=1) {
                    A[a][j][i] = 0;
                }
            }
        }
    }
}

// A -> 2