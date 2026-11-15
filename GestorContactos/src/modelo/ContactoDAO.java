/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package modelo;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class ContactoDAO {
    private final String archivo = "contactos.dat";
    private List<Contacto> contactos;
    private int siguienteId = 1;
    
    // CANDADO para evitar que dos hilos guarden/lean al mismo tiempo
    private final ReentrantLock lockExportacion = new ReentrantLock();

    public ContactoDAO() {
        contactos = cargar();
        for (Contacto c : contactos) {
            if (c.getId() >= siguienteId) siguienteId = c.getId() + 1;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Contacto> cargar() {
        File f = new File(archivo);
        if (!f.exists()) return new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            return (List<Contacto>) ois.readObject();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void guardar() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(archivo))) {
            oos.writeObject(contactos);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // SYNCHRONIZED: Evita conflictos si dos hilos agregan a la vez
    public synchronized void agregar(Contacto c) {
        c.setId(siguienteId++);
        contactos.add(c);
        guardar();
    }

    public synchronized void actualizar(Contacto c) {
        for (int i = 0; i < contactos.size(); i++) {
            if (contactos.get(i).getId() == c.getId()) {
                contactos.set(i, c);
                break;
            }
        }
        guardar();
    }

    public synchronized void eliminar(int id) {
        contactos.removeIf(c -> c.getId() == id);
        guardar();
    }

    public synchronized List<Contacto> listar() {
        return new ArrayList<>(contactos);
    }

    // VALIDACIÓN: Método para verificar si el contacto ya existe en segundo plano
    public synchronized boolean existeContacto(String telefono, String email) {
        for (Contacto c : contactos) {
            if (c.getTelefono().equals(telefono) || c.getEmail().equals(email)) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<Contacto> buscar(String texto) {
        List<Contacto> res = new ArrayList<>();
        String t = texto.toLowerCase();
        for (Contacto c : contactos) {
            if (c.getNombre().toLowerCase().contains(t)
                    || c.getTelefono().toLowerCase().contains(t)
                    || c.getEmail().toLowerCase().contains(t)) {
                res.add(c);
            }
        }
        return res;
    }

    public synchronized List<Contacto> filtrarPorCategoria(String categoria) {
        if (categoria == null || categoria.equals("Todas")) return listar();
        List<Contacto> res = new ArrayList<>();
        for (Contacto c : contactos) {
            if (c.getCategoria().equalsIgnoreCase(categoria)) res.add(c);
        }
        return res;
    }
    
    // EXPORTACIÓN CON BLOQUEO (Lock): Evita que dos exportaciones dañen el archivo
    public void exportarCSV(String ruta) throws IOException {
        lockExportacion.lock(); // Bloquea el recurso
        try (PrintWriter pw = new PrintWriter(new FileWriter(ruta))) {
            pw.println("ID,Nombre,Telefono,Email,Categoria");
            for (Contacto c : listar()) {
                pw.println(c.getId() + "," + c.getNombre() + "," + c.getTelefono() + "," 
                           + c.getEmail() + "," + c.getCategoria());
            }
        } finally {
            lockExportacion.unlock(); // Libera el recurso siempre
        }
    }
}