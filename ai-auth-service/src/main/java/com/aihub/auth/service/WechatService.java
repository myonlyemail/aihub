package com.aihub.auth.service;

import java.util.Map;

public interface WechatService {

    Map<String, String> getWechatSession(String code);
}
