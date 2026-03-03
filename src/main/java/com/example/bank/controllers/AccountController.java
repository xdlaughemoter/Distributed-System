package com.example.bank.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/account")
@EnableAsync
public class AccountController {
    private final Map<String, Double> accounts = new ConcurrentHashMap<>();
    // Get current account balance
    @GetMapping("/{name}/balance")
    public ResponseEntity<Double> getBalance(@PathVariable String name) {
        double balance = accounts.getOrDefault(name, 0.0);
        return ResponseEntity.ok(balance);
    }

    // Add money to the account
    @PostMapping("/{name}/add")
    public ResponseEntity<String> addMoney(@PathVariable String name, @RequestParam double amount) {
        if (amount <= 0) {
            return ResponseEntity.badRequest().body("Amount must be positive");
        }

        accounts.merge(name, amount, Double::sum);
        return ResponseEntity.ok("Money added successfully. Current balance: " + accounts.get(name));
    }

    // Remove money from the account
    @DeleteMapping("/{name}/remove")
    public ResponseEntity<String> removeMoney(@PathVariable String name, @RequestParam double amount) {
        double currentBalance = accounts.getOrDefault(name, 0.0);
        if (amount <= 0) {
            return ResponseEntity.badRequest().body("Amount must be positive");
        }
        if (amount > currentBalance) {
            return ResponseEntity.badRequest().body("Insufficient balance");
        }
        accounts.merge(name, -amount, Double::sum);
        return ResponseEntity.ok("Money removed successfully. Current balance: " + accounts.get(name));
    }

}
