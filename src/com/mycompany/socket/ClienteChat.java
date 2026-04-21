package com.mycompany.socket;
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClienteChat {

    private static final String HOST      = "localhost";
    private static final int    PUERTO    = 5000;
    private static final String PROMPT    = "Vos >> ";

    private static volatile boolean corriendo = true;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║         CLIENTE DE CHAT - SOCKETS                ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("Conectando a " + HOST + ":" + PUERTO + "...");

        try (
            Socket socket           = new Socket(HOST, PUERTO);
            BufferedReader entrada  = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter salida      = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()), true);
            Scanner teclado         = new Scanner(System.in)
        ) {
            System.out.println("✔ Conectado al servidor!\n");

            // ── Hilo receptor: SOLO imprime lo que llega ──
            Thread receptor = new Thread(() -> {
                try {
                    String linea;
                    while (corriendo && (linea = entrada.readLine()) != null) {
                        if (linea.equals("__PROMPT__")) continue;
                        
                        // Imprimimos la línea tal cual llega del servidor
                        System.out.println(linea);
                    }
                } catch (IOException e) {
                    if (corriendo) System.out.println("\n[!] Conexión perdida.");
                } finally {
                    corriendo = false;
                }
            });
            receptor.setDaemon(true);
            receptor.start();

            // ── Hilo principal: Maneja el prompt de forma controlada ──
            while (corriendo) {
                // Solo imprimimos el prompt cuando realmente estamos esperando
                System.out.print(PROMPT);

                if (!teclado.hasNextLine()) break;
                String input = teclado.nextLine().trim();

                if (!corriendo) break;
                if (input.isEmpty()) continue;

                salida.println(input);

                if (input.equalsIgnoreCase("SALIR")) {
                    Thread.sleep(500);
                    corriendo = false;
                    break;
                }
            }
            System.out.println("\n[Cliente] Sesión finalizada.");

        } catch (Exception e) {
            System.err.println("\n[!] Error: " + e.getMessage());
        }
    }
}