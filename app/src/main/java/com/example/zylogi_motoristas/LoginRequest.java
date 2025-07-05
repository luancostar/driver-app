package com.example.zylogi_motoristas;// Local: app/src/main/java/com/example/zylogi_motoristas/LoginRequest.java

public class LoginRequest {
    final String cpf;
    final String password; // O backend espera o campo "password"

    public LoginRequest(String cpf, String password) {
        this.cpf = cpf;
        this.password = password;
    }
}