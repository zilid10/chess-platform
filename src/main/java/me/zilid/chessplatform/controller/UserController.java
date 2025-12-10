package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.model.dto.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController("/api")
public class UserController {
    @GetMapping("/user/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void createUser(@RequestBody RegisterRequest request) {

    }



}
