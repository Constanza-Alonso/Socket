package com.mycompany.socket;

import java.io.*; // para leer y escribir texto
import java.net.*; // Para todo lo relacionado a sockets y red
import java.util.Scanner; // para leer lo que escribe el usuario por teclado

public class ClienteChat { // accesible desde cualquier parte del proyecto

    // Indican a donde conectarse en este caso  mi propia computadora
    private static final String HOST   = "localhost";
    // Numero de puerto donde escucha el servidor, ambos deben usar el mismo numero para encontrarse 
    private static final int    PUERTO = 5000;
    // El texto que se muestra cuando es tu turno de escribir
    private static final String PROMPT = "Vos >> ";
    // Indica que el programa debe seguir funcionando, volatile porque es compartida por dos hilos
    private static volatile boolean corriendo = true;

    public static void main(String[] args) {
        // Imprime el encabezado visual y avisa a que direccion y puerto se esta conectando
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║         CLIENTE DE CHAT - SOCKETS                ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("Conectando a " + HOST + ":" + PUERTO + "...");

        try (
            // Crea el socket y establece la conexion con el servidor 
            Socket socket          = new Socket(HOST, PUERTO);
            // Crea el canal de lectura, permite leer linea por linea 
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            // Crea el canal de escritura hacia el servidor 
            PrintWriter salida     = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()), true);
            // Crea el lector del teclado (lo que escribe el usuario por consola)
            Scanner teclado        = new Scanner(System.in)
        ) {
            // Imprime que la conexion fue exitosa hasta aca
            System.out.println("✔ Conectado al servidor!\n");

           // Crea un nuevo hilo (lambda) que va a correr en paralelo al hijo principal
            Thread receptor = new Thread(() -> {
                /* Bucle infinito que lee lineas del servidor mientras corriendo = true y 
                 la conexion siga abierta */
                try {
                    String linea;
                    while (corriendo && (linea = entrada.readLine()) != null) {
                        // Termino de responder, invita al usuario a escribir
                        if (linea.equals("__PROMPT__")) {
                            System.out.print(PROMPT);
                        // Si es cualquier otra linea, la imprime normalmente como mensaje del servidor
                        } else {
                            System.out.println(linea);
                        }
                    }
                // Si ocurre un error de red, avisa que se perdio la conexion 
                } catch (IOException e) {
                    if (corriendo) System.out.println("\n[!] Conexión perdida.");
                // Pase lo que pase (error o cierre) el hilo principal termina
                } finally {
                    corriendo = false;
                }
            });
            // Si el programa principal termina, este hilo muere sin bloquear el cierre
            receptor.setDaemon(true);
            receptor.start();

            // Bucle del hilo principal mientras la conexion este activa 
            while (corriendo) {
                // Verifica si hay input disponible de teclado, si el usuario termina sale del bucle
                if (!teclado.hasNextLine()) break;
                // Lee la linea que escribio el usuario y le saca los espacios sobrantes de los extremos
                String input = teclado.nextLine().trim();

                // vuelve a chequear que el servidor este activo
                if (!corriendo) break;
                // Si el usuario escribio enter sin nada, reimprime el prompt 
                if (input.isEmpty()) {
                    System.out.print(PROMPT); 
                    continue;
                }

                // Envia el mensaje al servidor a traves del socket
                salida.println(input);
            
                // Si el usuario escribio SALIR, espera 400 mls, corriendo = false y sale del bucle
                if (input.equalsIgnoreCase("SALIR")) {
                    Thread.sleep(400);
                    corriendo = false;
                    break;
                }
            }

            // Imprime mensaje de cierre
            System.out.println("\n[Cliente] Sesión finalizada.");

        // Captura error si el servidor no esta corriendo mientras se ejecuta el cliente
        } catch (ConnectException e) {
            System.err.println("\n[!] No se pudo conectar. ¿Está corriendo el Servidor?");
        // Captura cualquier otro error inesperado e imprime su descripcion
        } catch (Exception e) {
            System.err.println("\n[!] Error: " + e.getMessage());
        }
    }
}