package com.mycompany.socket;
import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import javax.script.*;

/**
 * ServidorMultihilo - Servidor de Chat con Sockets y soporte multihilo.
 * Cada cliente conectado es atendido en su propio hilo (Thread).
 * Puerto: 5000
 */
public class ServidorMultihilo {

    private static final int PUERTO = 5000;

    // Mapa sincronizado: nombre_usuario -> manejador del cliente
    private static final ConcurrentHashMap<String, ManejadorCliente> clientesConectados =
            new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────
    //  MAIN
    // ─────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        log("SISTEMA", "╔══════════════════════════════════════════════════╗");
        log("SISTEMA", "║     SERVIDOR MULTIHILO DE CHAT - SOCKETS         ║");
        log("SISTEMA", "╚══════════════════════════════════════════════════╝");
        log("SISTEMA", "Iniciando en puerto " + PUERTO + "...");

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            log("SISTEMA", "Servidor listo. Esperando clientes...\n");

            while (true) {
                Socket socketCliente = serverSocket.accept();
                String ip = socketCliente.getInetAddress().getHostAddress();
                log("SISTEMA", "Nueva conexión entrante desde: " + ip);

                // Crear hilo para este cliente
                ManejadorCliente manejador = new ManejadorCliente(socketCliente);
                Thread hilo = new Thread(manejador);
                hilo.setDaemon(true);
                hilo.start();
            }

        } catch (IOException e) {
            log("SISTEMA", "Error fatal del servidor: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  UTILIDADES GLOBALES DEL SERVIDOR
    // ─────────────────────────────────────────────────────────────

    /** Registra un nuevo cliente. Asigna nombre único si hay conflicto. */
    static String registrarCliente(String nombreDeseado, ManejadorCliente manejador) {
        String nombre = nombreDeseado.trim().replaceAll("\\s+", "_");
        if (nombre.isEmpty()) nombre = "Usuario";

        // Si el nombre ya existe, agrega número
        if (clientesConectados.containsKey(nombre)) {
            int n = 2;
            while (clientesConectados.containsKey(nombre + n)) n++;
            nombre = nombre + n;
        }
        clientesConectados.put(nombre, manejador);
        log("SISTEMA", "Cliente registrado como: " + nombre +
                " | Total conectados: " + clientesConectados.size());
        return nombre;
    }

    /** Elimina un cliente del registro. */
    static void desregistrarCliente(String nombre) {
        clientesConectados.remove(nombre);
        log("SISTEMA", "Cliente desconectado: " + nombre +
                " | Total conectados: " + clientesConectados.size());
        // Avisar al resto
        broadcast("SERVIDOR",
                ">>> " + nombre + " se ha desconectado del chat.", null);
    }

    /** Envía un mensaje a todos los clientes conectados (excepto al emisor si se especifica). */
    static void broadcast(String emisor, String mensaje, String excluir) {
        for (Map.Entry<String, ManejadorCliente> entry : clientesConectados.entrySet()) {
            if (excluir != null && entry.getKey().equals(excluir)) continue;
            entry.getValue().enviar("[" + emisor + " → TODOS] " + mensaje);
        }
    }

    /** Envía mensaje a un cliente específico. Retorna true si existe. */
    static boolean enviarA(String destino, String emisor, String mensaje) {
        ManejadorCliente dest = clientesConectados.get(destino);
        if (dest == null) return false;
        dest.enviar("[" + emisor + " → " + destino + "] " + mensaje);
        return true;
    }

    /** Lista los clientes conectados como string. */
    static String listarClientes() {
        if (clientesConectados.isEmpty()) return "(ninguno)";
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String nombre : clientesConectados.keySet()) {
            sb.append("  ").append(i++).append(". ").append(nombre).append("\n");
        }
        return sb.toString().trim();
    }

    /** Logger centralizado con timestamp. */
    static void log(String origen, String mensaje) {
        String ts = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        System.out.println("[" + ts + "][" + origen + "] " + mensaje);
    }

    // ─────────────────────────────────────────────────────────────
    //  HILO POR CLIENTE
    // ─────────────────────────────────────────────────────────────
    static class ManejadorCliente implements Runnable {

        private final Socket socket;
        private PrintWriter salida;
        private String nombreUsuario;

        ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        /** Envía una línea al cliente de forma segura. */
        void enviar(String mensaje) {
            if (salida != null) salida.println(mensaje);
        }

        @Override
        public void run() {
            try (
                BufferedReader entrada = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream()), true)
            ) {
                this.salida = out;

                // ── 1. Solicitar nombre de usuario ──────────────────────
                enviar("╔══════════════════════════════════════════════════╗");
                enviar("║       SERVIDOR DE CHAT MULTIHILO - BIENVENIDO    ║");
                enviar("╚══════════════════════════════════════════════════╝");
                enviar("Ingresá tu nombre de usuario:");
                enviar("__PROMPT__");

                String nombreDeseado = entrada.readLine();
                if (nombreDeseado == null) return;

                // Registrar con nombre único
                this.nombreUsuario = registrarCliente(nombreDeseado.trim(), this);

                boolean nombreCambiado = !nombreDeseado.trim().equals(nombreUsuario);

                // ── 2. Bienvenida personalizada ──────────────────────────
                enviar("");
                enviar("✔ Conectado exitosamente!");
                if (nombreCambiado) {
                    enviar("⚠ El nombre '" + nombreDeseado.trim() + "' ya estaba en uso.");
                    enviar("  Se te asignó el nombre: " + nombreUsuario);
                } else {
                    enviar("  Tu nombre de usuario es: " + nombreUsuario);
                }
                enviar("");
                enviar(menuAyuda());
                enviar("");

                log(nombreUsuario, "Sesión iniciada.");

                // Notificar ingreso al resto
                broadcast("SERVIDOR",
                        ">>> " + nombreUsuario + " se unió al chat!", nombreUsuario);

                // ── 3. Bucle principal de mensajes ───────────────────────
                String linea;
                while ((linea = entrada.readLine()) != null) {
                    String msg = linea.trim();
                    if (msg.isEmpty()) continue;

                    log(nombreUsuario, "Recibido: " + msg);

                    if (msg.equalsIgnoreCase("SALIR")) {
                        enviar("Hasta luego, " + nombreUsuario + "! 👋");
                        break;
                    }

                    String respuesta = procesarComando(msg);
                    enviar(respuesta);
                }

            } catch (IOException e) {
                log(nombreUsuario != null ? nombreUsuario : "?",
                        "Conexión perdida: " + e.getMessage());
            } finally {
                if (nombreUsuario != null) {
                    desregistrarCliente(nombreUsuario);
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        // ─────────────────────────────────────────────────────────
        //  PROCESAMIENTO DE COMANDOS
        // ─────────────────────────────────────────────────────────
        private String procesarComando(String msg) {
            String upper = msg.toUpperCase();

            // ── AYUDA ────────────────────────────────────────────────
            if (upper.equals("AYUDA") || upper.equals("HELP")) {
                return menuAyuda();
            }

            // ── FECHA Y HORA ─────────────────────────────────────────
            if (upper.equals("FECHA") || upper.equals("HORA") || upper.equals("DATE")) {
                LocalDateTime ahora = LocalDateTime.now();
                String fecha = ahora.format(DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy", new Locale("es", "AR")));
                String hora  = ahora.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                return "📅 Fecha: " + fecha + "\n⏰ Hora:  " + hora;
            }

            // ── RESOLVE ──────────────────────────────────────────────
            if (upper.startsWith("RESOLVE ")) {
                String expr = msg.substring(8).trim()
                        .replaceAll("^\"|\"$", "")
                        .replaceAll("^'|'$", "");
                log(nombreUsuario, "RESOLVE: " + expr);
                return resolverExpresion(expr);
            }
            // MAYUS ──────────────────────────────────────────────
            if (upper.startsWith("MAYUS ")) {
             String texto = msg.substring(6).trim();
             // Quita comillas si el usuario las puso
             texto = texto.replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
             log(nombreUsuario, "Comando MAYUS: " + texto);
             return "🔠 Texto en mayúsculas: " + texto.toUpperCase();
}

            // ── LISTAR CLIENTES ──────────────────────────────────────
            if (upper.equals("LISTAR") || upper.equals("LIST") || upper.equals("CLIENTES")) {
                return "👥 Clientes conectados ahora (" + clientesConectados.size() + "):\n"
                        + listarClientes();
            }

            // ── MENSAJE A UN CLIENTE: *NOMBRE "msg" ───────────────────
            // Ejemplo: *Pedro "Hola Pedro!"
            if (msg.startsWith("*") && !msg.toUpperCase().startsWith("*ALL")) {
                return procesarMensajeDirecto(msg);
            }

            // ── MENSAJE A TODOS: *ALL "msg" ───────────────────────────
            if (upper.startsWith("*ALL ")) {
                String contenido = extraerContenido(msg.substring(4).trim());
                if (contenido == null || contenido.isEmpty()) {
                    return "⚠ Uso correcto: *ALL \"tu mensaje\"";
                }
                broadcast(nombreUsuario, contenido, null);
                log(nombreUsuario, "Broadcast: " + contenido);
                return "✔ Mensaje enviado a todos (" + clientesConectados.size() + " clientes).";
            }

            // ── MENSAJE NO RECONOCIDO ────────────────────────────────
            return "❓ Comando no reconocido: \"" + msg + "\"\n" +
                   "   Escribí AYUDA para ver los comandos disponibles.";
        }

        /**
         * Procesa mensajes directos a uno o más clientes.
         * Formato: *Usuario1 *Usuario2 "mensaje"
         *  o bien: *Usuario1 "mensaje"
         */
        private String procesarMensajeDirecto(String msg) {
            // Extraer todos los destinatarios (palabras que empiezan con *)
            // y el mensaje entre comillas al final
            List<String> destinatarios = new ArrayList<>();
            String contenido = null;

            // Buscar el mensaje entre comillas
            int idxComilla = msg.indexOf('"');
            if (idxComilla == -1) {
                return "⚠ Formato incorrecto. Uso: *Usuario \"mensaje\"\n" +
                       "   Para varios: *User1 *User2 \"mensaje\"";
            }

            String parteDest = msg.substring(0, idxComilla).trim();
            contenido = extraerContenido(msg.substring(idxComilla).trim());

            if (contenido == null || contenido.isEmpty()) {
                return "⚠ El mensaje no puede estar vacío.";
            }

            // Separar destinatarios
            String[] tokens = parteDest.split("\\s+");
            for (String token : tokens) {
                if (token.startsWith("*")) {
                    String dest = token.substring(1);
                    if (!dest.isEmpty()) destinatarios.add(dest);
                }
            }

            if (destinatarios.isEmpty()) {
                return "⚠ No se especificó ningún destinatario válido.";
            }

            StringBuilder resultado = new StringBuilder();
            List<String> noEncontrados = new ArrayList<>();
            List<String> enviados      = new ArrayList<>();

            for (String dest : destinatarios) {
                if (dest.equalsIgnoreCase(nombreUsuario)) {
                    resultado.append("⚠ No podés enviarte mensajes a vos mismo.\n");
                    continue;
                }
                boolean ok = enviarA(dest, nombreUsuario, contenido);
                if (ok) {
                    enviados.add(dest);
                    log(nombreUsuario, "Mensaje a " + dest + ": " + contenido);
                } else {
                    noEncontrados.add(dest);
                    log(nombreUsuario, "Intento de mensaje a " + dest + " (no existe).");
                }
            }

            if (!enviados.isEmpty()) {
                resultado.append("✔ Mensaje enviado a: ")
                         .append(String.join(", ", enviados)).append("\n");
            }
            if (!noEncontrados.isEmpty()) {
                resultado.append("⚠ Los siguientes usuarios NO existen o no están conectados: ")
                         .append(String.join(", ", noEncontrados)).append("\n");
                resultado.append("  Usá LISTAR para ver quién está conectado.");
            }

            return resultado.toString().trim();
        }

        /** Extrae el contenido entre comillas dobles. */
        private String extraerContenido(String s) {
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                return s.substring(1, s.length() - 1);
            }
            // Sin comillas: tomar todo como contenido
            return s;
        }

        /** Menú de ayuda completo. */
        private String menuAyuda() {
            return "┌──────────────────────────────────────────────────────┐\n" +
                   "│ COMANDOS DISPONIBLES                                 │\n" +
                   "├──────────────────────────────────────────────────────┤\n" +
                   "│ FECHA / HORA       → Muestra fecha y hora actual     │\n" +
                   "│ RESOLVE \"expr\"     → Calcula expresión matemática    │\n" +
                   "│   Ej: RESOLVE \"45*23/54+234\"                         │\n" +
                   "│   Ej: RESOLVE \"(100+50)*2^3\"                         │\n" +
                   "│ MAYUS \"texto\"      → Convierte texto a mayúsculas    │\n" +
                   "│ LISTAR             → Lista los clientes conectados   │\n" +
                   "│ *Usuario \"msg\"     → Envía mensaje a un usuario      │\n" +
                   "│ *U1 *U2 \"msg\"      → Envía mensaje a varios usuarios │\n" +
                   "│ *ALL \"msg\"         → Envía mensaje a todos           │\n" +
                   "│ AYUDA / HELP       → Muestra esta ayuda              │\n" +
                   "│ SALIR              → Cierra tu conexión              │\n" +
                   "└──────────────────────────────────────────────────────┘";
        }

        // ─────────────────────────────────────────────────────────
        //  RESOLVER EXPRESIONES MATEMÁTICAS
        // ─────────────────────────────────────────────────────────
        private String resolverExpresion(String expresion) {
            try {
                String exprJS = expresion.replace("^", "**");
                ScriptEngineManager manager = new ScriptEngineManager();
                ScriptEngine engine = manager.getEngineByName("JavaScript");

                double valor;
                if (engine != null) {
                    Object resultado = engine.eval(exprJS);
                    valor = Double.parseDouble(resultado.toString());
                } else {
                    valor = evaluarBasico(expresion);
                }

                String valorStr = (valor == Math.floor(valor) && !Double.isInfinite(valor))
                        ? String.valueOf((long) valor)
                        : String.valueOf(valor);

                return "🔢 Resultado de [" + expresion + "] = " + valorStr;

            } catch (Exception e) {
                return "⚠ Error al evaluar \"" + expresion + "\": " + e.getMessage();
            }
        }

        private double evaluarBasico(String expresion) {
            return new Object() {
                int pos = -1, ch;
                final String expr = expresion;

                void nextChar() { ch = (++pos < expr.length()) ? expr.charAt(pos) : -1; }
                boolean eat(int c) {
                    while (ch == ' ') nextChar();
                    if (ch == c) { nextChar(); return true; }
                    return false;
                }
                double parse() { nextChar(); double x = parseExpr(); return x; }
                double parseExpr() {
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
                    double x; int start = pos;
                    if (eat('(')) { x = parseExpr(); eat(')'); }
                    else {
                        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                        x = Double.parseDouble(expr.substring(start + 1, pos));
                    }
                    if (eat('^')) x = Math.pow(x, parseFactor());
                    return x;
                }
            }.parse();
        }
    }
}