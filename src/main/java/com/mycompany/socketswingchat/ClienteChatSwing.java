package com.mycompany.socketswingchat;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import javax.swing.text.*;
import java.util.HashMap;
import java.util.Map;
import java.awt.Toolkit;

public class ClienteChatSwing extends JFrame {

    private JTextPane areaChat;
    private JTextField campoMensaje;
    private JButton botonEnviar;
    private JButton botonSalir;

    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter salida;
    private String usuario;

    private JLabel labelEscribiendo;
    private Timer timerEscribiendo;
    
    private JList<String> listaUsuarios;
    private Map<String, Color> coloresUsuarios = new HashMap<>();

    public ClienteChatSwing() {
        usuario = pedirUsuarioValido();
        configurarVentana();
        conectarServidor();
        escucharMensajes();
    }

    private String pedirUsuarioValido() {
        String nombre;

        while (true) {
            nombre = JOptionPane.showInputDialog(
                    null,
                    "Ingrese nombre de usuario:",
                    "Usuario",
                    JOptionPane.QUESTION_MESSAGE
            );

            if (nombre == null) {
                System.exit(0);
            }

            nombre = nombre.trim();

            if (validarUsuario(nombre)) {
                return nombre;
            }

            JOptionPane.showMessageDialog(
                    null,
                    "Usuario inválido.\n" +
                            "- Mínimo 3 caracteres\n" +
                            "- Máximo 15 caracteres\n" +
                            "- Sin espacios\n" +
                            "- Solo letras, números y guion bajo",
                    "Error de validación",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private boolean validarUsuario(String usuario) {
        return usuario.matches("^[A-Za-z0-9_]{3,15}$");
    }

    private void configurarVentana() {
    setTitle("Chat Cliente - " + usuario);
    setSize(850, 450);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);

    getContentPane().setBackground(new Color(245, 245, 245));

    areaChat = new JTextPane();
    areaChat.setEditable(false);
    areaChat.setBackground(new Color(250, 250, 250));
    areaChat.setFont(new Font("Arial", Font.PLAIN, 14));

    campoMensaje = new JTextField();
    campoMensaje.setFont(new Font("Arial", Font.PLAIN, 14));

    labelEscribiendo = new JLabel(" ");
    labelEscribiendo.setFont(new Font("Arial", Font.ITALIC, 12));
    labelEscribiendo.setForeground(Color.GRAY);

    botonEnviar = new JButton("Enviar");
    botonEnviar.setBackground(new Color(70, 130, 180));
    botonEnviar.setForeground(Color.BLACK);
    botonEnviar.setFocusPainted(false);

    botonSalir = new JButton("Salir");
    botonSalir.setBackground(new Color(180, 70, 70));
    botonSalir.setForeground(Color.BLACK);
    botonSalir.setFocusPainted(false);

    listaUsuarios = new JList<>();
    listaUsuarios.setFont(new Font("Arial", Font.PLAIN, 13));
    listaUsuarios.setBorder(BorderFactory.createTitledBorder("Usuarios conectados"));
    listaUsuarios.setBackground(new Color(250, 250, 250));
    listaUsuarios.setPreferredSize(new Dimension(120, 0));

    JPanel panelInferior = new JPanel(new BorderLayout(8, 0));
    panelInferior.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    JPanel panelBotones = new JPanel(new GridLayout(1, 2, 5, 0));
    panelBotones.setPreferredSize(new Dimension(160, 32));
    panelBotones.add(botonEnviar);
    panelBotones.add(botonSalir);

    JPanel panelMensaje = new JPanel(new BorderLayout());
    panelMensaje.add(labelEscribiendo, BorderLayout.NORTH);
    panelMensaje.add(campoMensaje, BorderLayout.CENTER);

    panelInferior.add(panelMensaje, BorderLayout.CENTER);
    panelInferior.add(panelBotones, BorderLayout.EAST);

    add(new JScrollPane(areaChat), BorderLayout.CENTER);
    add(new JScrollPane(listaUsuarios), BorderLayout.EAST);
    add(panelInferior, BorderLayout.SOUTH);

    botonEnviar.addActionListener(e -> enviarMensaje());
    campoMensaje.addActionListener(e -> enviarMensaje());

    campoMensaje.addKeyListener(new java.awt.event.KeyAdapter() {
        @Override
        public void keyTyped(java.awt.event.KeyEvent e) {
            if (salida != null) {
                salida.println("TYPING:" + usuario);
            }
        }
    });

    botonSalir.addActionListener(e -> {
        if (salida != null) {
            salida.println("SALIR");
        }
        cerrarConexion();
    });

    setVisible(true);
}

    private void conectarServidor() {
    while (true) {
        try {
            socket = new Socket("localhost", 5000);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), true);

            salida.println(usuario);

            String respuestaInicial = entrada.readLine();

            if ("ERROR_USUARIO_REPETIDO".equals(respuestaInicial)) {

                JOptionPane.showMessageDialog(
                        this,
                        "Ese nombre ya está en uso.\nElegí otro.",
                        "Usuario repetido",
                        JOptionPane.WARNING_MESSAGE
                );

                socket.close();

                // 👇 vuelve a pedir usuario
                usuario = pedirUsuarioValido();
                setTitle("Chat Cliente - " + usuario);

                continue; // vuelve a intentar conexión
            }

            // ✔ conexión OK
            appendNormal("Conectado al servidor.");
            break;

        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se pudo conectar al servidor.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(0);
        }
    }
}

    private void escucharMensajes() {
    Thread hiloEscucha = new Thread(() -> {
        try {
            String mensaje;

            while ((mensaje = entrada.readLine()) != null) {
                String msg = mensaje;

                SwingUtilities.invokeLater(() -> {
                    if (msg.startsWith("USUARIOS:")) {
                        actualizarListaUsuarios(msg);

                    } else if (msg.startsWith("TYPING:")) {
                        mostrarEscribiendo(msg);

                    } else if (msg.startsWith("MSG:")) {
                        mostrarMensajeConColor(msg);

                    } else {
                        appendNormal(msg);
                    }
                });
            }

        } catch (IOException e) {
            SwingUtilities.invokeLater(() ->
                    appendNormal("Conexión finalizada.")
            );
        }
    });

    hiloEscucha.start();
}

    private void actualizarListaUsuarios(String msg) {
        String contenido = msg.substring(9);

        if (contenido.isEmpty()) {
            listaUsuarios.setListData(new String[]{});
        } else {
            listaUsuarios.setListData(contenido.split(","));
        }
    }

    private void mostrarMensajeConColor(String msg) {
        String contenido = msg.substring(4);
        String[] partes = contenido.split("\\|", 3);

        if (partes.length < 3) {
            appendNormal(msg);
            return;
        }

        String user = partes[0];
        String hora = partes[1];
        String texto = partes[2];

        boolean esPrivado = texto.contains("[Privado]")
                || texto.contains("Privado para")
                || texto.contains("✔✔ Privado");

        try {
            StyledDocument doc = areaChat.getStyledDocument();
            Style style = areaChat.addStyle("Estilo_" + user, null);

            if (user.equals(usuario)) {
                StyleConstants.setForeground(style, Color.BLUE);
                StyleConstants.setBold(style, true);
            } else {
                StyleConstants.setForeground(style, obtenerColor(user));
                StyleConstants.setBold(style, false);
            }

            if (esPrivado) {
                StyleConstants.setItalic(style, true);
                StyleConstants.setBackground(style, new Color(255, 245, 200));
            } else {
                StyleConstants.setItalic(style, false);
                StyleConstants.setBackground(style, Color.WHITE);
            }

            doc.insertString(
                    doc.getLength(),
                    "[" + hora + "] " + user + ": " + texto + "\n",
                    style
            );
            
            if (!user.equals(usuario)) {
            animarNuevoMensaje();
             }

            if (esPrivado && !user.equals(usuario)) {
                Toolkit.getDefaultToolkit().beep();

                JOptionPane.showMessageDialog(
                        this,
                        "Mensaje privado de " + user,
                        "Nuevo mensaje privado",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
            // 🔊 SONIDO PARA MENSAJE NORMAL
            if (!esPrivado && !user.equals(usuario)) {
            new Thread(() -> {
            try {
            Toolkit.getDefaultToolkit().beep();
            Thread.sleep(120);
            Toolkit.getDefaultToolkit().beep();
            } catch (InterruptedException e) {
            e.printStackTrace();
              }
             }).start();
           }

            areaChat.setCaretPosition(areaChat.getDocument().getLength());

        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void appendNormal(String texto) {
        try {
            StyledDocument doc = areaChat.getStyledDocument();
            Style style = areaChat.addStyle("Normal", null);

            StyleConstants.setForeground(style, Color.DARK_GRAY);
            StyleConstants.setBold(style, false);
            StyleConstants.setItalic(style, false);
            StyleConstants.setBackground(style, Color.WHITE);

            doc.insertString(doc.getLength(), texto + "\n", style);
            areaChat.setCaretPosition(areaChat.getDocument().getLength());

        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private Color obtenerColor(String usuario) {
        if (!coloresUsuarios.containsKey(usuario)) {
            Color color = new Color(
                    (int) (Math.random() * 180),
                    (int) (Math.random() * 180),
                    (int) (Math.random() * 180)
            );
            coloresUsuarios.put(usuario, color);
        }

        return coloresUsuarios.get(usuario);
    }

    private void enviarMensaje() {
        String mensaje = campoMensaje.getText().trim();

        if (mensaje.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "El mensaje no puede estar vacío.",
                    "Validación",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        if (mensaje.length() > 200) {
            JOptionPane.showMessageDialog(
                    this,
                    "El mensaje no puede superar los 200 caracteres.",
                    "Validación",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        salida.println(mensaje);
        campoMensaje.setText("");

        if (mensaje.equalsIgnoreCase("SALIR")) {
            cerrarConexion();
        }
    }

    private void cerrarConexion() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error al cerrar conexión.");
        }

        System.exit(0);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClienteChatSwing::new);
    }
    
    private void animarNuevoMensaje() {
    Color colorOriginal = areaChat.getBackground();

    areaChat.setBackground(new Color(235, 245, 255));

    Timer timer = new Timer(250, e -> {
        areaChat.setBackground(colorOriginal);
    });

    timer.setRepeats(false);
    timer.start();
}
    private void mostrarEscribiendo(String msg) {
    String usuarioEscribiendo = msg.substring(7);

    if (usuarioEscribiendo.equals(usuario)) {
        return;
    }

    labelEscribiendo.setText(usuarioEscribiendo + " está escribiendo...");

    if (timerEscribiendo != null) {
        timerEscribiendo.stop();
    }

    timerEscribiendo = new Timer(1500, e -> labelEscribiendo.setText(" "));
    timerEscribiendo.setRepeats(false);
    timerEscribiendo.start();
}
}