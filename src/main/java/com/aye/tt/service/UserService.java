package com.aye.tt.service;

import java.util.List;

import com.aye.tt.model.User;


public interface UserService {

    User create(User object);

    User find(String id);

    User findByUsername(String userName);

    List<User> findAll();

    User update(String id, User object);

    String delete(String id);
}
