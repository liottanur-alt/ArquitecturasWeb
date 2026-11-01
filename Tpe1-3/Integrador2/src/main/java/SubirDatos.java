
public class SubirDatos {
    public static void main(String[] args) {
        System.out.println("Iniciando carga de datos...");

        new Repository.MySql.MySqlCarreraRepository().insertarDatosCsv();
        new Repository.MySql.MySqlEstudianteRepository().cargarDesdeCsv();
        new Repository.MySql.MySQLEstudianteCarreraRepository().insertarDatosCsv();

        System.out.println("Carga de datos finalizada.");
    }
}
