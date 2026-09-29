import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class CerarTablas {

    public static void main(String[] args) {
        // Nombre de la unidad de persistencia configurada en persistence.xml
        String persistenceUnitName = "Integrador_2";

        System.out.println("Iniciando conexión con JPA e instanciando tablas...");

        // Al crear el EntityManagerFactory, Hibernate lee persistence.xml
        // y ejecuta el ddl (create/update) en la base de datos MySQL
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(persistenceUnitName);
        EntityManager em = emf.createEntityManager();

        try {
            System.out.println("¡Conexión exitosa! Las tablas han sido verificadas/creadas en MySQL.");
        } catch (Exception e) {
            System.err.println("Ocurrió un error al conectar con la base de datos:");
            e.printStackTrace();
        } finally {
            // Cerrar los recursos
            em.close();
            emf.close();
        }
    }

}

