package com.mycompany.socket;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClienteChat {

    private static final String HOST   = "localhost";
    private static final int    PUERTO = 5000;
    private static final String PROMPT = "Vos >> ";

    private static volatile boolean corriendo = true;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║         CLIENTE DE CHAT - SOCKETS                ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("Conectando a " + HOST + ":" + PUERTO + "...");

        try (
            Socket socket          = new Socket(HOST, PUERTO);
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter salida     = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()), true);
            Scanner teclado        = new Scanner(System.in)
        ) {
            System.out.println("✔ Conectado al servidor!\n");

           
            Thread receptor = new Thread(() -> {
                try {
                    String linea;
                    while (corriendo && (linea = entrada.readLine()) != null) {
                        if (linea.equals("__PROMPT__")) {
                            System.out.print(PROMPT);
                        } else {
                            System.out.println(linea);
                        }
                    }
                } catch (IOException e) {
                    if (corriendo) System.out.println("\n[!] Conexión perdida.");
                } finally {
                    corriendo = false;
                }
            });
            receptor.setDaemon(true);
            receptor.start();

            // ── Hilo principal: solo lee el teclado y envía ────────────────
            // El prompt lo maneja el hilo receptor con la señal __PROMPT__.
            while (corriendo) {
                if (!teclado.hasNextLine()) break;
                String input = teclado.nextLine().trim();

                if (!corriendo) break;
                if (input.isEmpty()) {
                    System.out.print(PROMPT); // reimprimimos si el usuario no escribió nada
                    continue;
                }

                salida.println(input);

                if (input.equalsIgnoreCase("SALIR")) {
                    Thread.sleep(400);
                    corriendo = false;
                    break;
                }
            }

            System.out.println("\n[Cliente] Sesión finalizada.");

        } catch (ConnectException e) {
            System.err.println("\n[!] No se pudo conectar. ¿Está corriendo el Servidor?");
        } catch (Exception e) {
            System.err.println("\n[!] Error: " + e.getMessage());
        }
    }
}