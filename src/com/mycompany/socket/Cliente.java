import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Cliente de Chat con Sockets
 * Se conecta al servidor, permite enviar mensajes y recibir respuestas.
 * Soporta el comando RESOLVE para pedir al servidor que resuelva expresiones.
 *
 * Uso: ejecutar DESPUÉS del Servidor.
 * Puerto: 5000 | Host: localhost
 */
public class Cliente {

    private static final String HOST = "localhost";
    private static final int PUERTO = 5000;
    private static final String MSG_SALIDA = "SALIR";

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║       CLIENTE DE CHAT - SOCKET       ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println("[CLIENTE] Conectando a " + HOST + ":" + PUERTO + "...");

        try (
            Socket socket = new Socket(HOST, PUERTO);
            BufferedReader entradaServidor = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
            PrintWriter salidaServidor = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream()), true);
            Scanner teclado = new Scanner(System.in)
        ) {
            System.out.println("[CLIENTE] ✔ Conectado al servidor!\n");
            mostrarAyuda();

            String mensajeUsuario;

            while (true) {
                System.out.print("Vos >> ");
                mensajeUsuario = teclado.nextLine().trim();

                // Ignorar entradas vacías
                if (mensajeUsuario.isEmpty()) {
                    continue;
                }

                // Enviar mensaje al servidor
                salidaServidor.println(mensajeUsuario);

                // Leer respuesta del servidor
                String respuesta = entradaServidor.readLine();
                if (respuesta == null) {
                    System.out.println("[CLIENTE] El servidor cerró la conexión.");
                    break;
                }

                System.out.println("Servidor >> " + respuesta);
                System.out.println();

                // Si enviamos SALIR, cortamos el loop del lado cliente también
                if (mensajeUsuario.equalsIgnoreCase(MSG_SALIDA)) {
                    System.out.println("[CLIENTE] Conexión finalizada. ¡Hasta luego!");
                    break;
                }
            }

        } catch (ConnectException e) {
            System.err.println("[CLIENTE] No se pudo conectar al servidor.");
            System.err.println("          Asegurate de que el Servidor esté corriendo primero.");
        } catch (IOException e) {
            System.err.println("[CLIENTE] Error de comunicación: " + e.getMessage());
        }
    }

    /**
     * Muestra los comandos disponibles al inicio de la sesión.
     */
    private static void mostrarAyuda() {
        System.out.println("┌─────────────────────────────────────────────────────┐");
        System.out.println("│ COMANDOS DISPONIBLES                                │");
        System.out.println("│                                                     │");
        System.out.println("│  RESOLVE \"expresion\"  -> Calcula una expresión mat  │");
        System.out.println("│    Ejemplo: RESOLVE \"45*23/54+234\"                  │");
        System.out.println("│    Ejemplo: RESOLVE \"(100+50)*2\"                    │");
        System.out.println("│    Ejemplo: RESOLVE \"2^10\"                          │");
        System.out.println("│  MAYUS \"texto\"   -> Convierte un texto a mayúsculas │");
        System.out.println("│    Ejemplo: MAYUS \"hola mundo\"                      │");
        System.out.println("│                                                     │");
        System.out.println("│  AYUDA                -> Ver comandos del servidor  │");
        System.out.println("│  SALIR                -> Cerrar la conexión         │");
        System.out.println("│                                                     │");
        System.out.println("│  También podés escribir cualquier mensaje de chat.  │");
        System.out.println("└─────────────────────────────────────────────────────┘");
        System.out.println();
    }
}
