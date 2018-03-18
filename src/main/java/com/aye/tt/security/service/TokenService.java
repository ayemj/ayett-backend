package com.aye.tt.security.service;


public interface TokenService {

    String getToken(String username, String password);
}
