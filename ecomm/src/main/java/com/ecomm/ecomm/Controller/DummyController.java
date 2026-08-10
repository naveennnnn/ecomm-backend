package com.ecomm.ecomm.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DummyController{

    @GetMapping("/")
    public String getString(){
        return "Welcome to Om Jwellery";
    }
}