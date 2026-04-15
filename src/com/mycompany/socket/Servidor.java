import java.io.*;
import java.net.*;
import java.util.*;
import javax.script.*;

/**
 * Servidor de Chat con Sockets
 * Escucha conexiones de clientes, muestra un log de mensajes recibidos
 * y puede resolver expresiones matemáticas con el comando RESOLVE.
 *
 * Uso: ejecutar primero el Servidor, luego el Cliente.
 * Puerto: 5000
 */
public class Servidor {

    private static final int PUERTO = 5000;
    private static final String MSG_SALIDA = "SALIR";

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║       SERVIDOR DE CHAT - SOCKET      ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println("[SERVIDOR] Iniciando en puerto " + PUERTO + "...");

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            System.out.println("[SERVIDOR] Esperando conexión de un cliente...\n");

            // Acepta una conexión de cliente (bucle para múltiples clientes secuenciales)
            while (true) {
                Socket socketCliente = serverSocket.accept();
                String ipCliente = socketCliente.getInetAddress().getHostAddress();
                System.out.println("[SERVIDOR] ✔ Cliente conectado desde: " + ipCliente);
                System.out.println("[SERVIDOR] --- Inicio de sesión ---\n");

                atenderCliente(socketCliente);

                System.out.println("\n[SERVIDOR] --- Sesión cerrada ---");
                System.out.println("[SERVIDOR] Esperando nueva conexión...\n");
            }

        } catch (IOException e) {
            System.err.println("[SERVIDOR] Error al iniciar el servidor: " + e.getMessage());
        }
    }

    /**
     * Atiende a un cliente conectado: lee mensajes, responde y loguea todo.
     */
    private static void atenderCliente(Socket socket) {
        try (
            BufferedReader entrada = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
            PrintWriter salida = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            String mensajeCliente;

            while ((mensajeCliente = entrada.readLine()) != null) {
                // LOG en consola del servidor
                System.out.println("[LOG] Cliente dice: " + mensajeCliente);

                // Detectar mensaje de salida
                if (mensajeCliente.equalsIgnoreCase(MSG_SALIDA)) {
                    System.out.println("[SERVIDOR] El cliente solicitó desconectarse.");
                    salida.println("Hasta luego! Conexión cerrada por el servidor.");
                    break;
                }

                // Procesar el mensaje y generar respuesta
                String respuesta = procesarMensaje(mensajeCliente);
                salida.println(respuesta);
                System.out.println("[LOG] Servidor responde: " + respuesta + "\n");
            }

        } catch (IOException e) {
            System.err.println("[SERVIDOR] Error de comunicación: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("[SERVIDOR] Error al cerrar socket: " + e.getMessage());
            }
        }
    }

    /**
     * Procesa el mensaje recibido del cliente.
     * Si comienza con RESOLVE, evalúa la expresión matemática.
     * De lo contrario, devuelve un eco o ayuda.
     */
    private static String procesarMensaje(String mensaje) {
        String upper = mensaje.trim().toUpperCase();

        // Comando RESOLVE: evalúa expresión matemática
        if (upper.startsWith("RESOLVE ")) {
            String expresion = mensaje.trim().substring(8).trim();
            // Quitar comillas si las tiene
            expresion = expresion.replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
            return resolverExpresion(expresion);
        }
        
        // comando MAYUS: convierte a mayusculas
        if (upper.startsWith("MAYUS ")) {
    String texto = mensaje.trim().substring(6).trim();
    texto = texto.replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
    return texto.toUpperCase();
}

        // Comando de ayuda
        if (upper.equals("AYUDA") || upper.equals("HELP")) {
            return "Comandos disponibles:\n" +
                   "  RESOLVE \"expresion\" -> Resuelve una expresión matemática\n" +
                   "    Ejemplo: RESOLVE \"45*23/54+234\"\n" +
                   "  SALIR               -> Cierra la conexión\n" +
                   "  AYUDA               -> Muestra este mensaje";
        }
        
        // Respuesta por defecto (eco con info)
        return "Servidor recibió: \"" + mensaje + "\". " +
               "Tip: usá RESOLVE \"expresión\" para calcular, o AYUDA para ver comandos.";
    }

    /**
     * Evalúa una expresión matemática usando JavaScript engine (Nashorn/Rhino).
     * Soporta: +, -, *, /, potencias (^), paréntesis, funciones Math.
     */
    private static String resolverExpresion(String expresion) {
        try {
            // Reemplazar ^ por ** para potencias (JS moderno)
            String exprJS = expresion.replace("^", "**");

            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");

            if (engine == null) {
                // Fallback: evaluador básico propio
                return evaluarBasico(expresion);
            }

            Object resultado = engine.eval(exprJS);
            double valor = Double.parseDouble(resultado.toString());

            // Si el resultado es entero, mostrarlo sin decimales
            if (valor == Math.floor(valor) && !Double.isInfinite(valor)) {
                return "Resultado de [" + expresion + "] = " + (long) valor;
            } else {
                return "Resultado de [" + expresion + "] = " + valor;
            }

        } catch (ScriptException e) {
            return "Error al evaluar la expresión \"" + expresion + "\": " + e.getMessage();
        } catch (Exception e) {
            return "No se pudo resolver \"" + expresion + "\": expresión inválida.";
        }
    }

    /**
     * Evaluador básico de respaldo (solo +, -, *, /).
     * Se usa si el motor de JavaScript no está disponible.
     */
    private static String evaluarBasico(String expresion) {
        try {
            // Usa la clase interna para evaluar aritmética simple
            double resultado = new Object() {
                int pos = -1, ch;
                String expr;

                void nextChar() {
                    ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
                }

                boolean eat(int charToEat) {
                    while (ch == ' ') nextChar();
                    if (ch == charToEat) { nextChar(); return true; }
                    return false;
                }

                double parse(String e) {
                    expr = e;
                    nextChar();
                    double x = parseExpression();
                    if (pos < expr.length()) throw new RuntimeException("Carácter inesperado: " + (char)ch);
                    return x;
                }

                double parseExpression() {
                    double x = parseTerm();
                    for (;;) {
                        if      (eat('+')) x += parseTerm();
                        else if (eat('-')) x -= parseTerm();
                        else return x;
                    }
                }

                double parseTerm() {
                    double x = parseFactor();
                    for (;;) {
                        if      (eat('*')) x *= parseFactor();
                        else if (eat('/')) x /= parseFactor();
                        else return x;
                    }
                }

                double parseFactor() {
                    if (eat('+')) return parseFactor();
                    if (eat('-')) return -parseFactor();
                    double x;
                    int startPos = this.pos;
                    if (eat('(')) {
                        x = parseExpression();
                        eat(')');
                    } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                        x = Double.parseDouble(expr.substring(startPos, this.pos));
                    } else {
                        throw new RuntimeException("Carácter inesperado: " + (char)ch);
                    }
                    if (eat('^')) x = Math.pow(x, parseFactor());
                    return x;
                }
            }.parse(expresion);

            if (resultado == Math.floor(resultado) && !Double.isInfinite(resultado)) {
                return "Resultado de [" + expresion + "] = " + (long) resultado;
            } else {
                return "Resultado de [" + expresion + "] = " + resultado;
            }
        } catch (Exception e) {
            return "Error al evaluar \"" + expresion + "\": " + e.getMessage();
        }
    }
}
