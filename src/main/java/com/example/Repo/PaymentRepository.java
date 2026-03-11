package com.example.Repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.User.Payment;
import com.example.User.User;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByUserOrderByCreatedAtDesc(User user);
}
