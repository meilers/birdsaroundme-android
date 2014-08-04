package com.sobremesa.birdwatching.models;

/**
 * Created by omegatai on 2014-07-09.
 */
public enum SortByType {
    DISTANCE(0), DATE(1), NAME(2) ;
    private final int value;

    private SortByType(int value) {
        this.value = value;
    }
}