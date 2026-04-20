package com.example.bank.service;

import org.springframework.stereotype.Service;

@Service
public class HashingService {
    public Integer hashingFunction(String input){
    int hash = 0;
    // P is a prime number (31 is common for ASCII strings)
    int P = 31;
    // M is a large prime to keep the hash within a specific range
    int M = 1_000_000_009;

    for (int i = 0; i < input.length(); i++) {
        // Get the Unicode value of the character
        char c = input.charAt(i);

        // Formula: (hash * P + char_code) % M
        hash = (hash * P + c) % M;
    }

    return hash;
}
}
