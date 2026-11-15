/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controlador;

import modelo.Contacto;
import modelo.ContactoDAO;
import vista.VentanaPrincipal;
import javax.swing.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContactoController {

    private final VentanaPrincipal vista;
    private final ContactoDAO dao;
    
    // EXECUTOR SERVICE: Para manejar hilos de búsqueda y exportación
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    public ContactoController(VentanaPrincipal vista, ContactoDAO dao) {
        this.vista = vista;
        this.dao = dao;
        configurarEventos();
        configurarAtajosTeclado();
        cargarConProgreso();
    }

    private void configurarEventos() {
        vista.btnAgregar.addActionListener(e -> validarYAgregarEnSegundoPlano());
        vista.btnEditar.addActionListener(e -> editar());
        vista.btnEliminar.addActionListener(e -> eliminar());
        vista.btnLimpiar.addActionListener(e -> vista.limpiarFormulario());
        vista.btnExportar.addActionListener(e -> exportarEnSegundoPlano());

        // BÚSQUEDA EN SEGUNDO PLANO (Evento de teclado)
        vista.txtBuscar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) { buscarEnSegundoPlano(); }
        });

        vista.cbFiltro.addActionListener(e -> buscarEnSegundoPlano());

        vista.tablaContactos.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int fila = vista.tablaContactos.rowAtPoint(e.getPoint());
                    if (fila != -1) {
                        vista.tablaContactos.setRowSelectionInterval(fila, fila);
                        vista.menuContextual.show(vista.tablaContactos, e.getX(), e.getY());
                    }
                } else {
                    seleccionarFila();
                }
            }
        });

        vista.itemEditar.addActionListener(e -> editar());
        vista.itemEliminar.addActionListener(e -> eliminar());

        vista.tabbedPane.addChangeListener(e -> {
            if (vista.tabbedPane.getSelectedIndex() == 1) {
                actualizarEstadisticas();
            }
        });
    }

    private void configurarAtajosTeclado() {
        JRootPane root = vista.getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "nuevo");
        am.put("nuevo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { vista.limpiarFormulario(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK), "guardar");
        am.put("guardar", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { validarYAgregarEnSegundoPlano(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "eliminar");
        am.put("eliminar", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { eliminar(); }
        });
    }

    private void cargarConProgreso() {
        vista.barraProgreso.setMaximum(100);
        vista.barraProgreso.setValue(0);
        vista.barraProgreso.setString(vista.getTxtCargando());
        
        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (int i = 0; i <= 100; i += 10) {
                    Thread.sleep(40); 
                    publish(i);
                }
                return null;
            }
            @Override
            protected void process(List<Integer> chunks) {
                vista.barraProgreso.setValue(chunks.get(chunks.size() - 1));
            }
            @Override
            protected void done() {
                vista.barraProgreso.setString(vista.getTxtCompletado());
                buscarEnSegundoPlano();
            }
        };
        worker.execute();
    }

    // 1. VALIDACIÓN EN SEGUNDO PLANO Y NOTIFICACIÓN
    private void validarYAgregarEnSegundoPlano() {
        if (vista.getNombre().isEmpty()) {
            vista.mostrarError("El nombre es obligatorio.");
            return;
        }

        String tel = vista.getTelefono();
        String email = vista.getEmail();
        
        // Deshabilitamos el botón temporalmente para evitar doble clic
        vista.btnAgregar.setEnabled(false);
        vista.barraProgreso.setString("Validando contacto...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                // ESTO CORRE EN UN HILO APARTE
                return dao.existeContacto(tel, email);
            }
            @Override
            protected void done() {
                try {
                    boolean existe = get();
                    if (existe) {
                        vista.mostrarError("El contacto con ese teléfono o email ya está registrado.");
                        vista.barraProgreso.setString(vista.getTxtCompletado());
                    } else {
                        // Si no existe, lo agregamos
                        dao.agregar(new Contacto(0, vista.getNombre(), tel, email, vista.getCategoria()));
                        vista.limpiarFormulario();
                        buscarEnSegundoPlano();
                        
                        // NOTIFICACIÓN EN SUBPROCESO
                        new Thread(() -> {
                            SwingUtilities.invokeLater(() -> {
                                vista.barraProgreso.setString("Contacto guardado con éxito");
                            });
                        }).start();
                    }
                } catch (Exception ex) {
                    vista.mostrarError("Error en validación.");
                } finally {
                    vista.btnAgregar.setEnabled(true); // Volvemos a habilitar el botón
                }
            }
        };
        worker.execute();
    }

    // 2. BÚSQUEDA EN SEGUNDO PLANO (Usando ExecutorService)
    private void buscarEnSegundoPlano() {
        String texto = vista.getTextoBuscar();
        String filtro = vista.getFiltro();

        executor.submit(() -> {
            // ESTO SE EJECUTA EN UN HILO SECUNDARIO (No congela la UI)
            List<Contacto> lista;
            if (!texto.isEmpty()) {
                lista = dao.buscar(texto);
            } else {
                lista = dao.filtrarPorCategoria(filtro);
            }
            
            // PROGRAMACIÓN DE INTERFAZ: Actualizamos la UI en el hilo de eventos
            SwingUtilities.invokeLater(() -> {
                vista.getTableModel().setDatos(lista);
            });
        });
    }

    // 3. EXPORTACIÓN EN SEGUNDO PLANO
    private void exportarEnSegundoPlano() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Guardar como CSV");
        if (fileChooser.showSaveDialog(vista) == JFileChooser.APPROVE_OPTION) {
            File archivo = fileChooser.getSelectedFile();
            if (!archivo.getName().toLowerCase().endsWith(".csv")) {
                archivo = new File(archivo.getAbsolutePath() + ".csv");
            }
            final File archivoFinal = archivo;
            
            vista.btnExportar.setEnabled(false);
            vista.barraProgreso.setString("Exportando...");

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    dao.exportarCSV(archivoFinal.getAbsolutePath());
                    return null;
                }
                @Override
                protected void done() {
                    vista.btnExportar.setEnabled(true);
                    // Notificación en subproceso
                    new Thread(() -> {
                        SwingUtilities.invokeLater(() -> {
                            vista.barraProgreso.setString("Exportación completada");
                            JOptionPane.showMessageDialog(vista, "Exportado con éxito a:\n" + archivoFinal.getAbsolutePath());
                        });
                    }).start();
                }
            };
            worker.execute();
        }
    }

    private void editar() {
        int fila = vista.getFilaSeleccionada();
        if (fila == -1) {
            vista.mostrarError("Selecciona un contacto para editar.");
            return;
        }
        Contacto c = vista.getTableModel().getContactoAt(fila);
        if (vista.getNombre().isEmpty()) {
            vista.mostrarError("El nombre es obligatorio.");
            return;
        }
        
        // SINCRONIZACIÓN: Bloqueamos el objeto contacto mientras lo modificamos
        synchronized (c) {
            c.setNombre(vista.getNombre());
            c.setTelefono(vista.getTelefono());
            c.setEmail(vista.getEmail());
            c.setCategoria(vista.getCategoria());
            dao.actualizar(c);
        }
        
        vista.limpiarFormulario();
        buscarEnSegundoPlano();
    }

    private void eliminar() {
        int fila = vista.getFilaSeleccionada();
        if (fila == -1) {
            vista.mostrarError("Selecciona un contacto para eliminar.");
            return;
        }
        Contacto c = vista.getTableModel().getContactoAt(fila);
        if (vista.confirmar("¿Eliminar el contacto " + c.getNombre() + "?")) {
            dao.eliminar(c.getId());
            vista.limpiarFormulario();
            buscarEnSegundoPlano();
        }
    }

    private void seleccionarFila() {
        int fila = vista.getFilaSeleccionada();
        if (fila == -1) return;
        Contacto c = vista.getTableModel().getContactoAt(fila);
        vista.setCampos(c.getNombre(), c.getTelefono(), c.getEmail(), c.getCategoria());
    }

    private void actualizarEstadisticas() {
        List<Contacto> todos = dao.listar();
        int familia = 0, trabajo = 0, amigos = 0, otros = 0;
        for (Contacto c : todos) {
            switch (c.getCategoria()) {
                case "Familia": familia++; break;
                case "Trabajo": trabajo++; break;
                case "Amigos": amigos++; break;
                default: otros++;
            }
        }
        
        String texto = "=== ESTADÍSTICAS DE CONTACTOS ===\n\n" +
                       "Total de contactos: " + todos.size() + "\n\n" +
                       "Familia: " + familia + "\n" +
                       "Trabajo: " + trabajo + "\n" +
                       "Amigos: " + amigos + "\n" +
                       "Otros: " + otros;
        vista.areaEstadisticas.setText(texto);
    }
}