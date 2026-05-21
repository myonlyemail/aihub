package com.aihub.auth.service;

public interface SmsService {

    void sendCode(String phone);

    boolean verifyCode(String phone, String code);
}
