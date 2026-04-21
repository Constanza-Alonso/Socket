package com.mycompany.socket;
import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * ClienteChat - Cliente para el servidor de chat multihilo.
 *
 * Usa DOS hilos:
 *   1) Hilo principal → lee del teclado y envía al servidor.
 *   2) Hilo receptor  → escucha mensajes del servidor en tiempo real
 *                       (mensajes de otros clientes llegan sin bloquear el input).
 *
 * Uso: ejecutar DESPUÉS de ServidorMultihilo.
 * Puerto: 5000 | Host: localhost
 */
public class ClienteChat {

    private static final String HOST      = "localhost";
    private static final int    PUERTO    = 5000;
    private static final String PROMPT    = "Vos >> ";

    // Flag compartido para detener ambos hilos
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

            // ── Hilo receptor: imprime mensajes del servidor en tiempo real ──
            Thread receptor = new Thread(() -> {
                try {
                    String linea;
                    while (corriendo && (linea = entrada.readLine()) != null) {
                        // El servidor usa __PROMPT__ para indicar que espera input
                        if (linea.equals("__PROMPT__")) {
                            System.out.print("");  // No hacer nada, el Scanner ya espera
                            continue;
                        }
                        // Imprimir mensaje recibido sin romper el prompt del usuario
                        System.out.println("\r" + linea);
                        System.out.print(PROMPT);
                    }
                } catch (IOException e) {
                    if (corriendo) {
                        System.out.println("\n[!] Conexión con el servidor perdida.");
                    }
                } finally {
                    corriendo = false;
                }
            });
            receptor.setDaemon(true);
            receptor.start();

            // ── Hilo principal: envía input del teclado al servidor ──────────
            while (corriendo) {
                System.out.print(PROMPT);

                // Leer con timeout: si el servidor cerró, salir
                if (!teclado.hasNextLine()) break;
                String input = teclado.nextLine().trim();

                if (!corriendo) break;
                if (input.isEmpty()) continue;

                salida.println(input);

                // Si el usuario escribió SALIR, esperar respuesta y cerrar
                if (input.equalsIgnoreCase("SALIR")) {
                    Thread.sleep(500); // Dar tiempo a que llegue el mensaje de despedida
                    corriendo = false;
                    break;
                }
            }

            System.out.println("\n[Cliente] Sesión finalizada. ¡Hasta luego!");

        } catch (ConnectException e) {
            System.err.println("\n[!] No se pudo conectar al servidor en " + HOST + ":" + PUERTO);
            System.err.println("    Verificá que ServidorMultihilo esté corriendo.");
        } catch (IOException e) {
            System.err.println("\n[!] Error de comunicación: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}