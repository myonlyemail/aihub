package com.aihub.auth.service;

import java.util.Map;

public interface OAuthService {

    Map<String, Object> getOAuthUserInfo(String provider, String code, String redirectUri);
}
