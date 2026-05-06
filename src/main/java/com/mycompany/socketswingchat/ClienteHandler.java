package com.mycompany.socketswingchat;

import java.io.*;
import java.net.*;

public class ClienteHandler implements Runnable {

    private Socket socket;
    private PrintWriter salida;
    private String nombreUsuario;

    public ClienteHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))
        ) {
            salida = new PrintWriter(socket.getOutputStream(), true);

            nombreUsuario = entrada.readLine();
            
            // Validacion de usuario repetido
            if (ServidorChat.usuarios.contains(nombreUsuario)) {
            salida.println("ERROR_USUARIO_REPETIDO");
            socket.close();
            return;            
}
            ServidorChat.usuarios.add(nombreUsuario);
            salida.println("OK");

            ServidorChat.usuarios.add(nombreUsuario);

            System.out.println("[LOG] Usuario conectado: " + nombreUsuario);
            enviarATodos("Servidor: " + nombreUsuario + " se conectó.");
            enviarListaUsuarios();

            String mensaje;

            while ((mensaje = entrada.readLine()) != null) {
                System.out.println("[LOG] " + nombreUsuario + ": " + mensaje);

                if (mensaje.equalsIgnoreCase("SALIR")) {
                    break;
                }

                if (mensaje.startsWith("TYPING:")) {
                enviarATodos(mensaje);
                continue;
               }

                String hora = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());

                if (mensaje.startsWith("/privado ")) {
                enviarPrivado(mensaje, hora);
               } else {
                enviarATodos("MSG:" + nombreUsuario + "|" + hora + "|" + mensaje);
                  }
            }

        } catch (IOException e) {
            System.err.println("Cliente desconectado.");
        } finally {
            ServidorChat.usuarios.remove(nombreUsuario);
            ServidorChat.clientes.remove(this);

            enviarATodos("Servidor: " + nombreUsuario + " salió del chat.");
            enviarListaUsuarios();

            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error al cerrar socket.");
            }
        }
    }

    private void enviarATodos(String mensaje) {
        synchronized (ServidorChat.clientes) {
            for (ClienteHandler cliente : ServidorChat.clientes) {
                if (cliente.salida != null) {
                    cliente.salida.println(mensaje);
                }
            }
        }
    }

    /*private void enviarListaUsuarios() {
        String lista = "USUARIOS:" + String.join(",", ServidorChat.usuarios);
        enviarATodos(lista);
    }
    */
    private void enviarListaUsuarios() {
    String lista = "USUARIOS:" + String.join(",", ServidorChat.usuarios);
    System.out.println("[DEBUG] Enviando lista: " + lista); // 👈 agregar
    enviarATodos(lista);
}
    private void enviarPrivado(String mensaje, String hora) {
    String[] partes = mensaje.split(" ", 3);

    if (partes.length < 3) {
        salida.println("Servidor: Formato incorrecto. Usá: /privado usuario mensaje");
        return;
    }

    String destinatario = partes[1];
    String textoPrivado = partes[2];

    boolean encontrado = false;

    synchronized (ServidorChat.clientes) {
        for (ClienteHandler cliente : ServidorChat.clientes) {
            if (cliente.nombreUsuario != null && cliente.nombreUsuario.equalsIgnoreCase(destinatario)) {
                cliente.salida.println("MSG:" + nombreUsuario + "|" + hora + "|[Privado] " + textoPrivado);
                encontrado = true;
                break;
            }
        }
    }

    if (encontrado) {
        salida.println("MSG:" + nombreUsuario + "|" + hora + "|[✔✔ Privado para " + destinatario + "] " + textoPrivado);
    } else {
        salida.println("Servidor: Usuario no encontrado: " + destinatario);
    }
}
}