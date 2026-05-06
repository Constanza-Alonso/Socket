package com.mycompany.socketswingchat;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServidorChat {

    private static final int PUERTO = 5000;

    public static final Set<ClienteHandler> clientes =
            Collections.synchronizedSet(new HashSet<>());

    public static final Set<String> usuarios =
            Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        System.out.println("Servidor iniciado en puerto " + PUERTO);

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClienteHandler cliente = new ClienteHandler(socket);
                clientes.add(cliente);
                new Thread(cliente).start();
            }
        } catch (IOException e) {
            System.err.println("Error en servidor: " + e.getMessage());
        }
    }
}