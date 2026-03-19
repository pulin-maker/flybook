package com.bytedance;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TestSomething {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String pwd = encoder.encode("admin123");
        System.out.println(pwd);
    }
}
