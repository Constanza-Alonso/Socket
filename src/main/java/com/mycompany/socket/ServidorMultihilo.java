package com.mycompany.socket;
import java.io.*; // Lectura y escritura de texto por socket
import java.net.*; // ServerSockey y Socket
import java.time.*; // Para fecha y hora actual
import java.time.format.*; 
import java.util.*; // Listas
import java.util.concurrent.*; // Manejo seguro entre hilos
import javax.script.*; // Evaluar epresiones matematicas

/**
 * ServidorMultihilo - Servidor de Chat con Sockets y soporte multihilo.
 * Cada cliente conectado es atendido en su propio hilo (Thread).
 * Puerto: 5000
 */
public class ServidorMultihilo {

    // Puerto donde el servidor va a escuchar
    private static final int PUERTO = 5000;

    // Registro de todos los clientes, clave -> nombre, valor -> manejadorCliente
    private static final ConcurrentHashMap<String, ManejadorCliente> clientesConectados =
            new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────
    //  MAIN
    // ─────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        // Encabezado con log que agrega tiemstamp a cada mensaje
        log("SISTEMA", "╔══════════════════════════════════════════════════╗");
        log("SISTEMA", "║     SERVIDOR MULTIHILO DE CHAT - SOCKETS         ║");
        log("SISTEMA", "╚══════════════════════════════════════════════════╝");
        log("SISTEMA", "Iniciando en puerto " + PUERTO + "...");

        /// Crea el ServerSocket que abre la puerta en puerto 5000, si esta en uzo larga excepcion
        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            // Confirma que el servidor esta listo y esperando
            log("SISTEMA", "Servidor listo. Esperando clientes...\n");

            // Bucle infinito 
            while (true) {
                // Se queda bloqueado hasta que un cliente se conecte
                Socket socketCliente = serverSocket.accept();
                // Obtiene direccion IP del cliente y la loguea
                String ip = socketCliente.getInetAddress().getHostAddress();
                log("SISTEMA", "Nueva conexión entrante desde: " + ip);

                // Crear hilo para este cliente
                ManejadorCliente manejador = new ManejadorCliente(socketCliente);
                // Lanza manejador en un hilo separado
                Thread hilo = new Thread(manejador);
                hilo.setDaemon(true); // Muere solo si el servidor cierra
                hilo.start(); // Empieza a correr
            }

        // Si algo falla al abrir el puerto o aceptar conexiones, programa termina 
        } catch (IOException e) {
            log("SISTEMA", "Error fatal del servidor: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  UTILIDADES GLOBALES DEL SERVIDOR
    // ─────────────────────────────────────────────────────────────

    // Registra nuevo cliente, nombre y su manejador
    static String registrarCliente(String nombreDeseado, ManejadorCliente manejador) {
        // Limpia el nombre, reemplaza espacios por guion bajo
        String nombre = nombreDeseado.trim().replaceAll("\\s+", "_");
        // Si quedo vacio, le asigna Usuario por defecto
        if (nombre.isEmpty()) nombre = "Usuario";

        // Si el nombre ya existe, agrega número empezando por 2
        if (clientesConectados.containsKey(nombre)) {
            int n = 2;
            while (clientesConectados.containsKey(nombre + n)) n++;
            nombre = nombre + n;
        }
        // Agrega el cliente al mapa con su nombre definido 
        clientesConectados.put(nombre, manejador);
        // Loggea el registro y devuelve el nombre final asignado
        log("SISTEMA", "Cliente registrado como: " + nombre +
                " | Total conectados: " + clientesConectados.size());
        return nombre;
    }

    // Elimina el cliente del mapa cuando se desconecta
    static void desregistrarCliente(String nombre) {
        clientesConectados.remove(nombre);
        // Loggea la desconeccion con el total actualizado 
        log("SISTEMA", "Cliente desconectado: " + nombre +
                " | Total conectados: " + clientesConectados.size());
        // Avisar a todos los demas que ese usuario se fue 
        broadcast("SERVIDOR",
                ">>> " + nombre + " se ha desconectado del chat.", null); // null para no excluir a nadie
    }

    // Recorre todos los clientes del mapa
    static void broadcast(String emisor, String mensaje, String excluir) {
        for (Map.Entry<String, ManejadorCliente> entry : clientesConectados.entrySet()) {
            // Si hay alguien a excluir lo saltea
            if (excluir != null && entry.getKey().equals(excluir)) continue;
            // Le envia el mensaje al manejador de cada cliente
            entry.getValue().enviar("[" + emisor + " → TODOS] " + mensaje);
        }
    }

    // Busca al destinatario en el mapa, si no existe devuelve false 
    static boolean enviarA(String destino, String emisor, String mensaje) {
        ManejadorCliente dest = clientesConectados.get(destino);
        if (dest == null) return false;
        // Si lo encontro le manda el mensaje y devuleve true para confirmar que se envio 
        dest.enviar("[" + emisor + " → " + destino + "] " + mensaje);
        return true;
    }

    // Si no hay nadie conectado imprime ninguno
    static String listarClientes() {
        if (clientesConectados.isEmpty()) return "(ninguno)";
        // Recorre las claves del mapa y arma una lista numerada
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String nombre : clientesConectados.keySet()) {
            sb.append("  ").append(i++).append(". ").append(nombre).append("\n");
        }
        return sb.toString().trim();
    }

    // El logger del servicio, obtiene la fecha y hora actual y la imprime
    static void log(String origen, String mensaje) {
        String ts = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        System.out.println("[" + ts + "][" + origen + "] " + mensaje);
    }

    // ─────────────────────────────────────────────────────────────
    //  HILO POR CLIENTE
    // ─────────────────────────────────────────────────────────────
    static class ManejadorCliente implements Runnable {
    
        // Cada manejador guarda su propio Socket, su canal de escritura y el nombre de usuario que atiende
        private final Socket socket;
        private PrintWriter salida;
        private String nombreUsuario;

        // Constructor: recibe y guarda el Socket del cliente
        ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        // Metodo para enviarle una linea al cliente
        void enviar(String mensaje) {
            if (salida != null) salida.println(mensaje);
        }

        // Abre los canales de entrada y salida para este cliente y guarda out para que otros hilos puedan llamar enviar
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

                // Espera que el cliente mande su nombre, null si el cliente se desconecto antes 
                String nombreDeseado = entrada.readLine();
                if (nombreDeseado == null) return;

                // Registrar con nombre único
                this.nombreUsuario = registrarCliente(nombreDeseado.trim(), this);

                // Compara el nombre pedido con el asignado para saber si hubo error
                boolean nombreCambiado = !nombreDeseado.trim().equals(nombreUsuario);

                // ── 2. Bienvenida personalizada ──────────────────────────
                enviar("");
                enviar("✔ Conectado exitosamente!");
                if (nombreCambiado) {
                    enviar("⚠ El nombre '" + nombreDeseado.trim() + "' ya estaba en uso."); // Avisa si el nombre fue cambiado
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
                        ">>> " + nombreUsuario + " se unió al chat!", nombreUsuario); // Excluye a el mismo

                // ── 3. Bucle principal de mensajes ───────────────────────
                String linea;
                while ((linea = entrada.readLine()) != null) { // Lee cada mensaje
                    String msg = linea.trim();
                    if (msg.isEmpty()) continue;

                    // Loggea en consola del servidor y procesa
                    log(nombreUsuario, "Recibido: " + msg);

                    // Si el cliente manda SALIR, se despide y sale del bucle
                    if (msg.equalsIgnoreCase("SALIR")) {
                        enviar("Hasta luego, " + nombreUsuario + "! 👋");
                        break;
                    }

                    // Cualquier otro comando lo procesa y envia respuesta
                    String respuesta = procesarComando(msg);
                    enviar(respuesta);
                }

            // Si hubo un error de red inesperado
            } catch (IOException e) {
                log(nombreUsuario != null ? nombreUsuario : "?",
                        "Conexión perdida: " + e.getMessage());
            } finally {
                if (nombreUsuario != null) {
                    desregistrarCliente(nombreUsuario); // Elimina el cliente del mapa y notifica
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        // ─────────────────────────────────────────────────────────
        //  PROCESAMIENTO DE COMANDOS
        // ─────────────────────────────────────────────────────────
        private String procesarComando(String msg) {
            // Convierte mensaje en mayuscula para comparar comandos 
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
                        .replaceAll("^\"|\"$", "") // Saca comillas
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
             return "🔠 Texto en mayúsculas: " + texto.toUpperCase(); // Convierte en mayuscula
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

        // Si el texto esta entre comillas dobles las saca y devuelve solo el contenido 
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
                String exprJS = expresion.replace("^", "**"); // Reemplaza ^ por ** para potencias en Java 
                ScriptEngineManager manager = new ScriptEngineManager();
                ScriptEngine engine = manager.getEngineByName("JavaScript"); // Obtener el motor de Java

                double valor;
                if (engine != null) {
                    // Evalua la epresion como codigo JavaScript y convierte el resultado a double
                    Object resultado = engine.eval(exprJS); 
                    valor = Double.parseDouble(resultado.toString());
                } else {
                    valor = evaluarBasico(expresion);
                }

                // Si el resultado es entero lo muestra sin decimales, sino con 
                String valorStr = (valor == Math.floor(valor) && !Double.isInfinite(valor))
                        ? String.valueOf((long) valor)
                        : String.valueOf(valor);

                return "🔢 Resultado de [" + expresion + "] = " + valorStr;

            } catch (Exception e) {
                return "⚠ Error al evaluar \"" + expresion + "\": " + e.getMessage();
            }
        }

        // Recibe expresion matematica como texto y devuelve resultado como double 
        private double evaluarBasico(String expresion) {
            return new Object() { // Crea objeto anonimo para no crear clase separada que se usa 1 vez
                int pos = -1, ch; // pos: posicion actual, ch: caracter actual, -1: no hay mas caracteres
                final String expr = expresion;  // Guarda la expresion dentro del objetivo anonimo, final porq no cambia

                // Avanza al siguiente caracter 
                void nextChar() { ch = (++pos < expr.length()) ? expr.charAt(pos) : -1; }
                // consume un caracter especifico 
                boolean eat(int c) {
                    while (ch == ' ') nextChar(); // Saltea los espacios en blanco
                    // Si el caracter actual es el que buscamos devuelve true sino false sin moverse
                    if (ch == c) { nextChar(); return true; }
                    return false;
                }
                // Lee el primer caracter, inicia el analisis, devuelve resultado final
                double parse() { nextChar(); double x = parseExpr(); return x; }
                // Evalua el primer termino (*, /, numero) 
                double parseExpr() {
                    double x = parseTerm();
                    // Bucle infinito que busca + o -
                    for (;;) {
                        if      (eat('+')) x += parseTerm();
                        else if (eat('-')) x -= parseTerm();
                        else return x;
                    }
                }
                // Lo mismo pero para * y /, dentro de una multiplicacion pueden haber parentesis o potencias
                double parseTerm() {
                    double x = parseFactor();
                    for (;;) {
                        if      (eat('*')) x *= parseFactor();
                        else if (eat('/')) x /= parseFactor();
                        else return x;
                    }
                }
                // Maneja los signos delante de un numero, lo consume y aplica el signo al valor siguiente
                double parseFactor() {
                    if (eat('+')) return parseFactor();
                    if (eat('-')) return -parseFactor();
                    double x; int start = pos; // x para guardar valor y start para recordar donde empieza el numero actual 
                    if (eat('(')) { x = parseExpr(); eat(')'); } // Si hay () evalua lo de adentro primero
                    // Si no hay () 
                    else {
                        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                        x = Double.parseDouble(expr.substring(start, pos));
                    }
                    // Despues de obtener el numero, verifica potencia 
                    if (eat('^')) x = Math.pow(x, parseFactor());
                    return x;
                }
            }.parse(); // Cierra objeto anonimo 
        }
    }
}