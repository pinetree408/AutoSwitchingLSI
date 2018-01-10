package com.pinetree408.research.watchtapboard.util;

/**
 * Created by leesangyoon on 2017. 12. 22..
 */

public class Util {

    public static int[] predefineUniformRandom(int n, int size){
        double goalMean = n/2.0;

        int[] ret = new int[size];
        int currSize = 0;
        int retSum = 0;
        while (currSize < size - 1){
            int temp = StdRandom.uniform(n);
            if (!intContains(ret, temp)){
                ret[currSize++] = temp;
                retSum += temp;
            }
        }

        int last = (int) (goalMean * size) - retSum;

        if(last < 0 || last >= n){
            return predefineRandom(n, size);
        }
        else{
            if (intContains(ret, last))
                return predefineRandom(n, size);
            else{
                ret[size - 1] = last;
                return ret;
            }
        }
    }

    public static int[] predefineRandom(int n, int size){

        int[] ret = new int[size];
        int currSize = 0;
        while (currSize < size){
            int temp = StdRandom.uniform(n);
            if (!intContains(ret, temp)){
                ret[currSize++] = temp;
            }
        }

        return ret;
    }

    static boolean intContains(int[] A, int B) {
        for (int item : A) {
            if (item == B)
                return true;
        }
        return false;
    }
}
